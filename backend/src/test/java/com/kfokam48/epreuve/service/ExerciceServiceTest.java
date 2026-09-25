package com.kfokam48.epreuve.service;

import com.kfokam48.epreuve.domain.Etudiant;
import com.kfokam48.epreuve.domain.Exercice;
import com.kfokam48.epreuve.domain.Promotion;
import com.kfokam48.epreuve.domain.SessionCours;
import com.kfokam48.epreuve.domain.StatutExercice;
import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import com.kfokam48.epreuve.repository.EtudiantRepository;
import com.kfokam48.epreuve.repository.ExerciceRepository;
import com.kfokam48.epreuve.repository.SessionCoursRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class ExerciceServiceTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-28T09:00:00Z"), ZoneOffset.UTC);
    private static final String LIEN = "https://github.com/exemple/exercice-session-12";

    @Mock
    private SessionCoursRepository sessionRepository;
    @Mock
    private EtudiantRepository etudiantRepository;
    @Mock
    private ExerciceRepository exerciceRepository;
    @Mock
    private AttributionService attributionService;

    private ExerciceService service;
    private Promotion promotion;
    private SessionCours session;
    private Etudiant etudiant;

    @BeforeEach
    void setUp() {
        service = new ExerciceService(sessionRepository, etudiantRepository, exerciceRepository,
                attributionService, HORLOGE);
        promotion = avecId(new Promotion("L3 GL"), 1L);
        session = avecId(new SessionCours(promotion, "Spring Boot", "K7P2QX",
                Instant.parse("2026-09-28T08:00:00Z")), 12L);
        etudiant = avecId(new Etudiant("L3GL-001", "Mbarga", "Alice", "alice.mbarga@example.com", promotion), 1L);
    }

    @Test
    void deposerExercice_enregistreLeLienAuStatutDepose() {
        preparerSessionEtEtudiant();
        when(exerciceRepository.existsBySessionIdAndAuteurId(12L, 1L)).thenReturn(false);
        when(exerciceRepository.saveAndFlush(any(Exercice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Exercice exercice = service.deposerExercice(12L, 1L, "  " + LIEN + "  ");

        assertThat(exercice.getSession()).isSameAs(session);
        assertThat(exercice.getAuteur()).isSameAs(etudiant);
        assertThat(exercice.getLien()).isEqualTo(LIEN);
        assertThat(exercice.getStatut()).isEqualTo(StatutExercice.DEPOSE);
        assertThat(exercice.getDeposeAt()).isEqualTo(Instant.parse("2026-09-28T09:00:00Z"));
        // EF4 : un relecteur est recherché dès le dépôt.
        verify(attributionService).attribuerRelecteur(exercice);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://exemple.com/exercice",
            "https://",
            "pas-une-url",
            "https://exem ple.com",
            "javascript:alert(1)",
            "github.com/exemple/exercice"
    })
    void deposerExercice_lienInvalide_refuseAvecLienInvalide(String lien) {
        verifierErreur(() -> service.deposerExercice(12L, 1L, lien), CodeErreur.LIEN_INVALIDE);
        verifyNoInteractions(sessionRepository, etudiantRepository, exerciceRepository);
    }

    @Test
    void deposerExercice_lienTropLong_refuseAvecLienInvalide() {
        String lienTropLong = "https://exemple.com/" + "a".repeat(ExerciceService.LONGUEUR_MAX_LIEN);

        verifierErreur(() -> service.deposerExercice(12L, 1L, lienTropLong), CodeErreur.LIEN_INVALIDE);
        verifyNoInteractions(exerciceRepository);
    }

    @Test
    void deposerExercice_sessionInconnue_refuseAvecRequeteInvalide() {
        when(sessionRepository.findById(99L)).thenReturn(Optional.empty());

        verifierErreur(() -> service.deposerExercice(99L, 1L, LIEN), CodeErreur.REQUETE_INVALIDE);
        verifyNoInteractions(exerciceRepository);
    }

    @Test
    void deposerExercice_etudiantInconnu_refuseAvecRequeteInvalide() {
        when(sessionRepository.findById(12L)).thenReturn(Optional.of(session));
        when(etudiantRepository.findById(99L)).thenReturn(Optional.empty());

        verifierErreur(() -> service.deposerExercice(12L, 99L, LIEN), CodeErreur.REQUETE_INVALIDE);
        verifyNoInteractions(exerciceRepository);
    }

    @Test
    void deposerExercice_etudiantDUneAutrePromotion_refuseAvecRequeteInvalide() {
        Promotion autrePromotion = avecId(new Promotion("M1 Software Engineering"), 2L);
        Etudiant etudiantM1 = avecId(new Etudiant("M1SE-001", "Kamga", "Fabrice",
                "fabrice.kamga@example.com", autrePromotion), 6L);
        when(sessionRepository.findById(12L)).thenReturn(Optional.of(session));
        when(etudiantRepository.findById(6L)).thenReturn(Optional.of(etudiantM1));

        verifierErreur(() -> service.deposerExercice(12L, 6L, LIEN), CodeErreur.REQUETE_INVALIDE);
        verifyNoInteractions(exerciceRepository);
    }

    @Test
    void deposerExercice_dejaDepose_refuseAvecExerciceDejaDepose() {
        preparerSessionEtEtudiant();
        when(exerciceRepository.existsBySessionIdAndAuteurId(12L, 1L)).thenReturn(true);

        verifierErreur(() -> service.deposerExercice(12L, 1L, LIEN), CodeErreur.EXERCICE_DEJA_DEPOSE);
        verify(exerciceRepository, never()).saveAndFlush(any());
        verifyNoInteractions(attributionService);
    }

    @Test
    void deposerExercice_depotsSimultanes_laContrainteUniqueDonneExerciceDejaDepose() {
        preparerSessionEtEtudiant();
        when(exerciceRepository.existsBySessionIdAndAuteurId(12L, 1L)).thenReturn(false);
        when(exerciceRepository.saveAndFlush(any(Exercice.class)))
                .thenThrow(new DataIntegrityViolationException("uk_exercices_session_etudiant"));

        verifierErreur(() -> service.deposerExercice(12L, 1L, LIEN), CodeErreur.EXERCICE_DEJA_DEPOSE);
    }

    private void preparerSessionEtEtudiant() {
        when(sessionRepository.findById(12L)).thenReturn(Optional.of(session));
        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant));
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
