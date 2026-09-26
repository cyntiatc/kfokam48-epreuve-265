package com.kfokam48.epreuve.web;

import com.kfokam48.epreuve.domain.Etudiant;
import com.kfokam48.epreuve.domain.Exercice;
import com.kfokam48.epreuve.domain.Promotion;
import com.kfokam48.epreuve.domain.SessionCours;
import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import com.kfokam48.epreuve.service.DepotExercice;
import com.kfokam48.epreuve.service.ExerciceService;
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

@WebMvcTest(ExerciceController.class)
class ExerciceControllerTest {

    private static final String LIEN = "https://github.com/exemple/exercice-session-12";
    private static final String CORPS_VALIDE = """
            {"sessionId": 12, "etudiantId": 1, "lien": "%s"}
            """.formatted(LIEN);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExerciceService exerciceService;

    @Test
    void deposerExercice_renvoie201AvecLesChampsDuContrat() throws Exception {
        Promotion promotion = new Promotion("L3 GL");
        SessionCours session = new SessionCours(promotion, "Spring Boot", "K7P2QX",
                Instant.parse("2026-09-28T08:00:00Z"));
        Etudiant etudiant = new Etudiant("L3GL-001", "Mbarga", "Alice", "alice.mbarga@example.com", promotion);
        Exercice exercice = new Exercice(session, etudiant, LIEN, Instant.parse("2026-09-28T09:00:00Z"));
        ReflectionTestUtils.setField(exercice, "id", 58L);
        exercice.passerEnRelecture();
        when(exerciceService.deposerExercice(12L, 1L, LIEN)).thenReturn(new DepotExercice(exercice, 2));

        envoyer(CORPS_VALIDE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(58))
                .andExpect(jsonPath("$.statut").value("EN_RELECTURE"))
                .andExpect(jsonPath("$.relecteursAttribues").value(2))
                .andExpect(jsonPath("$.lien").doesNotExist());
    }

    @Test
    void deposerExercice_sansSession_renvoie400RequeteInvalide() throws Exception {
        envoyer("""
                {"etudiantId": 1, "lien": "%s"}
                """.formatted(LIEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message").value("Le champ sessionId est obligatoire."));
        verifyNoInteractions(exerciceService);
    }

    @Test
    void deposerExercice_sansLien_renvoie400RequeteInvalide() throws Exception {
        envoyer("""
                {"sessionId": 12, "etudiantId": 1}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message").value("Le champ lien est obligatoire."));
        verifyNoInteractions(exerciceService);
    }

    @Test
    void deposerExercice_lienInvalide_renvoie400LienInvalide() throws Exception {
        when(exerciceService.deposerExercice(12L, 1L, "ftp://exemple.com")).thenThrow(new ErreurMetierException(
                CodeErreur.LIEN_INVALIDE, "Le lien doit être une URL http ou https valide."));

        envoyer("""
                {"sessionId": 12, "etudiantId": 1, "lien": "ftp://exemple.com"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LIEN_INVALIDE"))
                .andExpect(jsonPath("$.message").value("Le lien doit être une URL http ou https valide."));
    }

    @Test
    void deposerExercice_dejaDepose_renvoie409ExerciceDejaDepose() throws Exception {
        when(exerciceService.deposerExercice(12L, 1L, LIEN)).thenThrow(new ErreurMetierException(
                CodeErreur.EXERCICE_DEJA_DEPOSE, "Vous avez déjà déposé un exercice pour cette session."));

        envoyer(CORPS_VALIDE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXERCICE_DEJA_DEPOSE"))
                .andExpect(jsonPath("$.message").value("Vous avez déjà déposé un exercice pour cette session."));
    }

    private ResultActions envoyer(String json) throws Exception {
        return mockMvc.perform(post("/api/exercices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
