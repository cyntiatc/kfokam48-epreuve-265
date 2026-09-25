package com.kfokam48.epreuve.web;

import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import com.kfokam48.epreuve.repository.StatistiquesEtudiant;
import com.kfokam48.epreuve.service.TableauService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TableauController.class)
class TableauControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TableauService tableauService;

    @Test
    void consulter_renvoie200AvecUneLigneParEtudiantAuFormatDuContrat() throws Exception {
        when(tableauService.consulter(1L)).thenReturn(List.of(
                new StatistiquesEtudiant(1L, "Mbarga", "Alice", 3, 2, new BigDecimal("15.67"), 1),
                new StatistiquesEtudiant(2L, "Nkoulou", "Brice", 0, 0, null, 0)));

        mockMvc.perform(get("/api/tableau").param("promotionId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].etudiantId").value(1))
                .andExpect(jsonPath("$[0].nom").value("Mbarga Alice"))
                .andExpect(jsonPath("$[0].presences").value(3))
                .andExpect(jsonPath("$[0].exercicesDeposes").value(2))
                .andExpect(jsonPath("$[0].moyenne").value(15.67))
                .andExpect(jsonPath("$[0].relecturesEnAttente").value(1))
                // Sans note reçue, la moyenne est présente et vaut null (RG11).
                .andExpect(jsonPath("$[1].nom").value("Nkoulou Brice"))
                .andExpect(jsonPath("$[1].moyenne").value(nullValue()));
    }

    @Test
    void consulter_promotionInconnue_renvoie404PromotionInconnue() throws Exception {
        when(tableauService.consulter(99L)).thenThrow(new ErreurMetierException(
                CodeErreur.PROMOTION_INCONNUE, "Aucune promotion ne correspond à l'identifiant 99."));

        mockMvc.perform(get("/api/tableau").param("promotionId", "99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROMOTION_INCONNUE"))
                .andExpect(jsonPath("$.message").value("Aucune promotion ne correspond à l'identifiant 99."));
    }

    @Test
    void consulter_sansPromotionId_renvoie400RequeteInvalide() throws Exception {
        mockMvc.perform(get("/api/tableau"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message").value("Le paramètre promotionId est obligatoire."));
        verifyNoInteractions(tableauService);
    }

    @Test
    void consulter_promotionIdNonNumerique_renvoie400RequeteInvalide() throws Exception {
        mockMvc.perform(get("/api/tableau").param("promotionId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
        verifyNoInteractions(tableauService);
    }
}
