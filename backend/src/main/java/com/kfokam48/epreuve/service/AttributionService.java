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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * EF4 : attribution aléatoire et équilibrée des relectures (RG2, RG7, RG8, section 7.2 du cahier des charges).
 * Traitement interne, sans route : déclenché par le dépôt d'un exercice (EF3) et par chaque nouvelle présence (EF2).
 */
@Service
public class AttributionService {

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
     * Après une nouvelle présence : tente d'attribuer un relecteur à chaque exercice de la session
     * encore au statut DEPOSE, du plus ancien au plus récent.
     */
    @Transactional
    public void attribuerExercicesEnAttente(SessionCours session) {
        exerciceRepository.findBySessionIdAndStatutOrderByDeposeAtAsc(session.getId(), StatutExercice.DEPOSE)
                .forEach(this::attribuerRelecteur);
    }

    /**
     * Tire au hasard le relecteur parmi les étudiants présents à la session (RG7), autres que l'auteur (RG2),
     * et, parmi eux, ceux qui ont le moins de relectures dans la session (RG8). Sans candidat, l'exercice
     * reste DEPOSE : il sera attribué à la prochaine présence éligible.
     */
    @Transactional
    public Optional<Relecture> attribuerRelecteur(Exercice exercice) {
        Long sessionId = exercice.getSession().getId();
        Long auteurId = exercice.getAuteur().getId();

        List<Etudiant> candidats = presenceRepository.findBySessionIdOrderByEtudiantIdAsc(sessionId).stream()
                .map(Presence::getEtudiant)
                .filter(etudiant -> !etudiant.getId().equals(auteurId))
                .toList();
        if (candidats.isEmpty()) {
            return Optional.empty();
        }

        Map<Long, Long> charges = relectureRepository.compterRelecturesParRelecteur(sessionId).stream()
                .collect(Collectors.toMap(ChargeRelecteur::relecteurId, ChargeRelecteur::nombre));
        long chargeMinimale = candidats.stream()
                .mapToLong(etudiant -> charges.getOrDefault(etudiant.getId(), 0L))
                .min()
                .orElseThrow();
        List<Etudiant> moinsCharges = candidats.stream()
                .filter(etudiant -> charges.getOrDefault(etudiant.getId(), 0L) == chargeMinimale)
                .toList();

        Etudiant relecteur = moinsCharges.get(aleatoire.nextInt(moinsCharges.size()));
        exercice.passerEnRelecture();
        return Optional.of(relectureRepository.save(new Relecture(exercice, relecteur, Instant.now(horloge))));
    }
}
