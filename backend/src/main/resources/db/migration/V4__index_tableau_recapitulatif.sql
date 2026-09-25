-- V4 : index pour le tableau récapitulatif (EF7, RG11, ENF4).
-- Candidate : Tedjou Nguimzi Cyntia - Matricule 265.
--
-- Les sous-requêtes de GET /api/tableau cherchent les présences et les exercices de chaque étudiant.
-- Les index des contraintes UNIQUE (session_id, etudiant_id) de V1 commencent par session_id :
-- ils ne servent pas à une recherche par étudiant seul.

CREATE INDEX idx_presences_etudiant ON presences (etudiant_id);
CREATE INDEX idx_exercices_etudiant ON exercices (etudiant_id);
