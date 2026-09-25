package com.kfokam48.epreuve.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Corps de {@code POST /api/relectures/{id}} (schéma {@code RelectureSoumission} du contrat).
 * La note est lue telle quelle pour que le service puisse refuser une note absente ou non entière
 * avec le code {@code NOTE_INVALIDE} (RG6), au lieu d'un arrondi silencieux.
 */
public record RelectureSoumission(

        BigDecimal note,

        @NotBlank(message = "Le commentaire est obligatoire.")
        @Size(max = 1000, message = "Le commentaire ne doit pas dépasser 1000 caractères.")
        String commentaire) {
}
