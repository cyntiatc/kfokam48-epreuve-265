import { useState } from 'react';
import { rendreRelecture } from '../api/client.js';
import Banniere from './Banniere.jsx';
import Champ from './Champ.jsx';

/** EF5, EF6 : le relecteur note (0 à 20) et commente l'exercice qui lui a été attribué. */
export default function FormulaireRelecture() {
  const [relectureId, setRelectureId] = useState('');
  const [note, setNote] = useState('');
  const [commentaire, setCommentaire] = useState('');
  const [envoiEnCours, setEnvoiEnCours] = useState(false);
  const [enregistree, setEnregistree] = useState(false);
  const [erreur, setErreur] = useState(null);

  async function soumettre(evenement) {
    evenement.preventDefault();
    setEnvoiEnCours(true);
    setErreur(null);
    setEnregistree(false);
    try {
      await rendreRelecture(Number(relectureId), Number(note), commentaire.trim());
      setEnregistree(true);
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
          libelle="Identifiant de la relecture"
          aide="Numéro de la relecture qui vous a été attribuée."
          placeholder="ex. 7"
          type="number"
          min="1"
          step="1"
          value={relectureId}
          onChange={(e) => setRelectureId(e.target.value)}
          required
        />
        <Champ
          libelle="Note sur 20"
          aide="Nombre entier de 0 à 20."
          placeholder="ex. 15"
          type="number"
          min="0"
          max="20"
          step="1"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          required
        />
        <Champ
          multiligne
          libelle="Commentaire"
          aide="Points forts et points à améliorer, 1 000 caractères au plus."
          rows={5}
          value={commentaire}
          onChange={(e) => setCommentaire(e.target.value)}
          maxLength={1000}
          required
        />
        <button
          type="submit"
          className="bouton bouton--principal"
          disabled={envoiEnCours}
          aria-busy={envoiEnCours}
        >
          {envoiEnCours && <span className="spinner" aria-hidden="true" />}
          {envoiEnCours ? 'Envoi…' : 'Envoyer la relecture'}
        </button>
      </form>

      {erreur && (
        <Banniere titre="Relecture refusée" code={erreur.code}>
          {erreur.message}
        </Banniere>
      )}

      {enregistree && (
        <Banniere type="succes" titre="Relecture enregistrée !">
          Vous pouvez encore corriger votre note en la renvoyant, tant que la session n’est pas clôturée.
        </Banniere>
      )}
    </>
  );
}
