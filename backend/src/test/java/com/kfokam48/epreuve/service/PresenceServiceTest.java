package com.kfokam48.epreuve.service;

import com.kfokam48.epreuve.domain.Etudiant;
import com.kfokam48.epreuve.domain.Presence;
import com.kfokam48.epreuve.domain.Promotion;
import com.kfokam48.epreuve.domain.SessionCours;
import com.kfokam48.epreuve.domain.SourcePresence;
import com.kfokam48.epreuve.domain.StatutSession;
import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import com.kfokam48.epreuve.repository.EtudiantRepository;
import com.kfokam48.epreuve.repository.PresenceRepository;
import com.kfokam48.epreuve.repository.SessionCoursRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    /** Session ouverte à 08:00, code valable jusqu'à 08:15 (RG1). */
    private static final Instant OUVERTURE = Instant.parse("2026-09-28T08:00:00Z");

    @Mock
    private SessionCoursRepository sessionRepository;
    @Mock
    private EtudiantRepository etudiantRepository;
    @Mock
    private PresenceRepository presenceRepository;

    private Promotion promotion;
    private SessionCours session;
    private Etudiant etudiant;

    @BeforeEach
    void setUp() {
        promotion = avecId(new Promotion("L3 GL"), 1L);
        session = avecId(new SessionCours(promotion, "Spring Boot", "K7P2QX", OUVERTURE), 12L);
        etudiant = avecId(new Etudiant("L3GL-001", "Mbarga", "Alice", "alice.mbarga@example.com", promotion), 1L);
    }

    @Test
    void marquerPresence_enregistreLaPresenceAvecLaSourceCode() {
        preparerSessionEtEtudiant();
        when(presenceRepository.existsBySessionIdAndEtudiantId(12L, 1L)).thenReturn(false);
        when(presenceRepository.saveAndFlush(any(Presence.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Presence presence = serviceA("2026-09-28T08:05:00Z").marquerPresence("K7P2QX", 1L);

        assertThat(presence.getSession()).isSameAs(session);
        assertThat(presence.getEtudiant()).isSameAs(etudiant);
        assertThat(presence.getSource()).isEqualTo(SourcePresence.CODE);
        assertThat(presence.getMarqueeAt()).isEqualTo(Instant.parse("2026-09-28T08:05:00Z"));
    }

    @Test
    void marquerPresence_accepteUnCodeEnMinusculesEntoureDEspaces() {
        preparerSessionEtEtudiant();
        when(presenceRepository.existsBySessionIdAndEtudiantId(12L, 1L)).thenReturn(false);
        when(presenceRepository.saveAndFlush(any(Presence.class))).thenAnswer(invocation -> invocation.getArgument(0));

        serviceA("2026-09-28T08:05:00Z").marquerPresence("  k7p2qx ", 1L);

        verify(presenceRepository).saveAndFlush(any(Presence.class));
    }

    @Test
    void marquerPresence_accepteLeCodeALInstantExactDeLExpiration() {
        preparerSessionEtEtudiant();
        when(presenceRepository.existsBySessionIdAndEtudiantId(12L, 1L)).thenReturn(false);
        when(presenceRepository.saveAndFlush(any(Presence.class))).thenAnswer(invocation -> invocation.getArgument(0));

        serviceA("2026-09-28T08:15:00Z").marquerPresence("K7P2QX", 1L);

        verify(presenceRepository).saveAndFlush(any(Presence.class));
    }

    @Test
    void marquerPresence_codeInconnu_refuseAvecCodeInconnu() {
        when(sessionRepository.findByCode("ZZZZZZ")).thenReturn(Optional.empty());

        verifierErreur(() -> serviceA("2026-09-28T08:05:00Z").marquerPresence("ZZZZZZ", 1L),
                CodeErreur.CODE_INCONNU);
        verifyNoInteractions(presenceRepository);
    }

    @Test
    void marquerPresence_etudiantInconnu_refuseAvecRequeteInvalide() {
        when(sessionRepository.findByCode("K7P2QX")).thenReturn(Optional.of(session));
        when(etudiantRepository.findById(99L)).thenReturn(Optional.empty());

        verifierErreur(() -> serviceA("2026-09-28T08:05:00Z").marquerPresence("K7P2QX", 99L),
                CodeErreur.REQUETE_INVALIDE);
        verifyNoInteractions(presenceRepository);
    }

    @Test
    void marquerPresence_etudiantDUneAutrePromotion_refuseAvecRequeteInvalide() {
        Promotion autrePromotion = avecId(new Promotion("M1 Software Engineering"), 2L);
        Etudiant etudiantM1 = avecId(new Etudiant("M1SE-001", "Kamga", "Fabrice",
                "fabrice.kamga@example.com", autrePromotion), 6L);
        when(sessionRepository.findByCode("K7P2QX")).thenReturn(Optional.of(session));
        when(etudiantRepository.findById(6L)).thenReturn(Optional.of(etudiantM1));

        verifierErreur(() -> serviceA("2026-09-28T08:05:00Z").marquerPresence("K7P2QX", 6L),
                CodeErreur.REQUETE_INVALIDE);
        verifyNoInteractions(presenceRepository);
    }

    @Test
    void marquerPresence_dejaPresent_refuseAvecDejaPresent() {
        preparerSessionEtEtudiant();
        when(presenceRepository.existsBySessionIdAndEtudiantId(12L, 1L)).thenReturn(true);

        verifierErreur(() -> serviceA("2026-09-28T08:05:00Z").marquerPresence("K7P2QX", 1L),
                CodeErreur.DEJA_PRESENT);
        verify(presenceRepository, never()).saveAndFlush(any());
    }

    @Test
    void marquerPresence_dejaPresentAvecUnCodeExpire_signaleDAbordDejaPresent() {
        preparerSessionEtEtudiant();
        when(presenceRepository.existsBySessionIdAndEtudiantId(12L, 1L)).thenReturn(true);

        verifierErreur(() -> serviceA("2026-09-28T09:00:00Z").marquerPresence("K7P2QX", 1L),
                CodeErreur.DEJA_PRESENT);
    }

    @Test
    void marquerPresence_apresLExpiration_refuseAvecCodeExpire() {
        preparerSessionEtEtudiant();
        when(presenceRepository.existsBySessionIdAndEtudiantId(12L, 1L)).thenReturn(false);

        verifierErreur(() -> serviceA("2026-09-28T08:15:01Z").marquerPresence("K7P2QX", 1L),
                CodeErreur.CODE_EXPIRE);
        verify(presenceRepository, never()).saveAndFlush(any());
    }

    @Test
    void marquerPresence_sessionCloturee_refuseAvecCodeExpire() {
        ReflectionTestUtils.setField(session, "statut", StatutSession.CLOTUREE);
        ReflectionTestUtils.setField(session, "clotureAt", Instant.parse("2026-09-28T08:03:00Z"));
        preparerSessionEtEtudiant();
        when(presenceRepository.existsBySessionIdAndEtudiantId(12L, 1L)).thenReturn(false);

        verifierErreur(() -> serviceA("2026-09-28T08:05:00Z").marquerPresence("K7P2QX", 1L),
                CodeErreur.CODE_EXPIRE);
        verify(presenceRepository, never()).saveAndFlush(any());
    }

    @Test
    void marquerPresence_envoisSimultanes_laContrainteUniqueDonneDejaPresent() {
        preparerSessionEtEtudiant();
        when(presenceRepository.existsBySessionIdAndEtudiantId(12L, 1L)).thenReturn(false);
        when(presenceRepository.saveAndFlush(any(Presence.class)))
                .thenThrow(new DataIntegrityViolationException("uk_presences_session_etudiant"));

        verifierErreur(() -> serviceA("2026-09-28T08:05:00Z").marquerPresence("K7P2QX", 1L),
                CodeErreur.DEJA_PRESENT);
    }

    private void preparerSessionEtEtudiant() {
        when(sessionRepository.findByCode("K7P2QX")).thenReturn(Optional.of(session));
        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant));
    }

    private PresenceService serviceA(String instant) {
        Clock horloge = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new PresenceService(sessionRepository, etudiantRepository, presenceRepository, horloge);
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
