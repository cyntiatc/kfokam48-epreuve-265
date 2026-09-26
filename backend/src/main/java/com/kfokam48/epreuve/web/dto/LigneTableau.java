package com.kfokam48.epreuve.web.dto;

import com.kfokam48.epreuve.repository.StatistiquesEtudiant;

/** Ligne de la réponse 200 de {@code GET /api/tableau} (schéma {@code LigneTableau} du contrat). */
public record LigneTableau(
        Long etudiantId,
        String nom,
        long presences,
        long exercicesDeposes,
        Double moyenne,
        boolean estProvisoire,
        long relecturesEnAttente) {

    public static LigneTableau depuis(StatistiquesEtudiant statistiques) {
        return new LigneTableau(
                statistiques.etudiantId(),
                statistiques.nom() + " " + statistiques.prenom(),
                statistiques.presences(),
                statistiques.exercicesDeposes(),
                statistiques.moyenne() == null ? null : statistiques.moyenne().doubleValue(),
                statistiques.estProvisoire(),
                statistiques.relecturesEnAttente());
    }
}
