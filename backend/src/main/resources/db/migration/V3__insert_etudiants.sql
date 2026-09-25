-- V3 : étudiants de référence (H4 : étudiants pré-chargés). Données fictives.
-- Candidate : Tedjou Nguimzi Cyntia - Matricule 265.
--
-- L3 GL (promotion 1) : 5 étudiants, nombre impair, pour tester l'attribution des relectures (RG8, section 7.2).
-- M1 Software Engineering (promotion 2) : 2 étudiants, le minimum pour qu'une relecture entre pairs soit possible.

-- Identifiants fixes pour que les tests manuels puissent viser etudiantId = 1 à 7.
-- ON CONFLICT DO NOTHING : sans effet si un étudiant de même id, matricule ou e-mail existe déjà.
INSERT INTO etudiants (id, matricule, nom, prenom, email, promotion_id) VALUES
    (1, 'L3GL-001', 'Mbarga',  'Alice',   'alice.mbarga@example.com',   1),
    (2, 'L3GL-002', 'Nkoulou', 'Brice',   'brice.nkoulou@example.com',  1),
    (3, 'L3GL-003', 'Fotso',   'Carine',  'carine.fotso@example.com',   1),
    (4, 'L3GL-004', 'Ngono',   'David',   'david.ngono@example.com',    1),
    (5, 'L3GL-005', 'Tchoupo', 'Estelle', 'estelle.tchoupo@example.com', 1),
    (6, 'M1SE-001', 'Kamga',   'Fabrice', 'fabrice.kamga@example.com',  2),
    (7, 'M1SE-002', 'Essomba', 'Grace',   'grace.essomba@example.com',  2)
ON CONFLICT DO NOTHING;

-- Les id explicites ne font pas avancer la séquence d'identité : on la recale sur le plus grand id existant.
SELECT setval(pg_get_serial_sequence('etudiants', 'id'), (SELECT MAX(id) FROM etudiants));
