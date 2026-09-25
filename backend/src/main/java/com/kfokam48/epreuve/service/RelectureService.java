package com.kfokam48.epreuve.service;

import com.kfokam48.epreuve.domain.Exercice;
import com.kfokam48.epreuve.domain.Relecture;
import com.kfokam48.epreuve.domain.StatutRelecture;
import com.kfokam48.epreuve.domain.StatutSession;
import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import com.kfokam48.epreuve.repository.RelectureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

@Service
public class RelectureService {

    /** RG6 : note maximale. */
    static final BigDecimal NOTE_MAX = BigDecimal.valueOf(20);

    private final RelectureRepository relectureRepository;
    private final Clock horloge;

    public RelectureService(RelectureRepository relectureRepository, Clock horloge) {
        this.relectureRepository = relectureRepository;
        this.horloge = horloge;
    }

    /**
     * EF5, EF6 : rend la relecture, ou la modifie tant que la session est ouverte (décision Q10, RG9).
     * <ul>
     *   <li>première soumission : relecture RENDUE, exercice RELU (session ouverte) ou DEFINITIF (session clôturée, H8) ;</li>
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
        Exercice exercice = relecture.getExercice();

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
            if (sessionOuverte) {
                exercice.marquerRelu();
            } else {
                exercice.rendreDefinitif();
            }
        } else if (sessionOuverte) {
            relecture.modifier(noteValide, commentaireNettoye, maintenant);
        } else {
            throw new ErreurMetierException(CodeErreur.RELECTURE_DEJA_RENDUE,
                    "La session est clôturée, cette relecture ne peut plus être modifiée.");
        }
        return relecture;
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
