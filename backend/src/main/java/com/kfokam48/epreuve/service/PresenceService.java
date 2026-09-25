package com.kfokam48.epreuve.service;

import com.kfokam48.epreuve.domain.Etudiant;
import com.kfokam48.epreuve.domain.Presence;
import com.kfokam48.epreuve.domain.SessionCours;
import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import com.kfokam48.epreuve.repository.EtudiantRepository;
import com.kfokam48.epreuve.repository.PresenceRepository;
import com.kfokam48.epreuve.repository.SessionCoursRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
public class PresenceService {

    private final SessionCoursRepository sessionRepository;
    private final EtudiantRepository etudiantRepository;
    private final PresenceRepository presenceRepository;
    private final Clock horloge;

    public PresenceService(SessionCoursRepository sessionRepository, EtudiantRepository etudiantRepository,
                           PresenceRepository presenceRepository, Clock horloge) {
        this.sessionRepository = sessionRepository;
        this.etudiantRepository = etudiantRepository;
        this.presenceRepository = presenceRepository;
        this.horloge = horloge;
    }

    /**
     * EF2 : marque la présence d'un étudiant avec le code de la session.
     * Contrôles dans l'ordre de H6 : code connu, présence unique (RG3), code encore valide (RG1).
     */
    @Transactional
    public Presence marquerPresence(String code, Long etudiantId) {
        // RG10 : la saisie du code est insensible à la casse.
        SessionCours session = sessionRepository.findByCode(code.strip().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ErreurMetierException(CodeErreur.CODE_INCONNU,
                        "Aucune session ne correspond au code saisi."));

        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new ErreurMetierException(CodeErreur.REQUETE_INVALIDE,
                        "Aucun étudiant ne correspond à l'identifiant " + etudiantId + "."));
        if (!etudiant.getPromotion().getId().equals(session.getPromotion().getId())) {
            throw new ErreurMetierException(CodeErreur.REQUETE_INVALIDE,
                    "L'étudiant n'appartient pas à la promotion de cette session.");
        }

        if (presenceRepository.existsBySessionIdAndEtudiantId(session.getId(), etudiantId)) {
            throw dejaPresent();
        }

        Instant maintenant = Instant.now(horloge);
        if (!session.codeEstValide(maintenant)) {
            throw new ErreurMetierException(CodeErreur.CODE_EXPIRE, "Ce code de présence a expiré.");
        }

        try {
            return presenceRepository.saveAndFlush(new Presence(session, etudiant, maintenant));
        } catch (DataIntegrityViolationException e) {
            // Deux envois simultanés passent le contrôle ci-dessus : la contrainte
            // UNIQUE (session_id, etudiant_id) refuse le second (ENF3).
            throw dejaPresent();
        }
    }

    private static ErreurMetierException dejaPresent() {
        return new ErreurMetierException(CodeErreur.DEJA_PRESENT,
                "Votre présence à cette session est déjà enregistrée.");
    }
}
