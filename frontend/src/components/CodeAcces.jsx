import { useEffect, useState } from 'react';
import { useTempsRestant } from '../hooks/useTempsRestant.js';
import { IconeCoche, IconeCopier, IconeHorloge } from './Icones.jsx';

const formatDateHeure = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long', timeStyle: 'medium' });

const LIBELLES_COPIE = {
  attente: 'Copier le code',
  copie: 'Code copié',
  echec: 'Copie impossible',
};

/** En dessous de 2 minutes, le compte à rebours passe en orange pour prévenir le formateur. */
const SEUIL_ALERTE_MS = 2 * 60 * 1000;

/** Mise en valeur du code de présence d'une session ouverte (réponse 201 de POST /api/sessions). */
export default function CodeAcces({ session }) {
  const restant = useTempsRestant(session.expirationAt);
  const cloturee = session.statut === 'CLOTUREE';
  const expire = cloturee || restant === 0;
  const [etatCopie, setEtatCopie] = useState('attente');

  useEffect(() => {
    if (etatCopie === 'attente') {
      return undefined;
    }
    const minuteur = setTimeout(() => setEtatCopie('attente'), 2000);
    return () => clearTimeout(minuteur);
  }, [etatCopie]);

  async function copier() {
    try {
      await navigator.clipboard.writeText(session.code);
      setEtatCopie('copie');
    } catch {
      setEtatCopie('echec');
    }
  }

  const duree = new Date(session.expirationAt).getTime() - new Date(session.ouvertureAt).getTime();

  return (
    <div className={`code-acces${expire ? ' code-acces--expire' : ''}`}>
      <div className="code-acces__entete">
        <span className="code-acces__libelle">Code de présence</span>
        <span className={`pastille pastille--${expire ? 'expire' : 'valide'}`} aria-live="polite">
          {libelleEtat(cloturee, expire)}
        </span>
      </div>

      <p className="code-acces__code" aria-live="polite">{session.code}</p>

      {!expire && <CompteARebours restant={restant} duree={duree} />}

      <button type="button" className="bouton bouton--secondaire" onClick={copier}>
        {etatCopie === 'copie' ? <IconeCoche taille={18} /> : <IconeCopier taille={18} />}
        {LIBELLES_COPIE[etatCopie]}
      </button>

      <dl className="code-acces__dates">
        <div>
          <dt>Ouverture</dt>
          <dd>{formatDateHeure.format(new Date(session.ouvertureAt))}</dd>
        </div>
        <div className="code-acces__expiration">
          <dt><IconeHorloge taille={16} /> Expiration</dt>
          <dd>{formatDateHeure.format(new Date(session.expirationAt))}</dd>
        </div>
      </dl>
    </div>
  );
}

/** RG1 : temps restant avant l'expiration du code (minuteur mm:ss et barre de progression). */
function CompteARebours({ restant, duree }) {
  const bientot = restant <= SEUIL_ALERTE_MS;
  const pourcentage = duree > 0 ? Math.min(100, (restant / duree) * 100) : 0;

  return (
    <div className={`compte-a-rebours${bientot ? ' compte-a-rebours--bientot' : ''}`}>
      {/* role="timer" : les lecteurs d'écran n'annoncent pas chaque seconde. */}
      <p className="compte-a-rebours__texte" role="timer">
        <IconeHorloge taille={16} />
        Expire dans <strong>{formaterDuree(restant)}</strong>
      </p>
      <div className="compte-a-rebours__barre" aria-hidden="true">
        <div className="compte-a-rebours__progression" style={{ width: `${pourcentage}%` }} />
      </div>
    </div>
  );
}

function libelleEtat(cloturee, expire) {
  if (cloturee) {
    return 'Clôturée';
  }
  return expire ? 'Expiré' : 'Valide';
}

function formaterDuree(millisecondes) {
  const secondes = Math.ceil(millisecondes / 1000);
  const minutes = Math.floor(secondes / 60);
  return `${String(minutes).padStart(2, '0')}:${String(secondes % 60).padStart(2, '0')}`;
}
