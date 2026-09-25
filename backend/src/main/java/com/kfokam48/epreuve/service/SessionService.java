package com.kfokam48.epreuve.service;

import com.kfokam48.epreuve.domain.Promotion;
import com.kfokam48.epreuve.domain.SessionCours;
import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import com.kfokam48.epreuve.repository.PromotionRepository;
import com.kfokam48.epreuve.repository.SessionCoursRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class SessionService {

    /** Au-delà, l'espace des codes (36^6) est anormalement saturé : on échoue plutôt que de boucler. */
    static final int MAX_TENTATIVES_CODE = 10;

    private final SessionCoursRepository sessionRepository;
    private final PromotionRepository promotionRepository;
    private final GenerateurCode generateurCode;
    private final Clock horloge;

    public SessionService(SessionCoursRepository sessionRepository, PromotionRepository promotionRepository,
                          GenerateurCode generateurCode, Clock horloge) {
        this.sessionRepository = sessionRepository;
        this.promotionRepository = promotionRepository;
        this.generateurCode = generateurCode;
        this.horloge = horloge;
    }

    /**
     * EF1 : ouvre une session pour une promotion existante. Le code est unique (RG10)
     * et valable 15 minutes à partir de l'ouverture (RG1).
     */
    @Transactional
    public SessionCours ouvrirSession(String titre, Long promotionId) {
        Promotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new ErreurMetierException(CodeErreur.REQUETE_INVALIDE,
                        "Aucune promotion ne correspond à l'identifiant " + promotionId + "."));

        Instant ouverture = Instant.now(horloge).truncatedTo(ChronoUnit.SECONDS);
        SessionCours session = new SessionCours(promotion, titre.strip(), genererCodeUnique(), ouverture);
        return sessionRepository.save(session);
    }

    private String genererCodeUnique() {
        for (int tentative = 1; tentative <= MAX_TENTATIVES_CODE; tentative++) {
            String code = generateurCode.generer();
            if (!sessionRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException(
                "Aucun code de présence libre après " + MAX_TENTATIVES_CODE + " tentatives.");
    }
}
