package com.kfokam48.epreuve.repository;

import java.math.BigDecimal;

/** Activité d'un étudiant dans sa promotion, telle que calculée par RG11. {@code moyenne} est null sans note reçue. */
public record StatistiquesEtudiant(
        Long etudiantId,
        String nom,
        String prenom,
        long presences,
        long exercicesDeposes,
        BigDecimal moyenne,
        long relecturesEnAttente) {
}
