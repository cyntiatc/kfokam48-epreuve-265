package com.kfokam48.epreuve;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parcours complet sur un vrai PostgreSQL : migrations Flyway V1 à V3, contraintes et données de référence
 * (promotion 1 = L3 GL avec les étudiants 1 à 5, promotion 2 = M1 avec les étudiants 6 et 7).
 * Nécessite Docker : sans Docker, ces tests sont ignorés.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PresenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void marquerPresence_renvoie201PuisRefuseLeDoublonEn409() throws Exception {
        String code = ouvrirSessionPourLaPromotion1();

        marquer(code, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.sessionId").isNumber())
                .andExpect(jsonPath("$.etudiantId").value(1))
                .andExpect(jsonPath("$.source").value("CODE"));

        marquer(code, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEJA_PRESENT"));

        Integer nombrePresences = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM presences p JOIN sessions s ON s.id = p.session_id
                WHERE s.code = ? AND p.etudiant_id = 1
                """, Integer.class, code);
        assertThat(nombrePresences).isEqualTo(1);
    }

    @Test
    void marquerPresence_accepteLeCodeEnMinuscules() throws Exception {
        String code = ouvrirSessionPourLaPromotion1();

        marquer(code.toLowerCase(), 2)
                .andExpect(status().isCreated());
    }

    @Test
    void marquerPresence_codeInconnu_renvoie400CodeInconnu() throws Exception {
        marquer("INCONNU", 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CODE_INCONNU"));
    }

    @Test
    void marquerPresence_etudiantDUneAutrePromotion_renvoie400RequeteInvalide() throws Exception {
        String code = ouvrirSessionPourLaPromotion1();

        marquer(code, 6)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
    }

    @Test
    void marquerPresence_codeExpire_renvoie410CodeExpire() throws Exception {
        // Session ouverte il y a une heure : son code a expiré il y a 45 minutes (RG1).
        jdbcTemplate.update("""
                INSERT INTO sessions (promotion_id, titre, code, ouverture_at, expiration_at, statut)
                VALUES (1, 'Session passee', 'EXPIR1', now() - INTERVAL '1 hour', now() - INTERVAL '45 minutes', 'OUVERTE')
                """);

        marquer("EXPIR1", 3)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRE"));
    }

    private String ouvrirSessionPourLaPromotion1() throws Exception {
        String reponse = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titre": "Session d'integration", "promotionId": 1}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.code");
    }

    private ResultActions marquer(String code, long etudiantId) throws Exception {
        return mockMvc.perform(post("/api/presences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "%s", "etudiantId": %d}
                        """.formatted(code, etudiantId)));
    }
}
