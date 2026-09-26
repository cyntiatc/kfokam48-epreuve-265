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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Double relecture (évolution de l'Étape 3) : les deux relecteurs d'un même exercice rendent leur note au même
 * instant. Chacune des deux transactions doit voir l'autre relecture rendue, sinon aucune ne fait passer l'exercice
 * RELU. Test de non-régression du verrou posé sur l'exercice ({@code ExerciceRepository.findPourMiseAJour}).
 * Nécessite Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ConteneurPostgresConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class RelectureConcurrencyIntegrationTest {

    private static final int TENTATIVES = 20;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deuxRelecturesDuMemeExerciceRenduesSimultanement_passentLExerciceRelu() throws Exception {
        for (int tentative = 1; tentative <= TENTATIVES; tentative++) {
            SessionOuverte session = ouvrirSessionPourLaPromotion1();
            for (long etudiant = 1; etudiant <= 3; etudiant++) {
                emarger(session.code(), etudiant);
            }
            long exerciceId = deposerExercice(session.id(), 1);  // relu par Brice (2) et Carine (3)
            List<Long> relectures = jdbcTemplate.queryForList(
                    "SELECT id FROM relectures WHERE exercice_id = ? ORDER BY id", Long.class, exerciceId);

            List<Integer> statuts = rendreSimultanement(relectures);

            assertThat(statuts).as("tentative %d : réponses des deux relectures", tentative).containsExactly(200, 200);
            assertThat(jdbcTemplate.queryForObject("SELECT statut FROM exercices WHERE id = ?", String.class, exerciceId))
                    .as("tentative %d : statut de l'exercice", tentative).isEqualTo("RELU");
        }
    }

    /** Rend chaque relecture dans son propre thread ; les threads sont libérés au même instant. */
    private List<Integer> rendreSimultanement(List<Long> relectures) throws Exception {
        ExecutorService executeur = Executors.newFixedThreadPool(relectures.size());
        CountDownLatch prets = new CountDownLatch(relectures.size());
        CountDownLatch depart = new CountDownLatch(1);
        try {
            List<Future<Integer>> envois = new ArrayList<>();
            for (long relectureId : relectures) {
                envois.add(executeur.submit(() -> {
                    prets.countDown();
                    depart.await();
                    return mockMvc.perform(post("/api/relectures/" + relectureId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"note": 14, "commentaire": "Relecture simultanée."}
                                            """))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertThat(prets.await(10, TimeUnit.SECONDS)).as("threads prêts").isTrue();
            depart.countDown();

            List<Integer> statuts = new ArrayList<>();
            for (Future<Integer> envoi : envois) {
                statuts.add(envoi.get(30, TimeUnit.SECONDS));
            }
            return statuts;
        } finally {
            executeur.shutdownNow();
        }
    }

    private SessionOuverte ouvrirSessionPourLaPromotion1() throws Exception {
        String reponse = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titre": "Session de relectures simultanées", "promotionId": 1}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new SessionOuverte(JsonPath.<Number>read(reponse, "$.id").longValue(), JsonPath.read(reponse, "$.code"));
    }

    private void emarger(String code, long etudiantId) throws Exception {
        mockMvc.perform(post("/api/presences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "%s", "etudiantId": %d}
                                """.formatted(code, etudiantId)))
                .andExpect(status().isCreated());
    }

    private long deposerExercice(long sessionId, long etudiantId) throws Exception {
        String reponse = mockMvc.perform(post("/api/exercices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId": %d, "etudiantId": %d, "lien": "https://github.com/exemple/relectures"}
                                """.formatted(sessionId, etudiantId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relecteursAttribues").value(2))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(reponse, "$.id").longValue();
    }

    private record SessionOuverte(long id, String code) {
    }
}
