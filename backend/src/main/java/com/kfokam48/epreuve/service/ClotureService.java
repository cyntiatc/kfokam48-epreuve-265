package com.kfokam48.epreuve.service;

import com.kfokam48.epreuve.domain.Exercice;
import com.kfokam48.epreuve.domain.SessionCours;
import com.kfokam48.epreuve.domain.StatutSession;
import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import com.kfokam48.epreuve.repository.ExerciceRepository;
import com.kfokam48.epreuve.repository.SessionCoursRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class ClotureService {

    private final SessionCoursRepository sessionRepository;
    private final ExerciceRepository exerciceRepository;
    private final Clock horloge;

    public ClotureService(SessionCoursRepository sessionRepository, ExerciceRepository exerciceRepository,
                          Clock horloge) {
        this.sessionRepository = sessionRepository;
        this.exerciceRepository = exerciceRepository;
        this.horloge = horloge;
    }

    /**
     * EF8 : clôture la session. Le code de présence n'est plus accepté (RG1), les notes rendues deviennent
     * définitives (RG9, D4 : RELU -> DEFINITIF) et les exercices sans relecteur passent SANS_RELECTURE.
     * Les relectures encore en attente restent ouvertes : elles pourront être rendues une fois (H8).
     */
    @Transactional
    public BilanCloture cloturerSession(Long sessionId) {
        SessionCours session = sessionRepository.findPourMiseAJour(sessionId)
                .orElseThrow(() -> new ErreurMetierException(CodeErreur.REQUETE_INVALIDE,
                        "Aucune session ne correspond à l'identifiant " + sessionId + "."));
        if (session.getStatut() == StatutSession.CLOTUREE) {
            throw new ErreurMetierException(CodeErreur.SESSION_DEJA_CLOTUREE, "Cette session est déjà clôturée.");
        }

        session.cloturer(Instant.now(horloge));

        int definitifs = 0;
        int sansRelecture = 0;
        int enAttente = 0;
        for (Exercice exercice : exerciceRepository.findBySessionIdOrderByDeposeAtAsc(sessionId)) {
            switch (exercice.getStatut()) {
                case RELU -> {
                    exercice.rendreDefinitif();
                    definitifs++;
                }
                case DEPOSE -> {
                    exercice.marquerSansRelecture();
                    sansRelecture++;
                }
                case EN_RELECTURE -> enAttente++;
                default -> {
                    // DEFINITIF ou SANS_RELECTURE : déjà dans un état final.
                }
            }
        }
        return new BilanCloture(session, definitifs, sansRelecture, enAttente);
    }
}
