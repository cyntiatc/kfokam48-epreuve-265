package com.kfokam48.epreuve.web;

import com.kfokam48.epreuve.domain.Etudiant;
import com.kfokam48.epreuve.domain.Presence;
import com.kfokam48.epreuve.domain.Promotion;
import com.kfokam48.epreuve.domain.SessionCours;
import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import com.kfokam48.epreuve.service.PresenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PresenceController.class)
class PresenceControllerTest {

    private static final String CORPS_VALIDE = """
            {"code": "K7P2QX", "etudiantId": 1}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PresenceService presenceService;

    @Test
    void marquerPresence_renvoie201AvecLesChampsDuContrat() throws Exception {
        Promotion promotion = new Promotion("L3 GL");
        SessionCours session = new SessionCours(promotion, "Spring Boot", "K7P2QX",
                Instant.parse("2026-09-28T08:00:00Z"));
        ReflectionTestUtils.setField(session, "id", 12L);
        Etudiant etudiant = new Etudiant("L3GL-001", "Mbarga", "Alice", "alice.mbarga@example.com", promotion);
        ReflectionTestUtils.setField(etudiant, "id", 1L);
        Presence presence = new Presence(session, etudiant, Instant.parse("2026-09-28T08:05:00Z"));
        ReflectionTestUtils.setField(presence, "id", 340L);
        when(presenceService.marquerPresence("K7P2QX", 1L)).thenReturn(presence);

        envoyer(CORPS_VALIDE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(340))
                .andExpect(jsonPath("$.sessionId").value(12))
                .andExpect(jsonPath("$.etudiantId").value(1))
                .andExpect(jsonPath("$.source").value("CODE"))
                .andExpect(jsonPath("$.marqueeAt").doesNotExist());
    }

    @Test
    void marquerPresence_sansCode_renvoie400RequeteInvalide() throws Exception {
        envoyer("""
                {"etudiantId": 1}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message").value("Le champ code est obligatoire."));
        verifyNoInteractions(presenceService);
    }

    @Test
    void marquerPresence_sansEtudiant_renvoie400RequeteInvalide() throws Exception {
        envoyer("""
                {"code": "K7P2QX"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message").value("Le champ etudiantId est obligatoire."));
        verifyNoInteractions(presenceService);
    }

    @Test
    void marquerPresence_codeInconnu_renvoie400CodeInconnu() throws Exception {
        when(presenceService.marquerPresence("K7P2QX", 1L)).thenThrow(new ErreurMetierException(
                CodeErreur.CODE_INCONNU, "Aucune session ne correspond au code saisi."));

        envoyer(CORPS_VALIDE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CODE_INCONNU"))
                .andExpect(jsonPath("$.message").value("Aucune session ne correspond au code saisi."));
    }

    @Test
    void marquerPresence_dejaPresent_renvoie409DejaPresent() throws Exception {
        when(presenceService.marquerPresence("K7P2QX", 1L)).thenThrow(new ErreurMetierException(
                CodeErreur.DEJA_PRESENT, "Votre présence à cette session est déjà enregistrée."));

        envoyer(CORPS_VALIDE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEJA_PRESENT"))
                .andExpect(jsonPath("$.message").value("Votre présence à cette session est déjà enregistrée."));
    }

    @Test
    void marquerPresence_codeExpire_renvoie410CodeExpire() throws Exception {
        when(presenceService.marquerPresence("K7P2QX", 1L)).thenThrow(new ErreurMetierException(
                CodeErreur.CODE_EXPIRE, "Ce code de présence a expiré."));

        envoyer(CORPS_VALIDE)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRE"))
                .andExpect(jsonPath("$.message").value("Ce code de présence a expiré."));
    }

    private ResultActions envoyer(String json) throws Exception {
        return mockMvc.perform(post("/api/presences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
