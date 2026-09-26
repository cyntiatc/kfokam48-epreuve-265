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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EF4 de bout en bout sur un vrai PostgreSQL : deux relecteurs par exercice (RG8, évolution de l'Étape 3),
 * attribués au dépôt et complétés à l'émargement.
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
    void depot_avecDeuxPresentsEligibles_attribueAussitotDeuxRelecteursDistincts() throws Exception {
        SessionOuverte session = ouvrirSession();
        emarger(session.code(), 1);
        emarger(session.code(), 2);
        emarger(session.code(), 3);

        // Brice (2) et Carine (3) sont les seuls présents éligibles pour l'exercice d'Alice (1).
        deposer(session.id(), 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_RELECTURE"))
                .andExpect(jsonPath("$.relecteursAttribues").value(2));
        assertThat(relecteursDe(session.id(), 1)).containsExactly(2L, 3L);

        // David (4), absent, dépose aussi (H5) : Alice, sans relecture, est tirée d'abord, puis Brice ou Carine.
        deposer(session.id(), 4)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relecteursAttribues").value(2));
        assertThat(relecteursDe(session.id(), 4)).hasSize(2).contains(1L).doesNotContain(4L);
    }

    @Test
    void depot_sansRelecteurEligible_estCompleteAuxPresencesSuivantes() throws Exception {
        SessionOuverte session = ouvrirSession();

        deposer(session.id(), 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("DEPOSE"))
                .andExpect(jsonPath("$.relecteursAttribues").value(0));

        emarger(session.code(), 1);  // l'auteure seule présente : toujours aucun relecteur (RG2)
        assertThat(statutExercice(session.id(), 1)).isEqualTo("DEPOSE");

        emarger(session.code(), 2);  // Brice arrive : premier relecteur
        assertThat(statutExercice(session.id(), 1)).isEqualTo("EN_RELECTURE");
        assertThat(relecteursDe(session.id(), 1)).containsExactly(2L);

        emarger(session.code(), 3);  // Carine arrive : second relecteur
        assertThat(relecteursDe(session.id(), 1)).containsExactly(2L, 3L);

        emarger(session.code(), 4);  // l'exercice a ses deux relecteurs : David n'en devient pas un troisième
        assertThat(relecteursDe(session.id(), 1)).containsExactly(2L, 3L);
    }

    @Test
    void cinqPresents_chaqueExerciceADeuxRelecteursDistinctsQuiNeSontJamaisSonAuteur() throws Exception {
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
                """, session.id())).isEqualTo(10);
        assertThat(compter("""
                SELECT COUNT(*) FROM (
                    SELECT e.id FROM exercices e JOIN relectures r ON r.exercice_id = e.id
                    WHERE e.session_id = ? GROUP BY e.id HAVING COUNT(DISTINCT r.relecteur_id) = 2
                ) exercices_complets
                """, session.id())).isEqualTo(5);
        assertThat(compter("""
                SELECT COUNT(*) FROM relectures r JOIN exercices e ON e.id = r.exercice_id
                WHERE e.session_id = ? AND r.relecteur_id = e.etudiant_id
                """, session.id())).isZero();
        // Répartition (section 7.2) : 10 relectures pour 5 présents. L'exclusion de l'auteur empêche parfois
        // l'équilibre parfait ; sur les 2 448 tirages possibles de ce scénario, la charge maximale est de 3.
        assertThat(compter("""
                SELECT COALESCE(MAX(nb), 0) FROM (
                    SELECT COUNT(*) AS nb FROM relectures r JOIN exercices e ON e.id = r.exercice_id
                    WHERE e.session_id = ? GROUP BY r.relecteur_id
                ) charges
                """, session.id())).isLessThanOrEqualTo(3);
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

    /** Relecteurs de l'exercice de l'auteur dans la session, par identifiant croissant. */
    private List<Long> relecteursDe(long sessionId, long auteurId) {
        return jdbcTemplate.queryForList("""
                SELECT r.relecteur_id FROM relectures r JOIN exercices e ON e.id = r.exercice_id
                WHERE e.session_id = ? AND e.etudiant_id = ? ORDER BY r.relecteur_id
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
