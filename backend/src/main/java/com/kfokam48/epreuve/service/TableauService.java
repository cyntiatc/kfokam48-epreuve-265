package com.kfokam48.epreuve.service;

import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import com.kfokam48.epreuve.repository.PromotionRepository;
import com.kfokam48.epreuve.repository.StatistiquesEtudiant;
import com.kfokam48.epreuve.repository.TableauRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TableauService {

    private final PromotionRepository promotionRepository;
    private final TableauRepository tableauRepository;

    public TableauService(PromotionRepository promotionRepository, TableauRepository tableauRepository) {
        this.promotionRepository = promotionRepository;
        this.tableauRepository = tableauRepository;
    }

    /** EF7 : tableau récapitulatif d'une promotion existante (RG11), sinon 404 PROMOTION_INCONNUE. */
    @Transactional(readOnly = true)
    public List<StatistiquesEtudiant> consulter(Long promotionId) {
        if (!promotionRepository.existsById(promotionId)) {
            throw new ErreurMetierException(CodeErreur.PROMOTION_INCONNUE,
                    "Aucune promotion ne correspond à l'identifiant " + promotionId + ".");
        }
        return tableauRepository.statistiquesDeLaPromotion(promotionId);
    }
}
