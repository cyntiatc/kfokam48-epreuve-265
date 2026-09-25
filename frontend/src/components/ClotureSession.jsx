import { useEffect, useRef, useState } from 'react';
import { cloturerSession } from '../api/client.js';
import Banniere from './Banniere.jsx';
import Champ from './Champ.jsx';

/**
 * EF8 : le formateur clôture une session, après confirmation car l'action est irréversible.
 * L'identifiant est pré-rempli avec la session ouverte depuis ce navigateur, s'il y en a une.
 */
export default function ClotureSession({ sessionIdInitial, onSessionCloturee }) {
  const [sessionId, setSessionId] = useState(String(sessionIdInitial ?? ''));
  const [confirmation, setConfirmation] = useState(false);
  const [envoiEnCours, setEnvoiEnCours] = useState(false);
  const [bilan, setBilan] = useState(null);
  const [erreur, setErreur] = useState(null);
  const boutonConfirmer = useRef(null);

  useEffect(() => {
    if (confirmation) {
      boutonConfirmer.current?.focus();
    }
  }, [confirmation]);

  function demanderConfirmation(evenement) {
    evenement.preventDefault();
    setErreur(null);
    setBilan(null);
    setConfirmation(true);
  }

  async function confirmer() {
    setEnvoiEnCours(true);
    try {
      const resultat = await cloturerSession(Number(sessionId));
      setBilan(resultat);
      onSessionCloturee?.(resultat);
    } catch (e) {
      setErreur(e);
    } finally {
      setEnvoiEnCours(false);
      setConfirmation(false);
    }
  }

  return (
    <>
      <form className="formulaire" onSubmit={demanderConfirmation}>
        <Champ
          libelle="Identifiant de la session"
          aide={sessionIdInitial != null
            ? 'Pré-rempli avec la session ouverte ci-dessus.'
            : 'Identifiant numérique de la session à clôturer.'}
          placeholder="ex. 12"
          type="number"
          min="1"
          step="1"
          value={sessionId}
          onChange={(e) => setSessionId(e.target.value)}
          disabled={confirmation}
          required
        />
        {!confirmation && (
          <button type="submit" className="bouton bouton--danger">Clôturer la session</button>
        )}
      </form>

      {confirmation && (
        <div className="confirmation" role="group" aria-label="Confirmation de la clôture">
          <p>
            Clôturer la session n° {sessionId} ? Le code de présence ne sera plus accepté et les notes rendues
            deviendront définitives. <strong>Cette action est irréversible.</strong>
          </p>
          <div className="confirmation__actions">
            <button
              ref={boutonConfirmer}
              type="button"
              className="bouton bouton--danger"
              onClick={confirmer}
              disabled={envoiEnCours}
              aria-busy={envoiEnCours}
            >
              {envoiEnCours && <span className="spinner" aria-hidden="true" />}
              {envoiEnCours ? 'Clôture…' : 'Confirmer la clôture'}
            </button>
            <button
              type="button"
              className="bouton bouton--secondaire"
              onClick={() => setConfirmation(false)}
              disabled={envoiEnCours}
            >
              Annuler
            </button>
          </div>
        </div>
      )}

      {erreur && (
        <Banniere titre="Clôture refusée" code={erreur.code}>
          {erreur.message}
        </Banniere>
      )}

      {bilan && (
        <Banniere type="succes" titre={`Session n° ${bilan.id} clôturée`}>
          {resumerBilan(bilan)}
        </Banniere>
      )}
    </>
  );
}

function resumerBilan(bilan) {
  return [
    pluriel(bilan.exercicesDefinitifs, 'note devenue définitive', 'notes devenues définitives'),
    pluriel(bilan.exercicesSansRelecture, 'exercice resté sans relecture', 'exercices restés sans relecture'),
    pluriel(bilan.relecturesEnAttente,
      'relecture encore attendue (elle pourra être rendue une fois)',
      'relectures encore attendues (elles pourront être rendues une fois)'),
  ].join(' · ');
}

function pluriel(nombre, singulier, plurielTexte) {
  return `${nombre} ${nombre > 1 ? plurielTexte : singulier}`;
}
