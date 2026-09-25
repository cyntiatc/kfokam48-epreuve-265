package com.kfokam48.epreuve.web;

import com.kfokam48.epreuve.domain.Etudiant;
import com.kfokam48.epreuve.domain.Exercice;
import com.kfokam48.epreuve.domain.Promotion;
import com.kfokam48.epreuve.domain.Relecture;
import com.kfokam48.epreuve.domain.SessionCours;
import com.kfokam48.epreuve.domain.StatutSession;
import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import com.kfokam48.epreuve.service.RelectureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void relecturesAttribuees_renvoie200AvecLeLienDeLExercice() throws Exception {
        Relecture enAttente = relectureAttribuee(StatutSession.OUVERTE);
        when(relectureService.relecturesAttribuees(2L)).thenReturn(List.of(enAttente));

        mockMvc.perform(get("/api/relectures").param("relecteurId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].sessionId").value(12))
                .andExpect(jsonPath("$[0].sessionTitre").value("Spring Boot"))
                .andExpect(jsonPath("$[0].lien").value("https://github.com/exemple/exercice-alice"))
                .andExpect(jsonPath("$[0].statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$[0].note").value(nullValue()))
                .andExpect(jsonPath("$[0].commentaire").value(nullValue()))
                .andExpect(jsonPath("$[0].modifiable").value(true))
                // L'auteur de l'exercice n'est pas exposé au relecteur.
                .andExpect(jsonPath("$[0].auteur").doesNotExist());
    }

    @Test
    void relecturesAttribuees_relectureRendueDansUneSessionCloturee_nEstPlusModifiable() throws Exception {
        Relecture rendue = relectureAttribuee(StatutSession.CLOTUREE);
        rendue.rendre(15, "Bien structuré.", Instant.parse("2026-09-28T09:30:00Z"));
        when(relectureService.relecturesAttribuees(2L)).thenReturn(List.of(rendue));

        mockMvc.perform(get("/api/relectures").param("relecteurId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].statut").value("RENDUE"))
                .andExpect(jsonPath("$[0].note").value(15))
                .andExpect(jsonPath("$[0].commentaire").value("Bien structuré."))
                .andExpect(jsonPath("$[0].modifiable").value(false));
    }

    @Test
    void relecturesAttribuees_sansRelecteurId_renvoie400RequeteInvalide() throws Exception {
        mockMvc.perform(get("/api/relectures"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message").value("Le paramètre relecteurId est obligatoire."));
        verifyNoInteractions(relectureService);
    }

    @Test
    void relecturesAttribuees_relecteurInconnu_renvoie400RequeteInvalide() throws Exception {
        when(relectureService.relecturesAttribuees(99L)).thenThrow(new ErreurMetierException(
                CodeErreur.REQUETE_INVALIDE, "Aucun étudiant ne correspond à l'identifiant 99."));

        mockMvc.perform(get("/api/relectures").param("relecteurId", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message").value("Aucun étudiant ne correspond à l'identifiant 99."));
    }

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

    /** Exercice d'Alice attribué à Brice (relecture n° 7, session n° 12), dans une session au statut donné. */
    private static Relecture relectureAttribuee(StatutSession statutSession) {
        Promotion promotion = new Promotion("L3 GL");
        SessionCours session = new SessionCours(promotion, "Spring Boot", "K7P2QX",
                Instant.parse("2026-09-28T08:00:00Z"));
        ReflectionTestUtils.setField(session, "id", 12L);
        ReflectionTestUtils.setField(session, "statut", statutSession);
        if (statutSession == StatutSession.CLOTUREE) {
            ReflectionTestUtils.setField(session, "clotureAt", Instant.parse("2026-09-28T10:00:00Z"));
        }
        Etudiant alice = new Etudiant("L3GL-001", "Mbarga", "Alice", "alice.mbarga@example.com", promotion);
        Etudiant brice = new Etudiant("L3GL-002", "Nkoulou", "Brice", "brice.nkoulou@example.com", promotion);
        Exercice exercice = new Exercice(session, alice, "https://github.com/exemple/exercice-alice",
                Instant.parse("2026-09-28T09:00:00Z"));
        Relecture relecture = new Relecture(exercice, brice, Instant.parse("2026-09-28T09:00:00Z"));
        ReflectionTestUtils.setField(relecture, "id", 7L);
        return relecture;
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
