package com.kfokam48.epreuve.service;

import com.kfokam48.epreuve.domain.Etudiant;
import com.kfokam48.epreuve.domain.Exercice;
import com.kfokam48.epreuve.domain.Promotion;
import com.kfokam48.epreuve.domain.SessionCours;
import com.kfokam48.epreuve.domain.StatutExercice;
import com.kfokam48.epreuve.domain.StatutSession;
import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import com.kfokam48.epreuve.repository.ExerciceRepository;
import com.kfokam48.epreuve.repository.SessionCoursRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClotureServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-28T11:00:00Z");

    @Mock
    private SessionCoursRepository sessionRepository;
    @Mock
    private ExerciceRepository exerciceRepository;

    private ClotureService service;
    private SessionCours session;
    private Promotion promotion;

    @BeforeEach
    void setUp() {
        service = new ClotureService(sessionRepository, exerciceRepository, Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        promotion = avecId(new Promotion("L3 GL"), 1L);
        session = avecId(new SessionCours(promotion, "Spring Boot", "K7P2QX",
                Instant.parse("2026-09-28T08:00:00Z")), 12L);
    }

    @Test
    void cloturerSession_fermeLaSessionEtFigeLesExercices() {
        Exercice relu = exercice(1L, StatutExercice.RELU);
        Exercice enRelecture = exercice(2L, StatutExercice.EN_RELECTURE);
        Exercice depose = exercice(3L, StatutExercice.DEPOSE);
        when(sessionRepository.findPourMiseAJour(12L)).thenReturn(Optional.of(session));
        when(exerciceRepository.findBySessionIdOrderByDeposeAtAsc(12L)).thenReturn(List.of(relu, enRelecture, depose));

        BilanCloture bilan = service.cloturerSession(12L);

        assertThat(session.getStatut()).isEqualTo(StatutSession.CLOTUREE);
        assertThat(session.getClotureAt()).isEqualTo(MAINTENANT);
        // D4 : RELU -> DEFINITIF, DEPOSE -> SANS_RELECTURE ; EN_RELECTURE reste ouvert (H8).
        assertThat(relu.getStatut()).isEqualTo(StatutExercice.DEFINITIF);
        assertThat(depose.getStatut()).isEqualTo(StatutExercice.SANS_RELECTURE);
        assertThat(enRelecture.getStatut()).isEqualTo(StatutExercice.EN_RELECTURE);
        assertThat(bilan.session()).isSameAs(session);
        assertThat(bilan.exercicesDefinitifs()).isEqualTo(1);
        assertThat(bilan.exercicesSansRelecture()).isEqualTo(1);
        assertThat(bilan.relecturesEnAttente()).isEqualTo(1);
    }

    @Test
    void cloturerSession_sansExercice_fermeSimplementLaSession() {
        when(sessionRepository.findPourMiseAJour(12L)).thenReturn(Optional.of(session));
        when(exerciceRepository.findBySessionIdOrderByDeposeAtAsc(12L)).thenReturn(List.of());

        BilanCloture bilan = service.cloturerSession(12L);

        assertThat(session.getStatut()).isEqualTo(StatutSession.CLOTUREE);
        assertThat(bilan.exercicesDefinitifs()).isZero();
        assertThat(bilan.exercicesSansRelecture()).isZero();
        assertThat(bilan.relecturesEnAttente()).isZero();
    }

    @Test
    void cloturerSession_dejaCloturee_refuseAvecSessionDejaCloturee() {
        session.cloturer(Instant.parse("2026-09-28T10:00:00Z"));
        when(sessionRepository.findPourMiseAJour(12L)).thenReturn(Optional.of(session));

        verifierErreur(() -> service.cloturerSession(12L), CodeErreur.SESSION_DEJA_CLOTUREE);
        assertThat(session.getClotureAt()).isEqualTo(Instant.parse("2026-09-28T10:00:00Z"));
        verifyNoInteractions(exerciceRepository);
    }

    @Test
    void cloturerSession_sessionInconnue_refuseAvecRequeteInvalide() {
        when(sessionRepository.findPourMiseAJour(99L)).thenReturn(Optional.empty());

        verifierErreur(() -> service.cloturerSession(99L), CodeErreur.REQUETE_INVALIDE);
        verifyNoInteractions(exerciceRepository);
    }

    /** Exercice de la session amené au statut voulu par les transitions du diagramme D4. */
    private Exercice exercice(Long id, StatutExercice statut) {
        Etudiant auteur = avecId(new Etudiant("L3GL-00" + id, "Nom" + id, "Prenom" + id,
                "etudiant" + id + "@example.com", promotion), id);
        Exercice exercice = avecId(new Exercice(session, auteur, "https://exemple.com/exercice-" + id,
                Instant.parse("2026-09-28T09:00:00Z")), 50L + id);
        if (statut != StatutExercice.DEPOSE) {
            exercice.passerEnRelecture();
        }
        if (statut == StatutExercice.RELU) {
            exercice.marquerRelu();
        }
        return exercice;
    }

    private static void verifierErreur(ThrowingCallable appel, CodeErreur codeAttendu) {
        assertThatThrownBy(appel).isInstanceOfSatisfying(ErreurMetierException.class,
                erreur -> assertThat(erreur.getCode()).isEqualTo(codeAttendu));
    }

    private static <T> T avecId(T entite, Long id) {
        ReflectionTestUtils.setField(entite, "id", id);
        return entite;
    }
}
