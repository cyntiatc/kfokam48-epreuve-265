package com.kfokam48.epreuve.service;

import com.kfokam48.epreuve.domain.Etudiant;
import com.kfokam48.epreuve.domain.Exercice;
import com.kfokam48.epreuve.domain.SessionCours;
import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import com.kfokam48.epreuve.repository.EtudiantRepository;
import com.kfokam48.epreuve.repository.ExerciceRepository;
import com.kfokam48.epreuve.repository.SessionCoursRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;

@Service
public class ExerciceService {

    /** RG5 : longueur maximale du lien (colonne exercices.lien). */
    static final int LONGUEUR_MAX_LIEN = 2048;

    private final SessionCoursRepository sessionRepository;
    private final EtudiantRepository etudiantRepository;
    private final ExerciceRepository exerciceRepository;
    private final AttributionService attributionService;
    private final Clock horloge;

    public ExerciceService(SessionCoursRepository sessionRepository, EtudiantRepository etudiantRepository,
                           ExerciceRepository exerciceRepository, AttributionService attributionService,
                           Clock horloge) {
        this.sessionRepository = sessionRepository;
        this.etudiantRepository = etudiantRepository;
        this.exerciceRepository = exerciceRepository;
        this.attributionService = attributionService;
        this.horloge = horloge;
    }

    /**
     * EF3 : dépose le lien de l'exercice d'un étudiant pour une session de sa promotion (H5).
     * La présence n'est pas exigée (H5). Un seul dépôt par étudiant et par session (RG4).
     * Deux relecteurs sont recherchés aussitôt parmi les étudiants présents éligibles (EF4, RG8) : le statut renvoyé
     * est EN_RELECTURE si au moins un a été attribué, sinon DEPOSE.
     */
    @Transactional
    public DepotExercice deposerExercice(Long sessionId, Long etudiantId, String lien) {
        String lienValide = validerLien(lien);

        SessionCours session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ErreurMetierException(CodeErreur.REQUETE_INVALIDE,
                        "Aucune session ne correspond à l'identifiant " + sessionId + "."));
        Etudiant auteur = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new ErreurMetierException(CodeErreur.REQUETE_INVALIDE,
                        "Aucun étudiant ne correspond à l'identifiant " + etudiantId + "."));
        if (!auteur.estDansLaPromotion(session.getPromotion())) {
            throw new ErreurMetierException(CodeErreur.REQUETE_INVALIDE,
                    "L'étudiant n'appartient pas à la promotion de cette session.");
        }

        if (exerciceRepository.existsBySessionIdAndAuteurId(sessionId, etudiantId)) {
            throw dejaDepose();
        }

        Exercice exercice;
        try {
            exercice = exerciceRepository.saveAndFlush(new Exercice(session, auteur, lienValide, Instant.now(horloge)));
        } catch (DataIntegrityViolationException e) {
            // Deux dépôts simultanés passent le contrôle ci-dessus : la contrainte
            // UNIQUE (session_id, etudiant_id) refuse le second (ENF3).
            throw dejaDepose();
        }

        int relecteursAttribues = attributionService.attribuerRelecteurs(exercice).size();
        return new DepotExercice(exercice, relecteursAttribues);
    }

    /** RG5 : URL absolue http ou https, avec un hôte, de 2048 caractères au plus. */
    private static String validerLien(String lien) {
        String lienNettoye = lien.strip();
        if (lienNettoye.length() > LONGUEUR_MAX_LIEN || !estUrlHttp(lienNettoye)) {
            throw new ErreurMetierException(CodeErreur.LIEN_INVALIDE,
                    "Le lien doit être une URL http ou https valide.");
        }
        return lienNettoye;
    }

    private static boolean estUrlHttp(String lien) {
        try {
            URI uri = new URI(lien);
            String schema = uri.getScheme();
            return ("http".equalsIgnoreCase(schema) || "https".equalsIgnoreCase(schema)) && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static ErreurMetierException dejaDepose() {
        return new ErreurMetierException(CodeErreur.EXERCICE_DEJA_DEPOSE,
                "Vous avez déjà déposé un exercice pour cette session.");
    }
}
