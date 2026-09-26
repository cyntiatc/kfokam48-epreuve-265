package com.kfokam48.epreuve;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migration V5 (double relecture, évolution de l'Étape 3) appliquée à une base déjà peuplée au format V4.
 * Les migrations sont rejouées dans un schéma dédié du PostgreSQL de test, pour ne pas toucher au schéma
 * utilisé par les autres tests. Même contexte Spring que les autres tests d'intégration (Docker requis).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ConteneurPostgresConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class MigrationDoubleRelectureIntegrationTest {

    private static final String SCHEMA = "migration_v5";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void supprimerLeSchema() {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void v5_conserveLesRelecturesAdapteLesStatutsEtAutoriseUnSecondRelecteurDistinct() {
        migrerJusqua("4");
        // Données au format V4 (étudiants 1 à 5 de la promotion 1, insérés par V3) :
        long sessionOuverte = inserer("""
                INSERT INTO %s.sessions (promotion_id, titre, code, ouverture_at, expiration_at, statut)
                VALUES (1, 'Session ouverte', 'MIGRV5', now(), now() + INTERVAL '15 minutes', 'OUVERTE') RETURNING id
                """);
        long sessionCloturee = inserer("""
                INSERT INTO %s.sessions (promotion_id, titre, code, ouverture_at, expiration_at, statut, cloture_at)
                VALUES (1, 'Session close', 'CLOSV5', now() - INTERVAL '2 hours',
                        now() - INTERVAL '2 hours' + INTERVAL '15 minutes', 'CLOTUREE', now() - INTERVAL '1 hour')
                RETURNING id
                """);
        long relu = exercice(sessionOuverte, 1, "RELU");
        long enRelecture = exercice(sessionOuverte, 3, "EN_RELECTURE");
        long depose = exercice(sessionOuverte, 5, "DEPOSE");
        long definitif = exercice(sessionCloturee, 1, "DEFINITIF");
        relectureRendue(relu, 2, 14);
        relectureEnAttente(enRelecture, 4);
        relectureRendue(definitif, 2, 16);
        // V4 : une seule relecture par exercice.
        assertThatThrownBy(() -> relectureEnAttente(relu, 3)).isInstanceOf(DuplicateKeyException.class);
        List<Map<String, Object>> relecturesAvant = relectures();

        migrerJusqua("5");

        // Aucune relecture perdue ni modifiée.
        assertThat(relectures()).isEqualTo(relecturesAvant).hasSize(3);
        // RELU (une note, session ouverte) redevient EN_RELECTURE : note provisoire, second relecteur attendu (H11).
        assertThat(statut(relu)).isEqualTo("EN_RELECTURE");
        assertThat(statut(enRelecture)).isEqualTo("EN_RELECTURE");
        assertThat(statut(depose)).isEqualTo("DEPOSE");
        assertThat(statut(definitif)).isEqualTo("DEFINITIF");
        // RG8 : un second relecteur distinct est accepté, le même relecteur une seconde fois ne l'est pas.
        relectureEnAttente(relu, 3);
        assertThatThrownBy(() -> relectureEnAttente(relu, 2)).isInstanceOf(DuplicateKeyException.class);
    }

    private void migrerJusqua(String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private long inserer(String sql, Object... parametres) {
        return jdbcTemplate.queryForObject(sql.formatted(SCHEMA), Long.class, parametres);
    }

    private long exercice(long sessionId, long auteurId, String statut) {
        return inserer("""
                INSERT INTO %s.exercices (session_id, etudiant_id, lien, statut, depose_at)
                VALUES (?, ?, 'https://exemple.com/migration', ?, now()) RETURNING id
                """, sessionId, auteurId, statut);
    }

    private void relectureRendue(long exerciceId, long relecteurId, int note) {
        inserer("""
                INSERT INTO %s.relectures (exercice_id, relecteur_id, note, commentaire, statut, attribuee_at, rendue_at)
                VALUES (?, ?, ?, 'Rendue avant V5.', 'RENDUE', now(), now()) RETURNING id
                """, exerciceId, relecteurId, note);
    }

    private void relectureEnAttente(long exerciceId, long relecteurId) {
        inserer("""
                INSERT INTO %s.relectures (exercice_id, relecteur_id, statut, attribuee_at)
                VALUES (?, ?, 'EN_ATTENTE', now()) RETURNING id
                """, exerciceId, relecteurId);
    }

    private List<Map<String, Object>> relectures() {
        return jdbcTemplate.queryForList("""
                SELECT id, exercice_id, relecteur_id, note, commentaire, statut, attribuee_at, rendue_at
                FROM %s.relectures ORDER BY id
                """.formatted(SCHEMA));
    }

    private String statut(long exerciceId) {
        return jdbcTemplate.queryForObject("SELECT statut FROM %s.exercices WHERE id = ?".formatted(SCHEMA),
                String.class, exerciceId);
    }
}
