package com.kfokam48.epreuve;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket #16 : la documentation interactive est servie par le backend et affiche le contrat lui-même.
 * Même contexte Spring que les autres tests d'intégration (Docker requis, sinon ignoré).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ConteneurPostgresConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class DocumentationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void leContratEstServiTelQuel() throws Exception {
        mockMvc.perform(get("/docs/contrat.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("openapi: 3.0.3")))
                .andExpect(content().string(containsString("/api/sessions/{id}/cloture")));
    }

    @Test
    void swaggerUiEstServiEtPointeSurLeContrat() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/docs/contrat.yaml"));
    }

    @Test
    void laDescriptionGenereeRegroupeLesRoutesApiParSection() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[*].name",
                        hasItems("Sessions", "Présences", "Exercices", "Relectures", "Tableau")))
                .andExpect(jsonPath("$.paths['/api/presences'].post.tags[0]").value("Présences"))
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/cloture'].post.tags[0]").value("Sessions"))
                // paths-to-match : aucune route hors /api dans la description générée.
                .andExpect(jsonPath("$.paths.keys()", everyItem(startsWith("/api/"))));
    }
}
