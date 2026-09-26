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
     * <p>
     * RG6 (évolution de l'Étape 3) : la note d'un exercice est la moyenne de ses notes rendues, et la moyenne de
     * l'étudiant est celle des notes de ses exercices. Une note issue d'une seule relecture, sur un exercice qui n'est
     * pas DEFINITIF, est provisoire : {@code est_provisoire} signale qu'au moins une note de la moyenne l'est.
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
                   (SELECT ROUND(AVG(n.note_exercice), 2)
                      FROM (SELECT AVG(r.note) AS note_exercice
                              FROM relectures r
                              JOIN exercices x ON x.id = r.exercice_id
                              JOIN sessions s ON s.id = x.session_id
                             WHERE x.etudiant_id = e.id AND s.promotion_id = e.promotion_id
                               AND r.statut = 'RENDUE'
                             GROUP BY x.id) n) AS moyenne,
                   EXISTS (SELECT 1
                             FROM relectures r
                             JOIN exercices x ON x.id = r.exercice_id
                             JOIN sessions s ON s.id = x.session_id
                            WHERE x.etudiant_id = e.id AND s.promotion_id = e.promotion_id
                              AND r.statut = 'RENDUE' AND x.statut <> 'DEFINITIF'
                            GROUP BY x.id
                           HAVING COUNT(*) = 1) AS est_provisoire,
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
                resultat.getBoolean("est_provisoire"),
                resultat.getLong("relectures_en_attente")));
    }
}
