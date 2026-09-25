import { useState } from 'react';
import { ouvrirSession } from '../api/client.js';
import Banniere from './Banniere.jsx';
import Champ from './Champ.jsx';
import CodeAcces from './CodeAcces.jsx';

/** EF1 : le formateur ouvre une session et obtient le code de présence. */
export default function FormulaireSession({ session, onSessionOuverte }) {
  const [titre, setTitre] = useState('');
  const [promotionId, setPromotionId] = useState('');
  const [envoiEnCours, setEnvoiEnCours] = useState(false);
  const [erreur, setErreur] = useState(null);

  async function soumettre(evenement) {
    evenement.preventDefault();
    setEnvoiEnCours(true);
    setErreur(null);
    try {
      onSessionOuverte(await ouvrirSession(titre, Number(promotionId)));
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
          libelle="Titre de la session"
          aide="150 caractères maximum."
          placeholder="ex. Spring Boot — API REST"
          value={titre}
          onChange={(e) => setTitre(e.target.value)}
          maxLength={150}
          required
        />
        <Champ
          libelle="Identifiant de la promotion"
          aide="Identifiant numérique de la promotion concernée."
          placeholder="ex. 1"
          type="number"
          min="1"
          step="1"
          value={promotionId}
          onChange={(e) => setPromotionId(e.target.value)}
          required
        />
        <button
          type="submit"
          className="bouton bouton--principal"
          disabled={envoiEnCours}
          aria-busy={envoiEnCours}
        >
          {envoiEnCours && <span className="spinner" aria-hidden="true" />}
          {envoiEnCours ? 'Ouverture…' : 'Ouvrir la session'}
        </button>
      </form>

      {erreur && (
        <Banniere titre="La session n'a pas pu être ouverte" code={erreur.code}>
          {erreur.message}
        </Banniere>
      )}

      {session && <CodeAcces key={session.id} session={session} />}
    </>
  );
}
