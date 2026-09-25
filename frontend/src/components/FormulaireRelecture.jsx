import { useState } from 'react';
import { listerRelectures, rendreRelecture } from '../api/client.js';
import Banniere from './Banniere.jsx';
import Champ from './Champ.jsx';

/**
 * EF5, EF6 : le relecteur retrouve les relectures qui lui sont attribuées (route H3), ouvre l'exercice,
 * puis le note (0 à 20) et le commente. L'identifiant est pré-rempli d'après le dernier émargement.
 */
export default function FormulaireRelecture({ etudiantIdInitial }) {
  const [relecteurId, setRelecteurId] = useState(String(etudiantIdInitial ?? ''));
  const [relectures, setRelectures] = useState(null);
  const [selection, setSelection] = useState(null);
  const [chargement, setChargement] = useState(false);
  const [erreur, setErreur] = useState(null);

  async function charger() {
    setChargement(true);
    setErreur(null);
    try {
      const liste = await listerRelectures(Number(relecteurId));
      setRelectures(liste);
      // La relecture choisie est remplacée par sa version à jour, ou retirée si elle n'est plus dans la liste.
      setSelection((actuelle) => actuelle && (liste.find((r) => r.id === actuelle.id) ?? null));
    } catch (e) {
      setErreur(e);
      setRelectures(null);
      setSelection(null);
    } finally {
      setChargement(false);
    }
  }

  function soumettre(evenement) {
    evenement.preventDefault();
    charger();
  }

  return (
    <>
      <form className="formulaire" onSubmit={soumettre}>
        <Champ
          libelle="Votre identifiant étudiant"
          aide="Pré-rempli d’après votre dernier émargement, s’il y en a un."
          placeholder="ex. 2"
          type="number"
          min="1"
          step="1"
          value={relecteurId}
          onChange={(e) => setRelecteurId(e.target.value)}
          required
        />
        <button
          type="submit"
          className="bouton bouton--principal"
          disabled={chargement}
          aria-busy={chargement}
        >
          {chargement && <span className="spinner" aria-hidden="true" />}
          {chargement ? 'Chargement…' : 'Afficher mes relectures'}
        </button>
      </form>

      {erreur && (
        <Banniere titre="Relectures indisponibles" code={erreur.code}>
          {erreur.message}
        </Banniere>
      )}

      {relectures && (
        <ListeRelectures relectures={relectures} selection={selection} onSelection={setSelection} />
      )}

      {selection && <NotationRelecture key={selection.id} relecture={selection} onEnregistree={charger} />}
    </>
  );
}

function ListeRelectures({ relectures, selection, onSelection }) {
  if (relectures.length === 0) {
    return <p className="liste-vide">Aucune relecture ne vous est attribuée pour le moment.</p>;
  }

  return (
    <ul className="liste-relectures">
      {relectures.map((relecture) => {
        const choisie = selection?.id === relecture.id;
        return (
          <li key={relecture.id} className={`relecture${choisie ? ' relecture--choisie' : ''}`}>
            <div className="relecture__infos">
              <p className="relecture__titre">{relecture.sessionTitre}</p>
              <p className="relecture__meta">
                Relecture n° {relecture.id} · <StatutRelecture relecture={relecture} />
              </p>
              <a className="relecture__lien" href={relecture.lien} target="_blank" rel="noopener noreferrer">
                Ouvrir l’exercice
              </a>
            </div>
            <button
              type="button"
              className="bouton bouton--secondaire"
              aria-pressed={choisie}
              onClick={() => onSelection(relecture)}
            >
              {libelleAction(relecture)}
            </button>
          </li>
        );
      })}
    </ul>
  );
}

function StatutRelecture({ relecture }) {
  if (relecture.statut === 'EN_ATTENTE') {
    return <span className="pastille pastille--attente">À rendre</span>;
  }
  return relecture.modifiable
    ? <span className="pastille pastille--valide">Rendue · {relecture.note}/20</span>
    : <span className="pastille pastille--definitive">Définitive · {relecture.note}/20</span>;
}

function libelleAction(relecture) {
  if (relecture.statut === 'EN_ATTENTE') {
    return 'Noter';
  }
  return relecture.modifiable ? 'Modifier' : 'Consulter';
}

function NotationRelecture({ relecture, onEnregistree }) {
  const [note, setNote] = useState(relecture.note === null ? '' : String(relecture.note));
  const [commentaire, setCommentaire] = useState(relecture.commentaire ?? '');
  const [envoiEnCours, setEnvoiEnCours] = useState(false);
  const [enregistree, setEnregistree] = useState(false);
  const [erreur, setErreur] = useState(null);

  async function soumettre(evenement) {
    evenement.preventDefault();
    setEnvoiEnCours(true);
    setErreur(null);
    setEnregistree(false);
    try {
      await rendreRelecture(relecture.id, Number(note), commentaire.trim());
      setEnregistree(true);
      onEnregistree();
    } catch (e) {
      setErreur(e);
    } finally {
      setEnvoiEnCours(false);
    }
  }

  return (
    <section className="notation" aria-labelledby={`notation-${relecture.id}`}>
      <h3 id={`notation-${relecture.id}`} className="notation__titre">
        Relecture n° {relecture.id} · {relecture.sessionTitre}
      </h3>

      {!relecture.modifiable && (
        <p className="notation__info">La session est clôturée : cette note est définitive.</p>
      )}

      <form className="formulaire" onSubmit={soumettre}>
        <fieldset className="formulaire__groupe" disabled={!relecture.modifiable || envoiEnCours}>
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
          <button type="submit" className="bouton bouton--principal" aria-busy={envoiEnCours}>
            {envoiEnCours && <span className="spinner" aria-hidden="true" />}
            {envoiEnCours ? 'Envoi…' : 'Envoyer la relecture'}
          </button>
        </fieldset>
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
    </section>
  );
}
