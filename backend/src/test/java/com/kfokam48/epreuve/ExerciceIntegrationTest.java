package com.kfokam48.epreuve;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dépôt d'exercice sur un vrai PostgreSQL (migrations V1 à V3). Nécessite Docker : sans Docker, ces tests sont ignorés.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ConteneurPostgresConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class ExerciceIntegrationTest {

    private static final String LIEN = "https://github.com/exemple/exercice";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deposerExercice_renvoie201PuisRefuseLeSecondDepotEn409() throws Exception {
        long sessionId = ouvrirSessionPourLaPromotion1();

        deposer(sessionId, 1, LIEN)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.statut").value("DEPOSE"));

        deposer(sessionId, 1, LIEN + "-v2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXERCICE_DEJA_DEPOSE"));
    }

    @Test
    void deposerExercice_lienNonHttp_renvoie400LienInvalide() throws Exception {
        long sessionId = ouvrirSessionPourLaPromotion1();

        deposer(sessionId, 2, "ftp://exemple.com/exercice")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LIEN_INVALIDE"));
    }

    @Test
    void deposerExercice_sessionInconnue_renvoie400RequeteInvalide() throws Exception {
        deposer(999_999, 1, LIEN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
    }

    @Test
    void deposerExercice_etudiantDUneAutrePromotion_renvoie400RequeteInvalide() throws Exception {
        long sessionId = ouvrirSessionPourLaPromotion1();

        deposer(sessionId, 6, LIEN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
    }

    private long ouvrirSessionPourLaPromotion1() throws Exception {
        String reponse = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titre": "Session d'integration", "promotionId": 1}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(reponse, "$.id");
        return id.longValue();
    }

    private ResultActions deposer(long sessionId, long etudiantId, String lien) throws Exception {
        return mockMvc.perform(post("/api/exercices")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sessionId": %d, "etudiantId": %d, "lien": "%s"}
                        """.formatted(sessionId, etudiantId, lien)));
    }
}
