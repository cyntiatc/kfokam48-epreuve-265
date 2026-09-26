package com.kfokam48.epreuve;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
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
 * Ticket #17 : deux étudiants qui émargent au même instant sur la même session doivent être enregistrés
 * tous les deux (EF2, ENF3). Chaque scénario est rejoué plusieurs fois, les deux requêtes étant libérées
 * ensemble, car une concurrence ne se manifeste pas à chaque exécution. Nécessite Docker.
 * <p>
 * Test de non-régression du verrou posé sur les exercices en attente de relecteur
 * ({@code ExerciceRepository.findBySessionIdAndStatutInOrderByDeposeAtAscIdAsc}, ticket #4) : sans lui, les deux
 * émargements complètent le même exercice sans se voir. Avant la double relecture (V5), le second échouait en 500 sur
 * {@code uk_relectures_exercice} et sa présence était annulée ; depuis, un exercice qui attend son second relecteur
 * en recevrait un de trop (troisième scénario).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ConteneurPostgresConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class PresenceConcurrencyIntegrationTest {

    private static final int TENTATIVES = 20;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deuxEmargementsSimultanes_surUneSessionSansExercice_sontTousDeuxEnregistres() throws Exception {
        for (int tentative = 1; tentative <= TENTATIVES; tentative++) {
            SessionOuverte session = ouvrirSessionPourLaPromotion1();

            List<String> reponses = marquerSimultanement(session.code(), 1, 2);

            assertThat(reponses).as("tentative %d : réponses des deux émargements", tentative)
                    .containsExactly("201", "201");
            assertThat(nombrePresences(session.id())).as("tentative %d : présences en base", tentative)
                    .isEqualTo(2);
        }
    }

    /**
     * Les deux émargements déclenchent l'attribution du même exercice en attente (EF4) :
     * c'est le seul traitement où leurs transactions se disputent une même ligne.
     */
    @Test
    void deuxEmargementsSimultanes_avecUnExerciceEnAttente_sontTousDeuxEnregistres() throws Exception {
        for (int tentative = 1; tentative <= TENTATIVES; tentative++) {
            SessionOuverte session = ouvrirSessionPourLaPromotion1();
            // Personne n'est encore présent : l'exercice de l'étudiant 3 reste DEPOSE, en attente de relecteur.
            long exerciceId = deposerExercice(session.id(), 3);

            List<String> reponses = marquerSimultanement(session.code(), 1, 2);

            assertThat(reponses).as("tentative %d : réponses des deux émargements", tentative)
                    .containsExactly("201", "201");
            assertThat(nombrePresences(session.id())).as("tentative %d : présences en base", tentative)
                    .isEqualTo(2);
            // RG8 : les deux présents deviennent les deux relecteurs, distincts, de l'exercice de l'étudiant 3.
            assertThat(relecteurs(exerciceId)).as("tentative %d : relecteurs de l'exercice (RG8)", tentative)
                    .containsExactly(1L, 2L);
        }
    }

    /**
     * L'exercice a déjà un relecteur sur les deux attendus (RG8) : un seul des deux émargements simultanés doit
     * devenir le second, jamais les deux.
     */
    @Test
    void deuxEmargementsSimultanes_avecUnExerciceAttendantSonSecondRelecteur_nEnAjoutentQuUn() throws Exception {
        for (int tentative = 1; tentative <= TENTATIVES; tentative++) {
            SessionOuverte session = ouvrirSessionPourLaPromotion1();
            // David (4) est présent au dépôt de l'étudiant 3 : il en est le premier relecteur.
            assertThat(marquer(session.code(), 4).getStatus()).isEqualTo(201);
            long exerciceId = deposerExercice(session.id(), 3, "EN_RELECTURE");

            List<String> reponses = marquerSimultanement(session.code(), 1, 2);

            assertThat(reponses).as("tentative %d : réponses des deux émargements", tentative)
                    .containsExactly("201", "201");
            assertThat(nombrePresences(session.id())).as("tentative %d : présences en base", tentative)
                    .isEqualTo(3);
            assertThat(relecteurs(exerciceId)).as("tentative %d : relecteurs de l'exercice (RG8)", tentative)
                    .hasSize(2).contains(4L);
        }
    }

    /**
     * Envoie un émargement par étudiant, chacun dans son propre thread. Les threads attendent tous d'être prêts
     * avant d'être libérés au même instant. Renvoie, dans l'ordre des étudiants, le statut HTTP de chaque réponse,
     * suivi de son corps s'il ne s'agit pas d'un 201.
     */
    private List<String> marquerSimultanement(String code, long... etudiantIds) throws Exception {
        ExecutorService executeur = Executors.newFixedThreadPool(etudiantIds.length);
        CountDownLatch prets = new CountDownLatch(etudiantIds.length);
        CountDownLatch depart = new CountDownLatch(1);
        try {
            List<Future<String>> envois = new ArrayList<>();
            for (long etudiantId : etudiantIds) {
                envois.add(executeur.submit(() -> {
                    prets.countDown();
                    depart.await();
                    return decrire(marquer(code, etudiantId));
                }));
            }
            assertThat(prets.await(10, TimeUnit.SECONDS)).as("threads prêts").isTrue();
            depart.countDown();

            List<String> reponses = new ArrayList<>();
            for (Future<String> envoi : envois) {
                reponses.add(envoi.get(30, TimeUnit.SECONDS));
            }
            return reponses;
        } finally {
            executeur.shutdownNow();
        }
    }

    private MockHttpServletResponse marquer(String code, long etudiantId) throws Exception {
        return mockMvc.perform(post("/api/presences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "%s", "etudiantId": %d}
                                """.formatted(code, etudiantId)))
                .andReturn().getResponse();
    }

    private static String decrire(MockHttpServletResponse reponse) throws Exception {
        String statut = String.valueOf(reponse.getStatus());
        return reponse.getStatus() == 201 ? statut : statut + " " + reponse.getContentAsString();
    }

    private SessionOuverte ouvrirSessionPourLaPromotion1() throws Exception {
        String reponse = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titre": "Session de concurrence", "promotionId": 1}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new SessionOuverte(JsonPath.<Number>read(reponse, "$.id").longValue(), JsonPath.read(reponse, "$.code"));
    }

    private long deposerExercice(long sessionId, long etudiantId) throws Exception {
        return deposerExercice(sessionId, etudiantId, "DEPOSE");
    }

    /** Dépose l'exercice, vérifie le statut obtenu et renvoie son identifiant. */
    private long deposerExercice(long sessionId, long etudiantId, String statutAttendu) throws Exception {
        String reponse = mockMvc.perform(post("/api/exercices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId": %d, "etudiantId": %d, "lien": "https://github.com/exemple/concurrence"}
                                """.formatted(sessionId, etudiantId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value(statutAttendu))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(reponse, "$.id").longValue();
    }

    private int nombrePresences(long sessionId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM presences WHERE session_id = ?", Integer.class, sessionId);
    }

    private List<Long> relecteurs(long exerciceId) {
        return jdbcTemplate.queryForList("SELECT relecteur_id FROM relectures WHERE exercice_id = ? ORDER BY relecteur_id",
                Long.class, exerciceId);
    }

    private record SessionOuverte(long id, String code) {
    }
}
