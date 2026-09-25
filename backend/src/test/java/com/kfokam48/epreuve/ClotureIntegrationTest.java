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
 * EF8 de bout en bout sur un vrai PostgreSQL : effets de la clôture sur le code, les notes et les exercices.
 * Nécessite Docker : sans Docker, ces tests sont ignorés.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ConteneurPostgresConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class ClotureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void cloture_figeLesNotesRenduesEtRefuseLeCodeMaisLaisseRendreLesRelecturesEnAttente() throws Exception {
        SessionOuverte session = ouvrirSession();
        emarger(session.code(), 1).andExpect(status().isCreated());
        emarger(session.code(), 2).andExpect(status().isCreated());
        long exerciceAlice = deposer(session.id(), 1);   // relu par Brice (seul candidat)
        long exerciceBrice = deposer(session.id(), 2);   // relu par Alice (seule candidate)
        rendre(relectureDe(exerciceAlice), 15).andExpect(status().isOk());

        mockMvc.perform(post("/api/sessions/" + session.id() + "/cloture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("CLOTUREE"))
                .andExpect(jsonPath("$.clotureAt").isNotEmpty())
                .andExpect(jsonPath("$.exercicesDefinitifs").value(1))
                .andExpect(jsonPath("$.exercicesSansRelecture").value(0))
                .andExpect(jsonPath("$.relecturesEnAttente").value(1));
        assertThat(statutExercice(exerciceAlice)).isEqualTo("DEFINITIF");
        assertThat(statutExercice(exerciceBrice)).isEqualTo("EN_RELECTURE");

        // RG9 : la note rendue ne peut plus être modifiée.
        rendre(relectureDe(exerciceAlice), 18)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RELECTURE_DEJA_RENDUE"));
        // H8 : la relecture en attente peut encore être rendue une fois, et la note est aussitôt définitive.
        rendre(relectureDe(exerciceBrice), 13).andExpect(status().isOk());
        assertThat(statutExercice(exerciceBrice)).isEqualTo("DEFINITIF");
        // RG1 : le code de la session clôturée est refusé.
        emarger(session.code(), 3)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRE"));
        // La clôture est irréversible et ne se refait pas.
        mockMvc.perform(post("/api/sessions/" + session.id() + "/cloture"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_DEJA_CLOTUREE"));
    }

    @Test
    void cloture_exerciceSansRelecteur_passeSansRelecture() throws Exception {
        SessionOuverte session = ouvrirSession();
        emarger(session.code(), 1).andExpect(status().isCreated());
        long exercice = deposer(session.id(), 1);  // l'auteure est la seule présente : aucun relecteur (RG2)

        mockMvc.perform(post("/api/sessions/" + session.id() + "/cloture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercicesSansRelecture").value(1));
        assertThat(statutExercice(exercice)).isEqualTo("SANS_RELECTURE");
    }

    @Test
    void cloture_sessionInconnue_renvoie400RequeteInvalide() throws Exception {
        mockMvc.perform(post("/api/sessions/999999/cloture"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
    }

    private record SessionOuverte(long id, String code) {
    }

    private SessionOuverte ouvrirSession() throws Exception {
        String reponse = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titre": "Session a cloturer", "promotionId": 1}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(reponse, "$.id");
        String code = JsonPath.read(reponse, "$.code");
        return new SessionOuverte(id.longValue(), code);
    }

    private ResultActions emarger(String code, long etudiantId) throws Exception {
        return mockMvc.perform(post("/api/presences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "%s", "etudiantId": %d}
                        """.formatted(code, etudiantId)));
    }

    /** Dépose un exercice et renvoie son identifiant. */
    private long deposer(long sessionId, long etudiantId) throws Exception {
        String reponse = mockMvc.perform(post("/api/exercices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId": %d, "etudiantId": %d, "lien": "https://exemple.com/exercice-%d"}
                                """.formatted(sessionId, etudiantId, etudiantId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(reponse, "$.id");
        return id.longValue();
    }

    private long relectureDe(long exerciceId) {
        return jdbcTemplate.queryForObject("SELECT id FROM relectures WHERE exercice_id = ?", Long.class, exerciceId);
    }

    private ResultActions rendre(long relectureId, int note) throws Exception {
        return mockMvc.perform(post("/api/relectures/" + relectureId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"note": %d, "commentaire": "Relecture avant ou après la clôture."}
                        """.formatted(note)));
    }

    private String statutExercice(long exerciceId) {
        return jdbcTemplate.queryForObject("SELECT statut FROM exercices WHERE id = ?", String.class, exerciceId);
    }
}
