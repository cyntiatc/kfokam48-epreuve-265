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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EF5 et EF6 de bout en bout sur un vrai PostgreSQL : rendre, modifier avant la clôture, refuser après.
 * Nécessite Docker : sans Docker, ces tests sont ignorés.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ConteneurPostgresConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class RelectureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rendre_modifierAvantCloture_puisRefuserApresCloture() throws Exception {
        RelectureAttribuee attribuee = preparerRelectureAttribuee();

        rendre(attribuee.relectureId(), "15", "Bon travail.").andExpect(status().isOk());
        assertThat(colonne("statut", attribuee.relectureId())).isEqualTo("RENDUE");
        assertThat(note(attribuee.relectureId())).isEqualTo(15);
        assertThat(statutExercice(attribuee.relectureId())).isEqualTo("RELU");

        // Décision Q10 : la note reste modifiable tant que la session est ouverte.
        rendre(attribuee.relectureId(), "17", "Note corrigée.").andExpect(status().isOk());
        assertThat(note(attribuee.relectureId())).isEqualTo(17);
        assertThat(colonne("modifiee_at", attribuee.relectureId())).isNotNull();

        cloturer(attribuee.sessionId());
        rendre(attribuee.relectureId(), "20", "Trop tard.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RELECTURE_DEJA_RENDUE"));
        assertThat(note(attribuee.relectureId())).isEqualTo(17);
    }

    @Test
    void premiereSoumissionApresCloture_rendLaNoteDefinitive() throws Exception {
        RelectureAttribuee attribuee = preparerRelectureAttribuee();
        cloturer(attribuee.sessionId());

        // H8 : une relecture encore en attente peut être rendue une fois après la clôture.
        rendre(attribuee.relectureId(), "14", "Rendue après la clôture.").andExpect(status().isOk());
        assertThat(statutExercice(attribuee.relectureId())).isEqualTo("DEFINITIF");
    }

    @Test
    void noteInvalideOuRelectureInconnue_renvoient400() throws Exception {
        RelectureAttribuee attribuee = preparerRelectureAttribuee();

        rendre(attribuee.relectureId(), "21", "Hors bornes.")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTE_INVALIDE"));
        rendre(attribuee.relectureId(), "12.5", "Non entière.")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTE_INVALIDE"));
        rendre(999_999, "15", "Relecture inconnue.")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
        assertThat(colonne("statut", attribuee.relectureId())).isEqualTo("EN_ATTENTE");
    }

    @Test
    void relecteurQuiEstLAuteur_renvoie403AutoRelecture() throws Exception {
        SessionOuverte session = ouvrirSession();
        // Situation impossible par l'API (le tirage exclut l'auteur) : fabriquée en base pour tester le contrôle de sûreté.
        Long exerciceId = jdbcTemplate.queryForObject("""
                INSERT INTO exercices (session_id, etudiant_id, lien, statut, depose_at)
                VALUES (?, 4, 'https://exemple.com/exercice-4', 'EN_RELECTURE', now()) RETURNING id
                """, Long.class, session.id());
        Long relectureId = jdbcTemplate.queryForObject("""
                INSERT INTO relectures (exercice_id, relecteur_id, statut, attribuee_at)
                VALUES (?, 4, 'EN_ATTENTE', now()) RETURNING id
                """, Long.class, exerciceId);

        rendre(relectureId, "20", "Je me note moi-même.")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTO_RELECTURE"));
    }

    @Test
    void consulterSesRelectures_donneLIdentifiantEtLeLienPuisSuitLEtatDeLaRelecture() throws Exception {
        RelectureAttribuee attribuee = preparerRelectureAttribuee();

        // Avant la notation : Brice voit la relecture en attente et le lien de l'exercice d'Alice.
        assertThat(relectureVueParBrice(attribuee.relectureId()))
                .containsEntry("statut", "EN_ATTENTE")
                .containsEntry("lien", "https://exemple.com/exercice-1")
                .containsEntry("modifiable", true);

        rendre(attribuee.relectureId(), "15", "Bon travail.").andExpect(status().isOk());
        assertThat(relectureVueParBrice(attribuee.relectureId()))
                .containsEntry("statut", "RENDUE")
                .containsEntry("note", 15)
                .containsEntry("modifiable", true);

        cloturer(attribuee.sessionId());
        assertThat(relectureVueParBrice(attribuee.relectureId())).containsEntry("modifiable", false);
    }

    /** Ligne de GET /api/relectures?relecteurId=2 correspondant à la relecture (Brice a aussi celles des autres tests). */
    private Map<String, Object> relectureVueParBrice(long relectureId) throws Exception {
        String reponse = mockMvc.perform(get("/api/relectures").param("relecteurId", "2"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> lignes = JsonPath.read(reponse, "$[?(@.id == " + relectureId + ")]");
        assertThat(lignes).hasSize(1);
        return lignes.get(0);
    }

    private record SessionOuverte(long id, String code) {
    }

    private record RelectureAttribuee(long sessionId, long relectureId) {
    }

    /** Alice (1) et Brice (2) émargent, Alice dépose : Brice est le seul relecteur possible (EF4). */
    private RelectureAttribuee preparerRelectureAttribuee() throws Exception {
        SessionOuverte session = ouvrirSession();
        emarger(session.code(), 1);
        emarger(session.code(), 2);
        mockMvc.perform(post("/api/exercices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId": %d, "etudiantId": 1, "lien": "https://exemple.com/exercice-1"}
                                """.formatted(session.id())))
                .andExpect(status().isCreated());
        Long relectureId = jdbcTemplate.queryForObject("""
                SELECT r.id FROM relectures r JOIN exercices e ON e.id = r.exercice_id
                WHERE e.session_id = ? AND e.etudiant_id = 1
                """, Long.class, session.id());
        return new RelectureAttribuee(session.id(), relectureId);
    }

    private SessionOuverte ouvrirSession() throws Exception {
        String reponse = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titre": "Session de relecture", "promotionId": 1}
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

    private ResultActions rendre(long relectureId, String note, String commentaire) throws Exception {
        return mockMvc.perform(post("/api/relectures/" + relectureId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"note": %s, "commentaire": "%s"}
                        """.formatted(note, commentaire)));
    }

    private void cloturer(long sessionId) {
        jdbcTemplate.update("UPDATE sessions SET statut = 'CLOTUREE', cloture_at = now() WHERE id = ?", sessionId);
    }

    private Object colonne(String nom, long relectureId) {
        return jdbcTemplate.queryForObject("SELECT " + nom + " FROM relectures WHERE id = ?", Object.class, relectureId);
    }

    private Integer note(long relectureId) {
        return jdbcTemplate.queryForObject("SELECT note FROM relectures WHERE id = ?", Integer.class, relectureId);
    }

    private String statutExercice(long relectureId) {
        return jdbcTemplate.queryForObject("""
                SELECT e.statut FROM exercices e JOIN relectures r ON r.exercice_id = e.id WHERE r.id = ?
                """, String.class, relectureId);
    }
}
