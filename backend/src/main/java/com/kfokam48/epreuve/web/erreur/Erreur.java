package com.kfokam48.epreuve.web.erreur;

/** Format d'erreur unique de l'API (schéma {@code Erreur} du contrat, RG12). */
public record Erreur(String code, String message) {
}
