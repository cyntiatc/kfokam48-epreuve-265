package com.kfokam48.epreuve.service;

import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import com.kfokam48.epreuve.repository.PromotionRepository;
import com.kfokam48.epreuve.repository.StatistiquesEtudiant;
import com.kfokam48.epreuve.repository.TableauRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TableauServiceTest {

    @Mock
    private PromotionRepository promotionRepository;
    @Mock
    private TableauRepository tableauRepository;

    private TableauService service;

    @BeforeEach
    void setUp() {
        service = new TableauService(promotionRepository, tableauRepository);
    }

    @Test
    void consulter_promotionConnue_renvoieLesStatistiquesDeSesEtudiants() {
        List<StatistiquesEtudiant> statistiques = List.of(
                new StatistiquesEtudiant(1L, "Mbarga", "Alice", 3, 2, new BigDecimal("15.67"), 1),
                new StatistiquesEtudiant(2L, "Nkoulou", "Brice", 0, 0, null, 0));
        when(promotionRepository.existsById(1L)).thenReturn(true);
        when(tableauRepository.statistiquesDeLaPromotion(1L)).thenReturn(statistiques);

        assertThat(service.consulter(1L)).isEqualTo(statistiques);
    }

    @Test
    void consulter_promotionInconnue_refuseAvecPromotionInconnue() {
        when(promotionRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.consulter(99L))
                .isInstanceOfSatisfying(ErreurMetierException.class, erreur -> {
                    assertThat(erreur.getCode()).isEqualTo(CodeErreur.PROMOTION_INCONNUE);
                    assertThat(erreur.getMessage()).isEqualTo("Aucune promotion ne correspond à l'identifiant 99.");
                });
        verifyNoInteractions(tableauRepository);
    }
}
