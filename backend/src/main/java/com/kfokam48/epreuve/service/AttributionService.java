package com.kfokam48.epreuve.service;

import com.kfokam48.epreuve.domain.Etudiant;
import com.kfokam48.epreuve.domain.Exercice;
import com.kfokam48.epreuve.domain.Presence;
import com.kfokam48.epreuve.domain.Relecture;
import com.kfokam48.epreuve.domain.SessionCours;
import com.kfokam48.epreuve.domain.StatutExercice;
import com.kfokam48.epreuve.repository.ChargeRelecteur;
import com.kfokam48.epreuve.repository.ExerciceRepository;
import com.kfokam48.epreuve.repository.PresenceRepository;
import com.kfokam48.epreuve.repository.RelectureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * EF4 : attribution aléatoire et équilibrée des relectures (RG2, RG7, RG8, section 7.2 du cahier des charges).
 * Chaque exercice reçoit deux relecteurs distincts (évolution de l'Étape 3). Traitement interne, sans route :
 * déclenché par le dépôt d'un exercice (EF3) et par chaque nouvelle présence (EF2).
 */
@Service
public class AttributionService {

    /** RG8 : nombre de relecteurs attendus par exercice. */
    public static final int RELECTEURS_PAR_EXERCICE = 2;

    private static final List<StatutExercice> STATUTS_A_COMPLETER =
            List.of(StatutExercice.DEPOSE, StatutExercice.EN_RELECTURE);

    private final PresenceRepository presenceRepository;
    private final ExerciceRepository exerciceRepository;
    private final RelectureRepository relectureRepository;
    private final RandomGenerator aleatoire;
    private final Clock horloge;

    public AttributionService(PresenceRepository presenceRepository, ExerciceRepository exerciceRepository,
                              RelectureRepository relectureRepository, RandomGenerator aleatoire, Clock horloge) {
        this.presenceRepository = presenceRepository;
        this.exerciceRepository = exerciceRepository;
        this.relectureRepository = relectureRepository;
        this.aleatoire = aleatoire;
        this.horloge = horloge;
    }

    /**
     * Après une nouvelle présence : complète les relecteurs de chaque exercice de la session qui en a moins de deux
     * (statut DEPOSE ou EN_RELECTURE), du plus ancien au plus récent.
     */
    @Transactional
    public void attribuerExercicesEnAttente(SessionCours session) {
        exerciceRepository.findBySessionIdAndStatutInOrderByDeposeAtAscIdAsc(session.getId(), STATUTS_A_COMPLETER)
                .forEach(this::attribuerRelecteurs);
    }

    /**
     * Attribue les relecteurs qui manquent à l'exercice, un par un. Chacun est tiré au hasard parmi les étudiants
     * présents à la session (RG7), autres que l'auteur (RG2) et que ses relecteurs déjà attribués (RG8), et, parmi eux,
     * ceux qui ont le moins de relectures dans la session (RG8). Faute de candidat, l'exercice attend la prochaine
     * présence éligible : il reste DEPOSE sans relecteur, EN_RELECTURE avec un seul.
     *
     * @return les relectures créées par cet appel (aucune, une ou deux)
     */
    @Transactional
    public List<Relecture> attribuerRelecteurs(Exercice exercice) {
        Long sessionId = exercice.getSession().getId();
        Long auteurId = exercice.getAuteur().getId();

        Set<Long> relecteursDejaAttribues = relectureRepository.findByExerciceId(exercice.getId()).stream()
                .map(relecture -> relecture.getRelecteur().getId())
                .collect(Collectors.toSet());
        int manquants = RELECTEURS_PAR_EXERCICE - relecteursDejaAttribues.size();
        if (manquants <= 0) {
            return List.of();
        }

        List<Etudiant> candidats = presenceRepository.findBySessionIdOrderByEtudiantIdAsc(sessionId).stream()
                .map(Presence::getEtudiant)
                .filter(etudiant -> !etudiant.getId().equals(auteurId))
                .filter(etudiant -> !relecteursDejaAttribues.contains(etudiant.getId()))
                .collect(Collectors.toCollection(ArrayList::new));
        if (candidats.isEmpty()) {
            return List.of();
        }

        // Charges mises à jour après chaque tirage : les deux relecteurs d'un exercice sont tirés l'un après l'autre.
        Map<Long, Long> charges = relectureRepository.compterRelecturesParRelecteur(sessionId).stream()
                .collect(Collectors.toMap(ChargeRelecteur::relecteurId, ChargeRelecteur::nombre, Long::sum, HashMap::new));
        List<Relecture> attribuees = new ArrayList<>();
        while (attribuees.size() < manquants && !candidats.isEmpty()) {
            Etudiant relecteur = tirerLeMoinsCharge(candidats, charges);
            candidats.remove(relecteur);
            charges.merge(relecteur.getId(), 1L, Long::sum);
            attribuees.add(relectureRepository.save(new Relecture(exercice, relecteur, Instant.now(horloge))));
        }
        exercice.passerEnRelecture();
        return attribuees;
    }

    /** RG8 : tirage au hasard parmi les candidats qui ont le moins de relectures dans la session. */
    private Etudiant tirerLeMoinsCharge(List<Etudiant> candidats, Map<Long, Long> charges) {
        long chargeMinimale = candidats.stream()
                .mapToLong(etudiant -> charges.getOrDefault(etudiant.getId(), 0L))
                .min()
                .orElseThrow();
        List<Etudiant> moinsCharges = candidats.stream()
                .filter(etudiant -> charges.getOrDefault(etudiant.getId(), 0L) == chargeMinimale)
                .toList();
        return moinsCharges.get(aleatoire.nextInt(moinsCharges.size()));
    }
}
