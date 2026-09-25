package com.kfokam48.epreuve.web.dto;

import com.kfokam48.epreuve.domain.Exercice;
import com.kfokam48.epreuve.domain.StatutExercice;

/** Réponse 201 de {@code POST /api/exercices} (schéma {@code ExerciceDepose} du contrat). */
public record ExerciceDepose(Long id, StatutExercice statut) {

    public static ExerciceDepose depuis(Exercice exercice) {
        return new ExerciceDepose(exercice.getId(), exercice.getStatut());
    }
}
