package com.kfokam48.epreuve.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Corps de {@code POST /api/exercices} (schéma {@code ExerciceDepot} du contrat).
 * Le format du lien (RG5) est contrôlé par le service, qui renvoie alors {@code LIEN_INVALIDE}.
 */
public record ExerciceDepot(

        @NotNull(message = "Le champ sessionId est obligatoire.")
        @Positive(message = "Le champ sessionId doit être un entier positif.")
        Long sessionId,

        @NotNull(message = "Le champ etudiantId est obligatoire.")
        @Positive(message = "Le champ etudiantId doit être un entier positif.")
        Long etudiantId,

        @NotBlank(message = "Le champ lien est obligatoire.")
        String lien) {
}
