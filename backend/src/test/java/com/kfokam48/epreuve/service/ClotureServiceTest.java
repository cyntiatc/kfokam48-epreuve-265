package com.kfokam48.epreuve.service;

import com.kfokam48.epreuve.domain.Etudiant;
import com.kfokam48.epreuve.domain.Exercice;
import com.kfokam48.epreuve.domain.Promotion;
import com.kfokam48.epreuve.domain.Relecture;
import com.kfokam48.epreuve.domain.SessionCours;
import com.kfokam48.epreuve.domain.StatutExercice;
import com.kfokam48.epreuve.domain.StatutRelecture;
import com.kfokam48.epreuve.domain.StatutSession;
import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import com.kfokam48.epreuve.repository.ExerciceRepository;
import com.kfokam48.epreuve.repository.RelectureRepository;
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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClotureServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-28T11:00:00Z");
    private static final Instant ATTRIBUTION = Instant.parse("2026-09-28T09:00:00Z");

    @Mock
    private SessionCoursRepository sessionRepository;
    @Mock
    private ExerciceRepository exerciceRepository;
    @Mock
    private RelectureRepository relectureRepository;

    private ClotureService service;
    private SessionCours session;
    private Promotion promotion;

    @BeforeEach
    void setUp() {
        service = new ClotureService(sessionRepository, exerciceRepository, relectureRepository,
                Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        promotion = avecId(new Promotion("L3 GL"), 1L);
        session = avecId(new SessionCours(promotion, "Spring Boot", "K7P2QX",
                Instant.parse("2026-09-28T08:00:00Z")), 12L);
    }

    @Test
    void cloturerSession_fermeLaSessionEtFigeLesExercicesSansRelectureEnAttente() {
        Exercice relu = exercice(1L, StatutExercice.RELU);
        Exercice uneEnAttente = exercice(2L, StatutExercice.EN_RELECTURE);
        relectures(uneEnAttente, StatutRelecture.RENDUE, StatutRelecture.EN_ATTENTE);
        Exercice deuxEnAttente = exercice(3L, StatutExercice.EN_RELECTURE);
        relectures(deuxEnAttente, StatutRelecture.EN_ATTENTE, StatutRelecture.EN_ATTENTE);
        Exercice relecteurUniqueRendu = exercice(4L, StatutExercice.EN_RELECTURE);
        relectures(relecteurUniqueRendu, StatutRelecture.RENDUE);
        Exercice depose = exercice(5L, StatutExercice.DEPOSE);
        when(sessionRepository.findPourMiseAJour(12L)).thenReturn(Optional.of(session));
        when(exerciceRepository.findBySessionIdOrderByDeposeAtAscIdAsc(12L))
                .thenReturn(List.of(relu, uneEnAttente, deuxEnAttente, relecteurUniqueRendu, depose));

        BilanCloture bilan = service.cloturerSession(12L);

        assertThat(session.getStatut()).isEqualTo(StatutSession.CLOTUREE);
        assertThat(session.getClotureAt()).isEqualTo(MAINTENANT);
        // D4 : RELU -> DEFINITIF ; relecteur unique qui a rendu sa note -> DEFINITIF (H10) ; DEPOSE -> SANS_RELECTURE.
        assertThat(relu.getStatut()).isEqualTo(StatutExercice.DEFINITIF);
        assertThat(relecteurUniqueRendu.getStatut()).isEqualTo(StatutExercice.DEFINITIF);
        assertThat(depose.getStatut()).isEqualTo(StatutExercice.SANS_RELECTURE);
        // Une relecture encore en attente laisse l'exercice ouvert (H8).
        assertThat(uneEnAttente.getStatut()).isEqualTo(StatutExercice.EN_RELECTURE);
        assertThat(deuxEnAttente.getStatut()).isEqualTo(StatutExercice.EN_RELECTURE);
        assertThat(bilan.session()).isSameAs(session);
        assertThat(bilan.exercicesDefinitifs()).isEqualTo(2);
        assertThat(bilan.exercicesSansRelecture()).isEqualTo(1);
        assertThat(bilan.relecturesEnAttente()).isEqualTo(3);
    }

    @Test
    void cloturerSession_sansExercice_fermeSimplementLaSession() {
        when(sessionRepository.findPourMiseAJour(12L)).thenReturn(Optional.of(session));
        when(exerciceRepository.findBySessionIdOrderByDeposeAtAscIdAsc(12L)).thenReturn(List.of());

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
        verifyNoInteractions(exerciceRepository, relectureRepository);
    }

    @Test
    void cloturerSession_sessionInconnue_refuseAvecRequeteInvalide() {
        when(sessionRepository.findPourMiseAJour(99L)).thenReturn(Optional.empty());

        verifierErreur(() -> service.cloturerSession(99L), CodeErreur.REQUETE_INVALIDE);
        verifyNoInteractions(exerciceRepository, relectureRepository);
    }

    /** Exercice de la session amené au statut voulu par les transitions du diagramme D4. */
    private Exercice exercice(Long id, StatutExercice statut) {
        Exercice exercice = avecId(new Exercice(session, etudiant(id), "https://exemple.com/exercice-" + id,
                ATTRIBUTION), 50L + id);
        if (statut != StatutExercice.DEPOSE) {
            exercice.passerEnRelecture();
        }
        if (statut == StatutExercice.RELU) {
            exercice.marquerRelu();
        }
        return exercice;
    }

    /** Relectures de l'exercice, une par statut, confiées à des relecteurs distincts. */
    private void relectures(Exercice exercice, StatutRelecture... statuts) {
        List<Relecture> relectures = IntStream.range(0, statuts.length).mapToObj(rang -> {
            Relecture relecture = new Relecture(exercice, etudiant(10L + rang), ATTRIBUTION);
            if (statuts[rang] == StatutRelecture.RENDUE) {
                relecture.rendre(14, "Relecture rendue avant la clôture.", ATTRIBUTION);
            }
            return relecture;
        }).toList();
        when(relectureRepository.findByExerciceId(exercice.getId())).thenReturn(relectures);
    }

    private Etudiant etudiant(Long id) {
        return avecId(new Etudiant("L3GL-0" + id, "Nom" + id, "Prenom" + id, "etudiant" + id + "@example.com",
                promotion), id);
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
