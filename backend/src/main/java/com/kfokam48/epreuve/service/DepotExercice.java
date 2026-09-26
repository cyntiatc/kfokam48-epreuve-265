package com.kfokam48.epreuve.service;

import com.kfokam48.epreuve.domain.Exercice;

/**
 * Résultat d'un dépôt (EF3) : l'exercice enregistré et le nombre de relecteurs attribués aussitôt (0, 1 ou 2, RG8).
 * Les relecteurs manquants seront attribués aux émargements suivants (EF4).
 */
public record DepotExercice(Exercice exercice, int relecteursAttribues) {
}
