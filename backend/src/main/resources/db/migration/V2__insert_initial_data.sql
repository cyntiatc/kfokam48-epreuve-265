-- V2 : données de référence initiales (H4 : promotions pré-chargées).
-- Candidate : Tedjou Nguimzi Cyntia - Matricule 265.

-- Identifiants fixes pour que les tests manuels puissent viser promotionId = 1 ou 2.
-- ON CONFLICT DO NOTHING : sans effet si une promotion de même id ou de même libellé
-- existe déjà (par exemple insérée à la main avant cette migration).
INSERT INTO promotions (id, libelle) VALUES
    (1, 'L3 GL'),
    (2, 'M1 Software Engineering')
ON CONFLICT DO NOTHING;

-- Les id explicites ne font pas avancer la séquence d'identité : on la recale sur le
-- plus grand id existant pour que les prochaines insertions ne réutilisent pas 1 ou 2.
SELECT setval(pg_get_serial_sequence('promotions', 'id'), (SELECT MAX(id) FROM promotions));
