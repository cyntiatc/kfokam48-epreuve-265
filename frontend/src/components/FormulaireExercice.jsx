import { useState } from 'react';
import { deposerExercice } from '../api/client.js';
import Banniere from './Banniere.jsx';
import Champ from './Champ.jsx';

/** RG8 (évolution de l'Étape 3) : chaque exercice est relu par deux relecteurs distincts. */
const RELECTEURS_ATTENDUS = 2;

/** Situation des relecteurs au dépôt : les manquants sont désignés aux émargements suivants (EF4). */
function situationDesRelecteurs(relecteursAttribues) {
  if (relecteursAttribues >= RELECTEURS_ATTENDUS) {
    return 'ses deux relecteurs lui ont été attribués.';
  }
  if (relecteursAttribues === 1) {
    return 'un premier relecteur lui a été attribué ; le second sera désigné dès qu’un autre étudiant émargera.';
  }
  return 'aucun relecteur n’est encore disponible ; les deux seront désignés parmi les prochains étudiants qui émargeront.';
}

/**
 * EF3 : l'étudiant dépose le lien de son exercice pour une session.
 * Les identifiants sont pré-remplis d'après le dernier émargement réussi, s'il y en a un.
 */
export default function FormulaireExercice({ sessionIdInitial, etudiantIdInitial }) {
  const [sessionId, setSessionId] = useState(String(sessionIdInitial ?? ''));
  const [etudiantId, setEtudiantId] = useState(String(etudiantIdInitial ?? ''));
  const [lien, setLien] = useState('');
  const [envoiEnCours, setEnvoiEnCours] = useState(false);
  const [exercice, setExercice] = useState(null);
  const [erreur, setErreur] = useState(null);

  const preRempli = sessionIdInitial != null;

  async function soumettre(evenement) {
    evenement.preventDefault();
    setEnvoiEnCours(true);
    setErreur(null);
    setExercice(null);
    try {
      setExercice(await deposerExercice(Number(sessionId), Number(etudiantId), lien.trim()));
    } catch (e) {
      setErreur(e);
    } finally {
      setEnvoiEnCours(false);
    }
  }

  return (
    <>
      <form className="formulaire" onSubmit={soumettre}>
        <Champ
          libelle="Identifiant de la session"
          aide={preRempli ? 'Pré-rempli d’après votre dernier émargement.' : 'Identifiant numérique de la session.'}
          placeholder="ex. 12"
          type="number"
          min="1"
          step="1"
          value={sessionId}
          onChange={(e) => setSessionId(e.target.value)}
          required
        />
        <Champ
          libelle="Identifiant étudiant"
          aide="Votre identifiant numérique d’étudiant."
          placeholder="ex. 1"
          type="number"
          min="1"
          step="1"
          value={etudiantId}
          onChange={(e) => setEtudiantId(e.target.value)}
          required
        />
        <Champ
          libelle="Lien de l’exercice"
          aide="Adresse http ou https : dépôt Git, document partagé… Un seul dépôt par session."
          placeholder="https://github.com/…"
          type="url"
          value={lien}
          onChange={(e) => setLien(e.target.value)}
          maxLength={2048}
          required
        />
        <button
          type="submit"
          className="bouton bouton--principal"
          disabled={envoiEnCours}
          aria-busy={envoiEnCours}
        >
          {envoiEnCours && <span className="spinner" aria-hidden="true" />}
          {envoiEnCours ? 'Dépôt…' : 'Déposer l’exercice'}
        </button>
      </form>

      {erreur && (
        <Banniere titre="Dépôt refusé" code={erreur.code}>
          {erreur.message}
        </Banniere>
      )}

      {exercice && (
        <Banniere type="succes" titre="Exercice déposé avec succès !">
          Exercice n° {exercice.id} : {situationDesRelecteurs(exercice.relecteursAttribues)}
          <br />
          Relecteurs attribués : <strong>{exercice.relecteursAttribues} / {RELECTEURS_ATTENDUS}</strong>
        </Banniere>
      )}
    </>
  );
}
