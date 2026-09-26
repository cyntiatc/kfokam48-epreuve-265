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
import com.kfokam48.epreuve.repository.EtudiantRepository;
import com.kfokam48.epreuve.repository.ExerciceRepository;
import com.kfokam48.epreuve.repository.RelectureRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
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
class RelectureServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-28T10:00:00Z");
    private static final Instant PLUS_TOT = Instant.parse("2026-09-28T09:30:00Z");
    private static final String COMMENTAIRE = "Code clair et bien structuré.";

    @Mock
    private RelectureRepository relectureRepository;
    @Mock
    private ExerciceRepository exerciceRepository;
    @Mock
    private EtudiantRepository etudiantRepository;

    private RelectureService service;
    private SessionCours session;
    private Etudiant alice;
    private Etudiant brice;
    private Exercice exercice;
    private Relecture relecture;
    private Relecture relectureDeCarine;

    @BeforeEach
    void setUp() {
        service = new RelectureService(relectureRepository, exerciceRepository, etudiantRepository,
                Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        Promotion promotion = avecId(new Promotion("L3 GL"), 1L);
        session = avecId(new SessionCours(promotion, "Spring Boot", "K7P2QX",
                Instant.parse("2026-09-28T08:00:00Z")), 12L);
        alice = avecId(new Etudiant("L3GL-001", "Mbarga", "Alice", "alice.mbarga@example.com", promotion), 1L);
        brice = avecId(new Etudiant("L3GL-002", "Nkoulou", "Brice", "brice.nkoulou@example.com", promotion), 2L);
        Etudiant carine = avecId(new Etudiant("L3GL-003", "Fotso", "Carine", "carine.fotso@example.com", promotion), 3L);
        // Exercice d'Alice attribué à Brice (relecture 7, celle des tests) et à Carine (relecture 9) (EF4, RG8).
        exercice = avecId(new Exercice(session, alice, "https://exemple.com/alice",
                Instant.parse("2026-09-28T09:00:00Z")), 58L);
        exercice.passerEnRelecture();
        relecture = avecId(new Relecture(exercice, brice, Instant.parse("2026-09-28T09:00:00Z")), 7L);
        relectureDeCarine = avecId(new Relecture(exercice, carine, Instant.parse("2026-09-28T09:00:00Z")), 9L);
    }

    @Test
    void premiereSoumission_autreRelectureEnAttente_rendLaRelectureEtLaisseLExerciceEnRelecture() {
        trouverLaRelecture();
        relecturesDeLExercice(relecture, relectureDeCarine);

        service.rendreRelecture(7L, new BigDecimal("15"), "  " + COMMENTAIRE + "  ");

        assertThat(relecture.getStatut()).isEqualTo(StatutRelecture.RENDUE);
        assertThat(relecture.getNote()).isEqualTo(15);
        assertThat(relecture.getCommentaire()).isEqualTo(COMMENTAIRE);
        assertThat(relecture.getRendueAt()).isEqualTo(MAINTENANT);
        assertThat(relecture.getModifieeAt()).isNull();
        // RG6 : une seule note rendue sur deux, elle est provisoire et l'exercice reste en relecture.
        assertThat(exercice.getStatut()).isEqualTo(StatutExercice.EN_RELECTURE);
    }

    @Test
    void premiereSoumission_autreRelectureDejaRendue_marqueLExerciceRelu() {
        relectureDeCarine.rendre(12, COMMENTAIRE, PLUS_TOT);
        trouverLaRelecture();
        relecturesDeLExercice(relecture, relectureDeCarine);

        service.rendreRelecture(7L, new BigDecimal("15"), COMMENTAIRE);

        assertThat(exercice.getStatut()).isEqualTo(StatutExercice.RELU);
    }

    @Test
    void premiereSoumission_relecteurUniqueSessionOuverte_laisseLExerciceEnRelecture() {
        trouverLaRelecture();
        relecturesDeLExercice(relecture);

        service.rendreRelecture(7L, new BigDecimal("15"), COMMENTAIRE);

        // Un second relecteur peut encore être attribué à la prochaine présence (EF4).
        assertThat(exercice.getStatut()).isEqualTo(StatutExercice.EN_RELECTURE);
    }

    @Test
    void premiereSoumission_apresCloture_derniereRelectureEnAttente_rendLaNoteDefinitive() {
        relectureDeCarine.rendre(12, COMMENTAIRE, PLUS_TOT);
        cloturerLaSession();
        trouverLaRelecture();
        relecturesDeLExercice(relecture, relectureDeCarine);

        service.rendreRelecture(7L, new BigDecimal("14"), COMMENTAIRE);

        assertThat(relecture.getStatut()).isEqualTo(StatutRelecture.RENDUE);
        assertThat(relecture.getNote()).isEqualTo(14);
        assertThat(exercice.getStatut()).isEqualTo(StatutExercice.DEFINITIF);
    }

    @Test
    void premiereSoumission_apresCloture_autreRelectureEncoreEnAttente_laisseLExerciceEnRelecture() {
        cloturerLaSession();
        trouverLaRelecture();
        relecturesDeLExercice(relecture, relectureDeCarine);

        service.rendreRelecture(7L, new BigDecimal("14"), COMMENTAIRE);

        // H8 : Carine peut encore rendre la sienne une fois ; l'exercice deviendra alors DEFINITIF.
        assertThat(relecture.getStatut()).isEqualTo(StatutRelecture.RENDUE);
        assertThat(exercice.getStatut()).isEqualTo(StatutExercice.EN_RELECTURE);
    }

    @Test
    void nouvelleSoumission_sessionOuverte_modifieLaNote() {
        relecture.rendre(15, COMMENTAIRE, PLUS_TOT);
        relectureDeCarine.rendre(12, COMMENTAIRE, PLUS_TOT);
        exercice.marquerRelu();
        trouverLaRelecture();

        service.rendreRelecture(7L, new BigDecimal("17"), "Note corrigée.");

        assertThat(relecture.getNote()).isEqualTo(17);
        assertThat(relecture.getCommentaire()).isEqualTo("Note corrigée.");
        assertThat(relecture.getRendueAt()).isEqualTo(PLUS_TOT);
        assertThat(relecture.getModifieeAt()).isEqualTo(MAINTENANT);
        assertThat(exercice.getStatut()).isEqualTo(StatutExercice.RELU);
    }

    @Test
    void nouvelleSoumission_apresCloture_refuseAvecRelectureDejaRendue() {
        relecture.rendre(15, COMMENTAIRE, PLUS_TOT);
        relectureDeCarine.rendre(12, COMMENTAIRE, PLUS_TOT);
        exercice.marquerRelu();
        cloturerLaSession();
        trouverLaRelecture();

        verifierErreur(() -> service.rendreRelecture(7L, new BigDecimal("17"), "Note corrigée."),
                CodeErreur.RELECTURE_DEJA_RENDUE);
        assertThat(relecture.getNote()).isEqualTo(15);
        assertThat(relecture.getModifieeAt()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"-1", "21", "12.5", "20.01"})
    void noteAbsenteNonEntiereOuHorsBornes_refuseAvecNoteInvalide(String note) {
        BigDecimal valeur = note == null ? null : new BigDecimal(note);

        verifierErreur(() -> service.rendreRelecture(7L, valeur, COMMENTAIRE), CodeErreur.NOTE_INVALIDE);
        verifyNoInteractions(relectureRepository, exerciceRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "20", "15.0"})
    void noteEntiereDe0A20_estAcceptee(String note) {
        trouverLaRelecture();
        relecturesDeLExercice(relecture, relectureDeCarine);

        service.rendreRelecture(7L, new BigDecimal(note), COMMENTAIRE);

        assertThat(relecture.getNote()).isEqualTo(new BigDecimal(note).intValueExact());
    }

    @Test
    void relectureInconnue_refuseAvecRequeteInvalide() {
        when(relectureRepository.findPourMiseAJour(99L)).thenReturn(Optional.empty());

        verifierErreur(() -> service.rendreRelecture(99L, new BigDecimal("15"), COMMENTAIRE),
                CodeErreur.REQUETE_INVALIDE);
    }

    @Test
    void relecteurQuiEstLAuteur_refuseAvecAutoRelecture() {
        Relecture autoRelecture = avecId(new Relecture(exercice, alice, PLUS_TOT), 8L);
        when(relectureRepository.findPourMiseAJour(8L)).thenReturn(Optional.of(autoRelecture));
        when(exerciceRepository.findPourMiseAJour(58L)).thenReturn(Optional.of(exercice));

        verifierErreur(() -> service.rendreRelecture(8L, new BigDecimal("20"), COMMENTAIRE),
                CodeErreur.AUTO_RELECTURE);
        assertThat(autoRelecture.getStatut()).isEqualTo(StatutRelecture.EN_ATTENTE);
    }

    @Test
    void relecturesAttribuees_placeLesRelecturesEnAttenteAvantLesRendues() {
        Relecture rendueRecente = relectureDeBrice(20L, "2026-09-28T09:50:00Z");
        rendueRecente.rendre(14, COMMENTAIRE, MAINTENANT);
        Relecture enAttenteRecente = relectureDeBrice(21L, "2026-09-28T09:40:00Z");
        Relecture enAttenteAncienne = relectureDeBrice(22L, "2026-09-28T09:10:00Z");
        when(etudiantRepository.existsById(2L)).thenReturn(true);
        // Le dépôt renvoie les relectures de la plus récente à la plus ancienne.
        when(relectureRepository.findByRelecteurIdOrderByAttribueeAtDesc(2L))
                .thenReturn(List.of(rendueRecente, enAttenteRecente, enAttenteAncienne));

        assertThat(service.relecturesAttribuees(2L))
                .containsExactly(enAttenteRecente, enAttenteAncienne, rendueRecente);
    }

    @Test
    void relecturesAttribuees_relecteurInconnu_refuseAvecRequeteInvalide() {
        when(etudiantRepository.existsById(99L)).thenReturn(false);

        verifierErreur(() -> service.relecturesAttribuees(99L), CodeErreur.REQUETE_INVALIDE);
        verifyNoInteractions(relectureRepository);
    }

    private Relecture relectureDeBrice(Long id, String attribueeAt) {
        Exercice exerciceDAlice = avecId(new Exercice(session, alice, "https://exemple.com/alice-" + id,
                Instant.parse(attribueeAt)), 100L + id);
        return avecId(new Relecture(exerciceDAlice, brice, Instant.parse(attribueeAt)), id);
    }

    /** La relecture 7 et son exercice, verrouillés comme le fait le service. */
    private void trouverLaRelecture() {
        when(relectureRepository.findPourMiseAJour(7L)).thenReturn(Optional.of(relecture));
        when(exerciceRepository.findPourMiseAJour(58L)).thenReturn(Optional.of(exercice));
    }

    /** Relectures de l'exercice, relues par le service après une première soumission pour fixer son statut. */
    private void relecturesDeLExercice(Relecture... relectures) {
        when(relectureRepository.findByExerciceId(58L)).thenReturn(List.of(relectures));
    }

    private void cloturerLaSession() {
        ReflectionTestUtils.setField(session, "statut", StatutSession.CLOTUREE);
        ReflectionTestUtils.setField(session, "clotureAt", PLUS_TOT);
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
