package com.kfokam48.epreuve.web;

import com.kfokam48.epreuve.domain.Promotion;
import com.kfokam48.epreuve.domain.SessionCours;
import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import com.kfokam48.epreuve.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionController.class)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionService sessionService;

    @Test
    void ouvrirSession_renvoie201AvecLesChampsDuContrat() throws Exception {
        SessionCours session = new SessionCours(new Promotion("L3 GL"), "Spring Boot", "K7P2QX",
                Instant.parse("2026-09-28T08:00:00Z"));
        ReflectionTestUtils.setField(session, "id", 12L);
        when(sessionService.ouvrirSession("Spring Boot", 3L)).thenReturn(session);

        envoyer("""
                {"titre": "Spring Boot", "promotionId": 3}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.code").value("K7P2QX"))
                .andExpect(jsonPath("$.ouvertureAt").value("2026-09-28T08:00:00Z"))
                .andExpect(jsonPath("$.expirationAt").value("2026-09-28T08:15:00Z"))
                .andExpect(jsonPath("$.titre").doesNotExist())
                .andExpect(jsonPath("$.statut").doesNotExist());
    }

    @Test
    void ouvrirSession_sansTitre_renvoie400RequeteInvalide() throws Exception {
        envoyer("""
                {"promotionId": 3}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message").value("Le champ titre est obligatoire."));
        verifyNoInteractions(sessionService);
    }

    @Test
    void ouvrirSession_titreVide_renvoie400RequeteInvalide() throws Exception {
        envoyer("""
                {"titre": "   ", "promotionId": 3}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
        verifyNoInteractions(sessionService);
    }

    @Test
    void ouvrirSession_titreTropLong_renvoie400RequeteInvalide() throws Exception {
        envoyer("""
                {"titre": "%s", "promotionId": 3}
                """.formatted("x".repeat(151)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message").value("Le titre ne doit pas dépasser 150 caractères."));
        verifyNoInteractions(sessionService);
    }

    @Test
    void ouvrirSession_sansPromotion_renvoie400RequeteInvalide() throws Exception {
        envoyer("""
                {"titre": "Spring Boot"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message").value("Le champ promotionId est obligatoire."));
        verifyNoInteractions(sessionService);
    }

    @Test
    void ouvrirSession_corpsMalForme_renvoie400RequeteInvalide() throws Exception {
        envoyer("{\"titre\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message").value("Le corps de la requête est absent ou mal formé."));
        verifyNoInteractions(sessionService);
    }

    @Test
    void ouvrirSession_promotionInconnue_renvoie400RequeteInvalide() throws Exception {
        when(sessionService.ouvrirSession("Spring Boot", 99L)).thenThrow(new ErreurMetierException(
                CodeErreur.REQUETE_INVALIDE, "Aucune promotion ne correspond à l'identifiant 99."));

        envoyer("""
                {"titre": "Spring Boot", "promotionId": 99}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message").value("Aucune promotion ne correspond à l'identifiant 99."));
    }

    @Test
    void ouvrirSession_erreurInattendue_renvoie500SansDetailTechnique() throws Exception {
        when(sessionService.ouvrirSession(any(), any())).thenThrow(new IllegalStateException("détail interne"));

        envoyer("""
                {"titre": "Spring Boot", "promotionId": 3}
                """)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("ERREUR_INTERNE"))
                .andExpect(jsonPath("$.message").value("Une erreur interne est survenue."));
    }

    private ResultActions envoyer(String json) throws Exception {
        return mockMvc.perform(post("/api/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
