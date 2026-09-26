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

import static com.kfokam48.epreuve.ConformiteContrat.conformeAuContrat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket #14 (ENF1, ENF2) : chaque route et chaque statut déclarés dans api/contrat.yaml sont provoqués sur un vrai
 * PostgreSQL, et chaque réponse est validée par le fichier du contrat lui-même (champs, types, formats, énumérations).
 * Nécessite Docker : sans Docker, ces tests sont ignorés.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ConteneurPostgresConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class ContratIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void postSessions_201Et400() throws Exception {
        poster("/api/sessions", """
                {"titre": "Session du contrat", "promotionId": 1}
                """).andExpect(status().isCreated()).andExpect(conformeAuContrat());

        poster("/api/sessions", "{}")
                .andExpect(status().isBadRequest()).andExpect(conformeAuContrat());
    }

    @Test
    void postPresences_201_400_409_410() throws Exception {
        SessionOuverte session = ouvrirSession();

        emarger(session.code(), 1).andExpect(status().isCreated()).andExpect(conformeAuContrat());
        emarger(session.code(), 1).andExpect(status().isConflict()).andExpect(conformeAuContrat());
        emarger("INCONNU", 1).andExpect(status().isBadRequest()).andExpect(conformeAuContrat());

        jdbcTemplate.update("""
                INSERT INTO sessions (promotion_id, titre, code, ouverture_at, expiration_at, statut)
                VALUES (1, 'Session expiree du contrat', 'CTRAT1', now() - INTERVAL '1 hour',
                        now() - INTERVAL '45 minutes', 'OUVERTE')
                """);
        emarger("CTRAT1", 2).andExpect(status().isGone()).andExpect(conformeAuContrat());
    }

    @Test
    void postExercices_201_400_409() throws Exception {
        SessionOuverte session = ouvrirSession();

        deposer(session.id(), 1, "https://exemple.com/exercice-contrat")
                .andExpect(status().isCreated()).andExpect(conformeAuContrat());
        deposer(session.id(), 1, "https://exemple.com/exercice-contrat")
                .andExpect(status().isConflict()).andExpect(conformeAuContrat());
        deposer(session.id(), 2, "ftp://exemple.com/exercice-contrat")
                .andExpect(status().isBadRequest()).andExpect(conformeAuContrat());
    }

    @Test
    void postRelectures_200_400_403_409() throws Exception {
        SessionOuverte session = ouvrirSession();
        emarger(session.code(), 1).andExpect(status().isCreated());
        emarger(session.code(), 2).andExpect(status().isCreated());
        long exercice = identifiant(deposer(session.id(), 1, "https://exemple.com/exercice-1")
                .andExpect(status().isCreated()));
        long relecture = relectureDe(exercice);

        rendre(relecture, "15").andExpect(status().isOk()).andExpect(conformeAuContrat());
        rendre(relecture, "21").andExpect(status().isBadRequest()).andExpect(conformeAuContrat());

        // Auto-relecture impossible par l'API (le tirage exclut l'auteur) : fabriquée en base pour provoquer le 403.
        Long exerciceForge = jdbcTemplate.queryForObject("""
                INSERT INTO exercices (session_id, etudiant_id, lien, statut, depose_at)
                VALUES (?, 4, 'https://exemple.com/exercice-4', 'EN_RELECTURE', now()) RETURNING id
                """, Long.class, session.id());
        Long autoRelecture = jdbcTemplate.queryForObject("""
                INSERT INTO relectures (exercice_id, relecteur_id, statut, attribuee_at)
                VALUES (?, 4, 'EN_ATTENTE', now()) RETURNING id
                """, Long.class, exerciceForge);
        rendre(autoRelecture, "20").andExpect(status().isForbidden()).andExpect(conformeAuContrat());

        poster("/api/sessions/" + session.id() + "/cloture", "").andExpect(status().isOk());
        rendre(relecture, "18").andExpect(status().isConflict()).andExpect(conformeAuContrat());
    }

    @Test
    void getTableau_200_400_404() throws Exception {
        mockMvc.perform(get("/api/tableau").param("promotionId", "1"))
                .andExpect(status().isOk()).andExpect(conformeAuContrat());
        mockMvc.perform(get("/api/tableau"))
                .andExpect(status().isBadRequest()).andExpect(conformeAuContrat());
        mockMvc.perform(get("/api/tableau").param("promotionId", "abc"))
                .andExpect(status().isBadRequest()).andExpect(conformeAuContrat());
        mockMvc.perform(get("/api/tableau").param("promotionId", "999999"))
                .andExpect(status().isNotFound()).andExpect(conformeAuContrat());
    }

    @Test
    void getRelectures_200Et400_extensionH3() throws Exception {
        mockMvc.perform(get("/api/relectures").param("relecteurId", "2"))
                .andExpect(status().isOk()).andExpect(conformeAuContrat());
        mockMvc.perform(get("/api/relectures").param("relecteurId", "999999"))
                .andExpect(status().isBadRequest()).andExpect(conformeAuContrat());
    }

    @Test
    void postCloture_200_400_409_extensionH3() throws Exception {
        SessionOuverte session = ouvrirSession();

        poster("/api/sessions/" + session.id() + "/cloture", "")
                .andExpect(status().isOk()).andExpect(conformeAuContrat());
        poster("/api/sessions/" + session.id() + "/cloture", "")
                .andExpect(status().isConflict()).andExpect(conformeAuContrat());
        poster("/api/sessions/999999/cloture", "")
                .andExpect(status().isBadRequest()).andExpect(conformeAuContrat());
    }

    /** ENF2 : les erreurs que le contrat ne décrit pas gardent quand même le format unique {code, message}. */
    @Test
    void erreursHorsContrat_gardentLeFormatUnique() throws Exception {
        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message").isString());
        mockMvc.perform(get("/api/route-inexistante"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message").isString());
    }

    private record SessionOuverte(long id, String code) {
    }

    private SessionOuverte ouvrirSession() throws Exception {
        String reponse = poster("/api/sessions", """
                {"titre": "Session du contrat", "promotionId": 1}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(reponse, "$.id");
        String code = JsonPath.read(reponse, "$.code");
        return new SessionOuverte(id.longValue(), code);
    }

    private ResultActions emarger(String code, long etudiantId) throws Exception {
        return poster("/api/presences", """
                {"code": "%s", "etudiantId": %d}
                """.formatted(code, etudiantId));
    }

    private ResultActions deposer(long sessionId, long etudiantId, String lien) throws Exception {
        return poster("/api/exercices", """
                {"sessionId": %d, "etudiantId": %d, "lien": "%s"}
                """.formatted(sessionId, etudiantId, lien));
    }

    private ResultActions rendre(long relectureId, String note) throws Exception {
        return poster("/api/relectures/" + relectureId, """
                {"note": %s, "commentaire": "Relecture du test de contrat."}
                """.formatted(note));
    }

    private ResultActions poster(String chemin, String json) throws Exception {
        return mockMvc.perform(post(chemin).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static long identifiant(ResultActions resultat) throws Exception {
        Number id = JsonPath.read(resultat.andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private long relectureDe(long exerciceId) {
        return jdbcTemplate.queryForObject("SELECT id FROM relectures WHERE exercice_id = ?", Long.class, exerciceId);
    }
}
