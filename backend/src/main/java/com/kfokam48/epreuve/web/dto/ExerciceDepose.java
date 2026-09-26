package com.kfokam48.epreuve.web.dto;

import com.kfokam48.epreuve.domain.StatutExercice;
import com.kfokam48.epreuve.service.DepotExercice;

/** Réponse 201 de {@code POST /api/exercices} (schéma {@code ExerciceDepose} du contrat). */
public record ExerciceDepose(Long id, StatutExercice statut, int relecteursAttribues) {

    public static ExerciceDepose depuis(DepotExercice depot) {
        return new ExerciceDepose(depot.exercice().getId(), depot.exercice().getStatut(), depot.relecteursAttribues());
    }
}
