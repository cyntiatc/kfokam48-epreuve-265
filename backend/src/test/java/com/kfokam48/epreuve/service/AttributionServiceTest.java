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
    void attribuerRelecteur_sansAucunPresent_laisseLExerciceDepose() {
        presents();

        assertThat(service.attribuerRelecteur(exerciceDAlice)).isEmpty();
        assertThat(exerciceDAlice.getStatut()).isEqualTo(StatutExercice.DEPOSE);
        verify(relectureRepository, never()).save(any());
        verifyNoInteractions(aleatoire);
    }

    @Test
    void attribuerRelecteur_seulLAuteurEstPresent_neLuiAttribueJamaisSonExercice() {
        presents(alice);

        assertThat(service.attribuerRelecteur(exerciceDAlice)).isEmpty();
        assertThat(exerciceDAlice.getStatut()).isEqualTo(StatutExercice.DEPOSE);
        verify(relectureRepository, never()).save(any());
    }

    @Test
    void attribuerRelecteur_tireAuHasardParmiLesPresentsLesMoinsCharges() {
        presents(alice, brice, carine, david);
        charges(new ChargeRelecteur(2L, 1L));  // Brice a déjà une relecture : Carine et David sont à égalité
        when(aleatoire.nextInt(2)).thenReturn(1);  // le tirage désigne le second des deux
        enregistrerLesRelectures();

        Relecture relecture = service.attribuerRelecteur(exerciceDAlice).orElseThrow();

        assertThat(relecture.getRelecteur()).isSameAs(david);
        assertThat(relecture.getExercice()).isSameAs(exerciceDAlice);
        assertThat(relecture.getStatut()).isEqualTo(StatutRelecture.EN_ATTENTE);
        assertThat(relecture.getAttribueeAt()).isEqualTo(MAINTENANT);
        assertThat(exerciceDAlice.getStatut()).isEqualTo(StatutExercice.EN_RELECTURE);
    }

    @Test
    void attribuerRelecteur_ecarteLAuteurMemeSIlEstLeMoinsCharge() {
        presents(alice, brice);
        charges(new ChargeRelecteur(2L, 3L));  // Brice a 3 relectures, Alice (l'auteure) aucune
        enregistrerLesRelectures();

        Relecture relecture = service.attribuerRelecteur(exerciceDAlice).orElseThrow();

        assertThat(relecture.getRelecteur()).isSameAs(brice);
    }

    @Test
    void attribuerExercicesEnAttente_repartitLaChargeEntreLesExercices() {
        Exercice exerciceDeBrice = avecId(new Exercice(session, brice, "https://exemple.com/brice", MAINTENANT), 59L);
        when(exerciceRepository.findBySessionIdAndStatutOrderByDeposeAtAsc(12L, StatutExercice.DEPOSE))
                .thenReturn(List.of(exerciceDAlice, exerciceDeBrice));
        presents(alice, brice, carine);
        // Charges relues avant chaque tirage : aucune, puis Carine à 1 après le premier.
        when(relectureRepository.compterRelecturesParRelecteur(12L))
                .thenReturn(List.of(), List.of(new ChargeRelecteur(3L, 1L)));
        when(aleatoire.nextInt(2)).thenReturn(1);
        enregistrerLesRelectures();

        service.attribuerExercicesEnAttente(session);

        ArgumentCaptor<Relecture> relectures = ArgumentCaptor.forClass(Relecture.class);
        verify(relectureRepository, times(2)).save(relectures.capture());
        // Exercice d'Alice : Brice et Carine à égalité, le tirage désigne Carine.
        // Exercice de Brice : Carine a déjà une relecture, Alice est la seule moins chargée.
        assertThat(relectures.getAllValues()).extracting(Relecture::getRelecteur).containsExactly(carine, alice);
        assertThat(exerciceDAlice.getStatut()).isEqualTo(StatutExercice.EN_RELECTURE);
        assertThat(exerciceDeBrice.getStatut()).isEqualTo(StatutExercice.EN_RELECTURE);
    }

    private void presents(Etudiant... etudiants) {
        when(presenceRepository.findBySessionIdOrderByEtudiantIdAsc(12L)).thenReturn(Arrays.stream(etudiants)
                .map(etudiant -> new Presence(session, etudiant, MAINTENANT))
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
