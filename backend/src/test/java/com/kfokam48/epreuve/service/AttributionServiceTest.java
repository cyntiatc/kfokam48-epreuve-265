package com.kfokam48.epreuve.service;

import com.kfokam48.epreuve.domain.Etudiant;
import com.kfokam48.epreuve.domain.Exercice;
import com.kfokam48.epreuve.domain.Presence;
import com.kfokam48.epreuve.domain.Promotion;
import com.kfokam48.epreuve.domain.Relecture;
import com.kfokam48.epreuve.domain.SessionCours;
import com.kfokam48.epreuve.domain.StatutExercice;
import com.kfokam48.epreuve.domain.StatutRelecture;
import com.kfokam48.epreuve.repository.ChargeRelecteur;
import com.kfokam48.epreuve.repository.ExerciceRepository;
import com.kfokam48.epreuve.repository.PresenceRepository;
import com.kfokam48.epreuve.repository.RelectureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttributionServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-28T09:00:00Z");

    @Mock
    private PresenceRepository presenceRepository;
    @Mock
    private ExerciceRepository exerciceRepository;
    @Mock
    private RelectureRepository relectureRepository;
    @Mock
    private RandomGenerator aleatoire;

    private AttributionService service;
    private SessionCours session;
    private Etudiant alice;
    private Etudiant brice;
    private Etudiant carine;
    private Etudiant david;
    private Exercice exerciceDAlice;

    @BeforeEach
    void setUp() {
        service = new AttributionService(presenceRepository, exerciceRepository, relectureRepository, aleatoire,
                Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        Promotion promotion = avecId(new Promotion("L3 GL"), 1L);
        session = avecId(new SessionCours(promotion, "Spring Boot", "K7P2QX",
                Instant.parse("2026-09-28T08:00:00Z")), 12L);
        alice = etudiant(1L, "Mbarga", "Alice", promotion);
        brice = etudiant(2L, "Nkoulou", "Brice", promotion);
        carine = etudiant(3L, "Fotso", "Carine", promotion);
        david = etudiant(4L, "Ngono", "David", promotion);
        exerciceDAlice = avecId(new Exercice(session, alice, "https://exemple.com/alice", MAINTENANT), 58L);
    }

    @Test
    void attribuerRelecteurs_sansAucunPresent_laisseLExerciceDepose() {
        presents();

        assertThat(service.attribuerRelecteurs(exerciceDAlice)).isEmpty();
        assertThat(exerciceDAlice.getStatut()).isEqualTo(StatutExercice.DEPOSE);
        verify(relectureRepository, never()).save(any());
        verifyNoInteractions(aleatoire);
    }

    @Test
    void attribuerRelecteurs_seulLAuteurEstPresent_neLuiAttribueJamaisSonExercice() {
        presents(alice);

        assertThat(service.attribuerRelecteurs(exerciceDAlice)).isEmpty();
        assertThat(exerciceDAlice.getStatut()).isEqualTo(StatutExercice.DEPOSE);
        verify(relectureRepository, never()).save(any());
    }

    @Test
    void attribuerRelecteurs_tireDeuxRelecteursDistinctsParmiLesMoinsCharges() {
        presents(alice, brice, carine, david);
        charges(new ChargeRelecteur(2L, 1L));  // Brice a déjà une relecture : Carine et David sont à égalité
        when(aleatoire.nextInt(2)).thenReturn(1);  // premier tirage : le second des deux, David
        enregistrerLesRelectures();

        List<Relecture> relectures = service.attribuerRelecteurs(exerciceDAlice);

        // Second tirage : David vient de passer à une relecture, comme Brice ; Carine est la seule moins chargée.
        assertThat(relectures).extracting(Relecture::getRelecteur).containsExactly(david, carine);
        assertThat(relectures).allSatisfy(relecture -> {
            assertThat(relecture.getExercice()).isSameAs(exerciceDAlice);
            assertThat(relecture.getStatut()).isEqualTo(StatutRelecture.EN_ATTENTE);
            assertThat(relecture.getAttribueeAt()).isEqualTo(MAINTENANT);
        });
        assertThat(exerciceDAlice.getStatut()).isEqualTo(StatutExercice.EN_RELECTURE);
    }

    @Test
    void attribuerRelecteurs_unSeulCandidat_attribueUnRelecteurEtAttendLeSecond() {
        presents(alice, brice);
        enregistrerLesRelectures();

        List<Relecture> relectures = service.attribuerRelecteurs(exerciceDAlice);

        assertThat(relectures).extracting(Relecture::getRelecteur).containsExactly(brice);
        assertThat(exerciceDAlice.getStatut()).isEqualTo(StatutExercice.EN_RELECTURE);
    }

    @Test
    void attribuerRelecteurs_exerciceAyantDejaUnRelecteur_attribueLeSecondSansRepeterLePremier() {
        exerciceDAlice.passerEnRelecture();
        relecteursDejaAttribues(brice);
        presents(alice, brice, carine);
        charges(new ChargeRelecteur(3L, 3L));  // Carine est plus chargée que Brice, mais Brice relit déjà cet exercice
        enregistrerLesRelectures();

        List<Relecture> relectures = service.attribuerRelecteurs(exerciceDAlice);

        assertThat(relectures).extracting(Relecture::getRelecteur).containsExactly(carine);
        assertThat(exerciceDAlice.getStatut()).isEqualTo(StatutExercice.EN_RELECTURE);
    }

    @Test
    void attribuerRelecteurs_exerciceAyantDejaDeuxRelecteurs_nAttribueRien() {
        exerciceDAlice.passerEnRelecture();
        relecteursDejaAttribues(brice, carine);

        assertThat(service.attribuerRelecteurs(exerciceDAlice)).isEmpty();
        verify(relectureRepository, never()).save(any());
        verifyNoInteractions(presenceRepository, aleatoire);
    }

    @Test
    void attribuerRelecteurs_ecarteLAuteurMemeSIlEstLeMoinsCharge() {
        presents(alice, brice, carine);
        charges(new ChargeRelecteur(2L, 3L), new ChargeRelecteur(3L, 3L));  // Alice (l'auteure) n'a aucune relecture
        enregistrerLesRelectures();

        List<Relecture> relectures = service.attribuerRelecteurs(exerciceDAlice);

        assertThat(relectures).extracting(Relecture::getRelecteur).containsExactlyInAnyOrder(brice, carine);
    }

    @Test
    void attribuerExercicesEnAttente_completeLesExercicesDeposesEtEnRelecture() {
        // Exercice d'Alice : aucun relecteur. Exercice de Brice : déjà relu par Carine, il lui manque un relecteur.
        relecteursDejaAttribues();
        Exercice exerciceDeBrice = avecId(new Exercice(session, brice, "https://exemple.com/brice", MAINTENANT), 59L);
        exerciceDeBrice.passerEnRelecture();
        when(relectureRepository.findByExerciceId(59L))
                .thenReturn(List.of(new Relecture(exerciceDeBrice, carine, MAINTENANT)));
        when(exerciceRepository.findBySessionIdAndStatutInOrderByDeposeAtAscIdAsc(
                12L, List.of(StatutExercice.DEPOSE, StatutExercice.EN_RELECTURE)))
                .thenReturn(List.of(exerciceDAlice, exerciceDeBrice));
        presents(alice, brice, carine);
        charges();
        enregistrerLesRelectures();

        service.attribuerExercicesEnAttente(session);

        ArgumentCaptor<Relecture> relectures = ArgumentCaptor.forClass(Relecture.class);
        verify(relectureRepository, times(3)).save(relectures.capture());
        // Exercice d'Alice : Brice puis Carine. Exercice de Brice : Alice, seule présente qui ne le relit pas déjà.
        assertThat(relectures.getAllValues()).extracting(Relecture::getRelecteur).containsExactly(brice, carine, alice);
        assertThat(exerciceDAlice.getStatut()).isEqualTo(StatutExercice.EN_RELECTURE);
        assertThat(exerciceDeBrice.getStatut()).isEqualTo(StatutExercice.EN_RELECTURE);
    }

    private void presents(Etudiant... etudiants) {
        when(presenceRepository.findBySessionIdOrderByEtudiantIdAsc(12L)).thenReturn(Arrays.stream(etudiants)
                .map(etudiant -> new Presence(session, etudiant, MAINTENANT))
                .toList());
    }

    private void relecteursDejaAttribues(Etudiant... relecteurs) {
        when(relectureRepository.findByExerciceId(58L)).thenReturn(Arrays.stream(relecteurs)
                .map(relecteur -> new Relecture(exerciceDAlice, relecteur, MAINTENANT))
                .toList());
    }

    private void charges(ChargeRelecteur... charges) {
        when(relectureRepository.compterRelecturesParRelecteur(12L)).thenReturn(List.of(charges));
    }

    private void enregistrerLesRelectures() {
        when(relectureRepository.save(any(Relecture.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Etudiant etudiant(Long id, String nom, String prenom, Promotion promotion) {
        return avecId(new Etudiant("L3GL-00" + id, nom, prenom, prenom.toLowerCase() + "@example.com", promotion), id);
    }

    private static <T> T avecId(T entite, Long id) {
        ReflectionTestUtils.setField(entite, "id", id);
        return entite;
    }
}
