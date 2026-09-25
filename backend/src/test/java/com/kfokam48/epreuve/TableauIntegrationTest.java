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
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EF7 de bout en bout sur un vrai PostgreSQL. La promotion et ses étudiants sont créés pour ce test :
 * les autres tests d'intégration partagent la base et ajoutent de l'activité à la promotion 1.
 * Nécessite Docker : sans Docker, ces tests sont ignorés.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ConteneurPostgresConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class TableauIntegrationTest {

    private final String suffixe = UUID.randomUUID().toString().substring(0, 8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void tableau_calculeLesIndicateursDeChaqueEtudiantDeLaPromotion() throws Exception {
        long promotionId = creerPromotion();
        long aline = creerEtudiant(promotionId, "Atangana", "Aline");
        long boris = creerEtudiant(promotionId, "Bella", "Boris");
        long chloe = creerEtudiant(promotionId, "Chouta", "Chloe");
        creerEtudiant(promotionId, "Dikongue", "Denis");

        // Session 1 : Aline et Boris présents. Boris est le seul relecteur possible d'Aline et lui met 15.
        SessionOuverte session1 = ouvrirSession(promotionId);
        emarger(session1, aline);
        emarger(session1, boris);
        rendre(relectureDe(deposer(session1, aline)), 15);

        // Session 2 : Aline et Chloé présentes. Chloé met 16 à Aline ; l'exercice de Chloé attend la relecture d'Aline.
        SessionOuverte session2 = ouvrirSession(promotionId);
        emarger(session2, aline);
        emarger(session2, chloe);
        rendre(relectureDe(deposer(session2, aline)), 16);
        deposer(session2, chloe);

        // Session 3 : Aline et Boris présents. Boris met 16 à Aline.
        SessionOuverte session3 = ouvrirSession(promotionId);
        emarger(session3, aline);
        emarger(session3, boris);
        rendre(relectureDe(deposer(session3, aline)), 16);

        mockMvc.perform(get("/api/tableau").param("promotionId", String.valueOf(promotionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                // Aline : notes 15, 16 et 16, soit 15,67 arrondi à 2 décimales ; doit encore relire Chloé.
                .andExpect(jsonPath("$[0].etudiantId").value(Math.toIntExact(aline)))
                .andExpect(jsonPath("$[0].nom").value("Atangana Aline"))
                .andExpect(jsonPath("$[0].presences").value(3))
                .andExpect(jsonPath("$[0].exercicesDeposes").value(3))
                .andExpect(jsonPath("$[0].moyenne").value(15.67))
                .andExpect(jsonPath("$[0].relecturesEnAttente").value(1))
                // Boris : deux présences, aucun dépôt, ses deux relectures sont rendues.
                .andExpect(jsonPath("$[1].nom").value("Bella Boris"))
                .andExpect(jsonPath("$[1].presences").value(2))
                .andExpect(jsonPath("$[1].exercicesDeposes").value(0))
                .andExpect(jsonPath("$[1].moyenne").value(nullValue()))
                .andExpect(jsonPath("$[1].relecturesEnAttente").value(0))
                // Chloé : un dépôt pas encore relu, donc pas de moyenne.
                .andExpect(jsonPath("$[2].nom").value("Chouta Chloe"))
                .andExpect(jsonPath("$[2].presences").value(1))
                .andExpect(jsonPath("$[2].exercicesDeposes").value(1))
                .andExpect(jsonPath("$[2].moyenne").value(nullValue()))
                .andExpect(jsonPath("$[2].relecturesEnAttente").value(0))
                // Denis : aucune activité, mais bien présent dans le tableau (EF7).
                .andExpect(jsonPath("$[3].nom").value("Dikongue Denis"))
                .andExpect(jsonPath("$[3].presences").value(0))
                .andExpect(jsonPath("$[3].exercicesDeposes").value(0))
                .andExpect(jsonPath("$[3].moyenne").value(nullValue()))
                .andExpect(jsonPath("$[3].relecturesEnAttente").value(0));
    }

    @Test
    void tableau_promotionInconnue_renvoie404PromotionInconnue() throws Exception {
        mockMvc.perform(get("/api/tableau").param("promotionId", "999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROMOTION_INCONNUE"));
    }

    private record SessionOuverte(long id, String code) {
    }

    private long creerPromotion() {
        return jdbcTemplate.queryForObject("INSERT INTO promotions (libelle) VALUES (?) RETURNING id",
                Long.class, "Promotion tableau " + suffixe);
    }

    private long creerEtudiant(long promotionId, String nom, String prenom) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO etudiants (matricule, nom, prenom, email, promotion_id)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, "T" + suffixe + prenom.charAt(0), nom, prenom,
                prenom.toLowerCase() + "." + suffixe + "@example.com", promotionId);
    }

    private SessionOuverte ouvrirSession(long promotionId) throws Exception {
        String reponse = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titre": "Session du tableau", "promotionId": %d}
                                """.formatted(promotionId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(reponse, "$.id");
        String code = JsonPath.read(reponse, "$.code");
        return new SessionOuverte(id.longValue(), code);
    }

    private void emarger(SessionOuverte session, long etudiantId) throws Exception {
        mockMvc.perform(post("/api/presences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "%s", "etudiantId": %d}
                                """.formatted(session.code(), etudiantId)))
                .andExpect(status().isCreated());
    }

    /** Dépose un exercice et renvoie son identifiant. */
    private long deposer(SessionOuverte session, long etudiantId) throws Exception {
        String reponse = mockMvc.perform(post("/api/exercices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId": %d, "etudiantId": %d, "lien": "https://exemple.com/exercice-%d"}
                                """.formatted(session.id(), etudiantId, etudiantId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(reponse, "$.id");
        return id.longValue();
    }

    private long relectureDe(long exerciceId) {
        return jdbcTemplate.queryForObject("SELECT id FROM relectures WHERE exercice_id = ?", Long.class, exerciceId);
    }

    private void rendre(long relectureId, int note) throws Exception {
        mockMvc.perform(post("/api/relectures/" + relectureId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note": %d, "commentaire": "Relecture pour le tableau."}
                                """.formatted(note)))
                .andExpect(status().isOk());
    }
}
