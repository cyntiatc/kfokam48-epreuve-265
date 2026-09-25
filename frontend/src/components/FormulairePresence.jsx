import { useState } from 'react';
import { marquerPresence } from '../api/client.js';
import Banniere from './Banniere.jsx';
import Champ from './Champ.jsx';

/** EF2 : l'étudiant émarge avec le code de présence communiqué par le formateur. */
export default function FormulairePresence() {
  const [code, setCode] = useState('');
  const [etudiantId, setEtudiantId] = useState('');
  const [envoiEnCours, setEnvoiEnCours] = useState(false);
  const [presence, setPresence] = useState(null);
  const [erreur, setErreur] = useState(null);

  async function soumettre(evenement) {
    evenement.preventDefault();
    setEnvoiEnCours(true);
    setErreur(null);
    setPresence(null);
    try {
      setPresence(await marquerPresence(code.trim(), Number(etudiantId)));
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
          libelle="Code de présence"
          aide="6 caractères, communiqués par le formateur."
          className="champ__saisie--code"
          placeholder="CG8IMD"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          maxLength={6}
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          required
        />
        <Champ
          libelle="Identifiant étudiant"
          aide="Votre identifiant numérique d'étudiant."
          placeholder="ex. 1"
          type="number"
          min="1"
          step="1"
          value={etudiantId}
          onChange={(e) => setEtudiantId(e.target.value)}
          required
        />
        <button
          type="submit"
          className="bouton bouton--principal"
          disabled={envoiEnCours}
          aria-busy={envoiEnCours}
        >
          {envoiEnCours && <span className="spinner" aria-hidden="true" />}
          {envoiEnCours ? 'Émargement…' : 'Émarger'}
        </button>
      </form>

      {erreur && (
        <Banniere titre="Émargement refusé" code={erreur.code}>
          {erreur.message}
        </Banniere>
      )}

      {presence && (
        <Banniere type="succes" titre="Émargement confirmé avec succès !">
          Présence enregistrée pour la session n° {presence.sessionId}.
        </Banniere>
      )}
    </>
  );
}
