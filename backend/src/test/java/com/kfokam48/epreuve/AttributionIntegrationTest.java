package com.kfokam48.epreuve;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EF4 de bout en bout sur un vrai PostgreSQL : attribution au dépôt et à l'émargement.
 * Étudiants de la promotion 1 (V3) : 1 Alice, 2 Brice, 3 Carine, 4 David, 5 Estelle.
 * Nécessite Docker : sans Docker, ces tests sont ignorés.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ConteneurPostgresConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class AttributionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void depot_avecDesPresents_attribueAussitotLeRelecteurLeMoinsCharge() throws Exception {
        SessionOuverte session = ouvrirSession();
        emarger(session.code(), 1);
        emarger(session.code(), 2);

        // Seul Brice (2) est présent et éligible pour l'exercice d'Alice (1).
        deposer(session.id(), 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_RELECTURE"));
        assertThat(relecteurDe(session.id(), 1)).isEqualTo(2L);

        // Carine (3), absente, dépose aussi (H5) : Alice (aucune relecture) passe avant Brice (une relecture).
        deposer(session.id(), 3)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_RELECTURE"));
        assertThat(relecteurDe(session.id(), 3)).isEqualTo(1L);
    }

    @Test
    void depot_sansRelecteurEligible_estAttribueALaPresenceSuivante() throws Exception {
        SessionOuverte session = ouvrirSession();

        deposer(session.id(), 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("DEPOSE"));

        emarger(session.code(), 1);  // l'auteure seule présente : toujours aucun relecteur (RG2)
        assertThat(statutExercice(session.id(), 1)).isEqualTo("DEPOSE");

        emarger(session.code(), 2);  // Brice arrive : l'exercice en attente lui est attribué
        assertThat(statutExercice(session.id(), 1)).isEqualTo("EN_RELECTURE");
        assertThat(relecteurDe(session.id(), 1)).isEqualTo(2L);
    }

    @Test
    void cinqPresents_chaqueExerciceAUnRelecteurQuiNEstJamaisSonAuteur() throws Exception {
        SessionOuverte session = ouvrirSession();
        for (long etudiant = 1; etudiant <= 5; etudiant++) {
            emarger(session.code(), etudiant);
        }
        for (long etudiant = 1; etudiant <= 5; etudiant++) {
            deposer(session.id(), etudiant).andExpect(status().isCreated());
        }

        assertThat(compter("""
                SELECT COUNT(*) FROM relectures r JOIN exercices e ON e.id = r.exercice_id
                WHERE e.session_id = ?
                """, session.id())).isEqualTo(5);
        assertThat(compter("""
                SELECT COUNT(*) FROM relectures r JOIN exercices e ON e.id = r.exercice_id
                WHERE e.session_id = ? AND r.relecteur_id = e.etudiant_id
                """, session.id())).isZero();
        // Nombre impair de présents (section 7.2) : personne ne relit plus de deux exercices.
        assertThat(compter("""
                SELECT COALESCE(MAX(nb), 0) FROM (
                    SELECT COUNT(*) AS nb FROM relectures r JOIN exercices e ON e.id = r.exercice_id
                    WHERE e.session_id = ? GROUP BY r.relecteur_id
                ) charges
                """, session.id())).isLessThanOrEqualTo(2);
    }

    private record SessionOuverte(long id, String code) {
    }

    private SessionOuverte ouvrirSession() throws Exception {
        String reponse = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titre": "Session d'attribution", "promotionId": 1}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(reponse, "$.id");
        String code = JsonPath.read(reponse, "$.code");
        return new SessionOuverte(id.longValue(), code);
    }

    private void emarger(String code, long etudiantId) throws Exception {
        mockMvc.perform(post("/api/presences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "%s", "etudiantId": %d}
                                """.formatted(code, etudiantId)))
                .andExpect(status().isCreated());
    }

    private ResultActions deposer(long sessionId, long etudiantId) throws Exception {
        return mockMvc.perform(post("/api/exercices")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sessionId": %d, "etudiantId": %d, "lien": "https://exemple.com/exercice-%d"}
                        """.formatted(sessionId, etudiantId, etudiantId)));
    }

    private Long relecteurDe(long sessionId, long auteurId) {
        return jdbcTemplate.queryForObject("""
                SELECT r.relecteur_id FROM relectures r JOIN exercices e ON e.id = r.exercice_id
                WHERE e.session_id = ? AND e.etudiant_id = ?
                """, Long.class, sessionId, auteurId);
    }

    private String statutExercice(long sessionId, long auteurId) {
        return jdbcTemplate.queryForObject("SELECT statut FROM exercices WHERE session_id = ? AND etudiant_id = ?",
                String.class, sessionId, auteurId);
    }

    private long compter(String sql, long sessionId) {
        return jdbcTemplate.queryForObject(sql, Long.class, sessionId);
    }
}
