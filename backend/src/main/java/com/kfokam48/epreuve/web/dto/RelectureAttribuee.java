package com.kfokam48.epreuve.web.dto;

import com.kfokam48.epreuve.domain.Exercice;
import com.kfokam48.epreuve.domain.Relecture;
import com.kfokam48.epreuve.domain.SessionCours;
import com.kfokam48.epreuve.domain.StatutRelecture;

/**
 * Élément de la réponse 200 de {@code GET /api/relectures} (schéma {@code RelectureAttribuee}, extension H3).
 * L'auteur de l'exercice n'est pas exposé : le relecteur n'a besoin que du lien.
 */
public record RelectureAttribuee(
        Long id,
        Long sessionId,
        String sessionTitre,
        String lien,
        StatutRelecture statut,
        Integer note,
        String commentaire,
        boolean modifiable) {

    public static RelectureAttribuee depuis(Relecture relecture) {
        Exercice exercice = relecture.getExercice();
        SessionCours session = exercice.getSession();
        return new RelectureAttribuee(
                relecture.getId(),
                session.getId(),
                session.getTitre(),
                exercice.getLien(),
                relecture.getStatut(),
                relecture.getNote(),
                relecture.getCommentaire(),
                relecture.peutEtreSoumise());
    }
}
