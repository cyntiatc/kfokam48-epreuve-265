package com.kfokam48.epreuve.service;

import com.kfokam48.epreuve.domain.Exercice;
import com.kfokam48.epreuve.domain.Relecture;
import com.kfokam48.epreuve.domain.StatutRelecture;
import com.kfokam48.epreuve.domain.StatutSession;
import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import com.kfokam48.epreuve.repository.EtudiantRepository;
import com.kfokam48.epreuve.repository.ExerciceRepository;
import com.kfokam48.epreuve.repository.RelectureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class RelectureService {

    /** RG6 : note maximale. */
    static final BigDecimal NOTE_MAX = BigDecimal.valueOf(20);

    private final RelectureRepository relectureRepository;
    private final ExerciceRepository exerciceRepository;
    private final EtudiantRepository etudiantRepository;
    private final Clock horloge;

    public RelectureService(RelectureRepository relectureRepository, ExerciceRepository exerciceRepository,
                            EtudiantRepository etudiantRepository, Clock horloge) {
        this.relectureRepository = relectureRepository;
        this.exerciceRepository = exerciceRepository;
        this.etudiantRepository = etudiantRepository;
        this.horloge = horloge;
    }

    /**
     * H3 (route complémentaire) : relectures attribuées à un relecteur, celles en attente d'abord,
     * puis de la plus récente à la plus ancienne. Relecteur inconnu : 400 REQUETE_INVALIDE (H2).
     */
    @Transactional(readOnly = true)
    public List<Relecture> relecturesAttribuees(Long relecteurId) {
        if (!etudiantRepository.existsById(relecteurId)) {
            throw new ErreurMetierException(CodeErreur.REQUETE_INVALIDE,
                    "Aucun étudiant ne correspond à l'identifiant " + relecteurId + ".");
        }
        // Tri stable : l'ordre par date d'attribution de la requête est conservé dans chaque groupe.
        return relectureRepository.findByRelecteurIdOrderByAttribueeAtDesc(relecteurId).stream()
                .sorted(Comparator.comparing(relecture -> relecture.getStatut() != StatutRelecture.EN_ATTENTE))
                .toList();
    }

    /**
     * EF5, EF6 : rend la relecture, ou la modifie tant que la session est ouverte (décision Q10, RG9).
     * <ul>
     *   <li>première soumission : relecture RENDUE. Session ouverte : l'exercice passe RELU quand ses deux relectures
     *       sont rendues (RG8), sinon il reste EN_RELECTURE avec une note provisoire (RG6). Session clôturée (H8) :
     *       l'exercice devient DEFINITIF quand plus aucune de ses relectures n'est en attente ;</li>
     *   <li>nouvelle soumission, session ouverte : note et commentaire remplacés ;</li>
     *   <li>nouvelle soumission, session clôturée : 409 RELECTURE_DEJA_RENDUE, la note est définitive.</li>
     * </ul>
     */
    @Transactional
    public Relecture rendreRelecture(Long relectureId, BigDecimal note, String commentaire) {
        int noteValide = validerNote(note);

        Relecture relecture = relectureRepository.findPourMiseAJour(relectureId)
                .orElseThrow(() -> new ErreurMetierException(CodeErreur.REQUETE_INVALIDE,
                        "Aucune relecture ne correspond à l'identifiant " + relectureId + "."));
        // Verrou de l'exercice avant toute lecture de son état : l'autre relecture du même exercice, rendue au même
        // instant, attend la fin de celle-ci et la voit rendue.
        Exercice exercice = exerciceRepository.findPourMiseAJour(relecture.getExercice().getId()).orElseThrow();

        // RG2 : contrôle de sûreté. Le tirage exclut déjà l'auteur, et l'émetteur n'est pas transmis (H4).
        if (relecture.getRelecteur().getId().equals(exercice.getAuteur().getId())) {
            throw new ErreurMetierException(CodeErreur.AUTO_RELECTURE,
                    "Vous ne pouvez pas relire votre propre exercice.");
        }

        boolean sessionOuverte = exercice.getSession().getStatut() == StatutSession.OUVERTE;
        Instant maintenant = Instant.now(horloge);
        String commentaireNettoye = commentaire.strip();

        if (relecture.getStatut() == StatutRelecture.EN_ATTENTE) {
            relecture.rendre(noteValide, commentaireNettoye, maintenant);
            mettreAJourLExercice(exercice, sessionOuverte);
        } else if (sessionOuverte) {
            relecture.modifier(noteValide, commentaireNettoye, maintenant);
        } else {
            throw new ErreurMetierException(CodeErreur.RELECTURE_DEJA_RENDUE,
                    "La session est clôturée, cette relecture ne peut plus être modifiée.");
        }
        return relecture;
    }

    /** D4 : effet d'une première soumission sur l'exercice, selon ses relectures rendues et en attente. */
    private void mettreAJourLExercice(Exercice exercice, boolean sessionOuverte) {
        List<Relecture> relectures = relectureRepository.findByExerciceId(exercice.getId());
        long rendues = relectures.stream().filter(r -> r.getStatut() == StatutRelecture.RENDUE).count();
        long enAttente = relectures.size() - rendues;
        if (sessionOuverte && rendues == AttributionService.RELECTEURS_PAR_EXERCICE) {
            exercice.marquerRelu();
        } else if (!sessionOuverte && enAttente == 0) {
            exercice.rendreDefinitif();
        }
    }

    /** RG6 : entier de 0 à 20 inclus. 15.0 est accepté, 12.5 ne l'est pas. */
    private static int validerNote(BigDecimal note) {
        if (note == null
                || note.stripTrailingZeros().scale() > 0
                || note.compareTo(BigDecimal.ZERO) < 0
                || note.compareTo(NOTE_MAX) > 0) {
            throw new ErreurMetierException(CodeErreur.NOTE_INVALIDE,
                    "La note doit être un entier compris entre 0 et 20.");
        }
        return note.intValueExact();
    }
}
