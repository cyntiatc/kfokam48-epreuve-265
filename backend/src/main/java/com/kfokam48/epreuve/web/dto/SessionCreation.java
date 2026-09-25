package com.kfokam48.epreuve.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Corps de {@code POST /api/sessions} (schéma {@code SessionCreation} du contrat). */
public record SessionCreation(

        @NotBlank(message = "Le champ titre est obligatoire.")
        @Size(max = 150, message = "Le titre ne doit pas dépasser 150 caractères.")
        String titre,

        @NotNull(message = "Le champ promotionId est obligatoire.")
        @Positive(message = "Le champ promotionId doit être un entier positif.")
        Long promotionId) {
}
