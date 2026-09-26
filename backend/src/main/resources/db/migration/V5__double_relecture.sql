-- V5 : double relecture croisée, évolution imposée à l'Étape 3 (RG6, RG8, section 7.4 du cahier des charges).
-- Candidate : Tedjou Nguimzi Cyntia - Matricule 265.
--
-- Chaque exercice reçoit désormais deux relecteurs distincts. Aucune ligne n'est supprimée :
-- les relectures existantes (au plus une par exercice) respectent déjà la nouvelle contrainte.

-- RG8 : un même relecteur au plus une fois par exercice, au lieu d'une seule relecture par exercice.
-- L'index de la nouvelle contrainte commence par exercice_id : il sert toujours les jointures par exercice.
ALTER TABLE relectures DROP CONSTRAINT uk_relectures_exercice;
ALTER TABLE relectures ADD CONSTRAINT uk_relectures_exercice_relecteur UNIQUE (exercice_id, relecteur_id);

-- D4 : RELU signifie désormais « deux notes rendues ». Un exercice relu une seule fois dans une session
-- encore ouverte redevient EN_RELECTURE : sa note est provisoire et il attend son second relecteur (H11).
-- Les exercices des sessions clôturées (DEFINITIF, SANS_RELECTURE) restent inchangés.
UPDATE exercices x
   SET statut = 'EN_RELECTURE'
  FROM sessions s
 WHERE s.id = x.session_id
   AND s.statut = 'OUVERTE'
   AND x.statut = 'RELU';
