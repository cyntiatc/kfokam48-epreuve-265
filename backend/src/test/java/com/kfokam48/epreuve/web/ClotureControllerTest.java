package com.kfokam48.epreuve.web;

import com.kfokam48.epreuve.domain.Promotion;
import com.kfokam48.epreuve.domain.SessionCours;
import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import com.kfokam48.epreuve.service.BilanCloture;
import com.kfokam48.epreuve.service.ClotureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClotureController.class)
class ClotureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClotureService clotureService;

    @Test
    void cloturerSession_renvoie200AvecLeBilan() throws Exception {
        SessionCours session = new SessionCours(new Promotion("L3 GL"), "Spring Boot", "K7P2QX",
                Instant.parse("2026-09-28T08:00:00Z"));
        ReflectionTestUtils.setField(session, "id", 12L);
        session.cloturer(Instant.parse("2026-09-28T11:00:00Z"));
        when(clotureService.cloturerSession(12L)).thenReturn(new BilanCloture(session, 3, 1, 2));

        mockMvc.perform(post("/api/sessions/12/cloture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.statut").value("CLOTUREE"))
                .andExpect(jsonPath("$.clotureAt").value("2026-09-28T11:00:00Z"))
                .andExpect(jsonPath("$.exercicesDefinitifs").value(3))
                .andExpect(jsonPath("$.exercicesSansRelecture").value(1))
                .andExpect(jsonPath("$.relecturesEnAttente").value(2));
    }

    @Test
    void cloturerSession_dejaCloturee_renvoie409() throws Exception {
        when(clotureService.cloturerSession(12L)).thenThrow(new ErreurMetierException(
                CodeErreur.SESSION_DEJA_CLOTUREE, "Cette session est déjà clôturée."));

        mockMvc.perform(post("/api/sessions/12/cloture"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_DEJA_CLOTUREE"))
                .andExpect(jsonPath("$.message").value("Cette session est déjà clôturée."));
    }

    @Test
    void cloturerSession_sessionInconnue_renvoie400RequeteInvalide() throws Exception {
        when(clotureService.cloturerSession(99L)).thenThrow(new ErreurMetierException(
                CodeErreur.REQUETE_INVALIDE, "Aucune session ne correspond à l'identifiant 99."));

        mockMvc.perform(post("/api/sessions/99/cloture"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
    }

    @Test
    void cloturerSession_identifiantNonNumerique_renvoie400RequeteInvalide() throws Exception {
        mockMvc.perform(post("/api/sessions/abc/cloture"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
        verifyNoInteractions(clotureService);
    }
}
