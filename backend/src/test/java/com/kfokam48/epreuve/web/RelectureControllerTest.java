package com.kfokam48.epreuve.web;

import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import com.kfokam48.epreuve.service.RelectureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RelectureController.class)
class RelectureControllerTest {

    private static final String COMMENTAIRE = "Très bon travail.";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RelectureService relectureService;

    @Test
    void rendreRelecture_renvoie200SansCorps() throws Exception {
        envoyer("7", corps("15"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
        verify(relectureService).rendreRelecture(7L, new BigDecimal("15"), COMMENTAIRE);
    }

    @Test
    void rendreRelecture_sansCommentaire_renvoie400RequeteInvalide() throws Exception {
        envoyer("7", """
                {"note": 15}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message").value("Le commentaire est obligatoire."));
        verifyNoInteractions(relectureService);
    }

    @Test
    void rendreRelecture_identifiantNonNumerique_renvoie400RequeteInvalide() throws Exception {
        envoyer("abc", corps("15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message").value("Un paramètre de la requête n'a pas le bon format."));
        verifyNoInteractions(relectureService);
    }

    @Test
    void rendreRelecture_noteInvalide_renvoie400NoteInvalide() throws Exception {
        when(relectureService.rendreRelecture(7L, new BigDecimal("21"), COMMENTAIRE)).thenThrow(
                new ErreurMetierException(CodeErreur.NOTE_INVALIDE, "La note doit être un entier compris entre 0 et 20."));

        envoyer("7", corps("21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTE_INVALIDE"))
                .andExpect(jsonPath("$.message").value("La note doit être un entier compris entre 0 et 20."));
    }

    @Test
    void rendreRelecture_autoRelecture_renvoie403() throws Exception {
        when(relectureService.rendreRelecture(7L, new BigDecimal("15"), COMMENTAIRE)).thenThrow(
                new ErreurMetierException(CodeErreur.AUTO_RELECTURE, "Vous ne pouvez pas relire votre propre exercice."));

        envoyer("7", corps("15"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTO_RELECTURE"));
    }

    @Test
    void rendreRelecture_apresCloture_renvoie409RelectureDejaRendue() throws Exception {
        when(relectureService.rendreRelecture(7L, new BigDecimal("15"), COMMENTAIRE)).thenThrow(
                new ErreurMetierException(CodeErreur.RELECTURE_DEJA_RENDUE,
                        "La session est clôturée, cette relecture ne peut plus être modifiée."));

        envoyer("7", corps("15"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RELECTURE_DEJA_RENDUE"));
    }

    private static String corps(String note) {
        return """
                {"note": %s, "commentaire": "%s"}
                """.formatted(note, COMMENTAIRE);
    }

    private ResultActions envoyer(String id, String json) throws Exception {
        return mockMvc.perform(post("/api/relectures/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
