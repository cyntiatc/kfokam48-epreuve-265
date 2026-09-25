package com.kfokam48.epreuve.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Lecture du tableau récapitulatif (EF7) : une requête SQL agrégée plutôt que le chargement des entités,
 * pour rester rapide sur une promotion entière (ENF4).
 */
@Repository
public class TableauRepository {

    /**
     * RG11 : une ligne par étudiant de la promotion, y compris sans activité, triée par nom.
     * Seules les sessions de la promotion comptent. La moyenne inclut les notes encore modifiables (H7).
     */
    private static final String REQUETE = """
            SELECT e.id AS etudiant_id,
                   e.nom,
                   e.prenom,
                   (SELECT COUNT(*)
                      FROM presences p JOIN sessions s ON s.id = p.session_id
                     WHERE p.etudiant_id = e.id AND s.promotion_id = e.promotion_id) AS presences,
                   (SELECT COUNT(*)
                      FROM exercices x JOIN sessions s ON s.id = x.session_id
                     WHERE x.etudiant_id = e.id AND s.promotion_id = e.promotion_id) AS exercices_deposes,
                   (SELECT ROUND(AVG(r.note), 2)
                      FROM relectures r
                      JOIN exercices x ON x.id = r.exercice_id
                      JOIN sessions s ON s.id = x.session_id
                     WHERE x.etudiant_id = e.id AND s.promotion_id = e.promotion_id
                       AND r.statut = 'RENDUE') AS moyenne,
                   (SELECT COUNT(*)
                      FROM relectures r
                      JOIN exercices x ON x.id = r.exercice_id
                      JOIN sessions s ON s.id = x.session_id
                     WHERE r.relecteur_id = e.id AND s.promotion_id = e.promotion_id
                       AND r.statut = 'EN_ATTENTE') AS relectures_en_attente
              FROM etudiants e
             WHERE e.promotion_id = :promotionId
             ORDER BY e.nom, e.prenom, e.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public TableauRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<StatistiquesEtudiant> statistiquesDeLaPromotion(Long promotionId) {
        return jdbc.query(REQUETE, Map.of("promotionId", promotionId), (resultat, ligne) -> new StatistiquesEtudiant(
                resultat.getLong("etudiant_id"),
                resultat.getString("nom"),
                resultat.getString("prenom"),
                resultat.getLong("presences"),
                resultat.getLong("exercices_deposes"),
                resultat.getBigDecimal("moyenne"),
                resultat.getLong("relectures_en_attente")));
    }
}
