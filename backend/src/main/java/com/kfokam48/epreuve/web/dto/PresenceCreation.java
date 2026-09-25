package com.kfokam48.epreuve.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Corps de {@code POST /api/presences} (schéma {@code PresenceCreation} du contrat). */
public record PresenceCreation(

        @NotBlank(message = "Le champ code est obligatoire.")
        String code,

        @NotNull(message = "Le champ etudiantId est obligatoire.")
        @Positive(message = "Le champ etudiantId doit être un entier positif.")
        Long etudiantId) {
}
