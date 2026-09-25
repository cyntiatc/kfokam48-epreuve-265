import { useEffect, useState } from 'react';
import { useEstExpire } from '../hooks/useEstExpire.js';
import { IconeCoche, IconeCopier, IconeHorloge } from './Icones.jsx';

const formatDateHeure = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long', timeStyle: 'medium' });

const LIBELLES_COPIE = {
  attente: 'Copier le code',
  copie: 'Code copié',
  echec: 'Copie impossible',
};

/** Mise en valeur du code de présence d'une session ouverte (réponse 201 de POST /api/sessions). */
export default function CodeAcces({ session }) {
  const expire = useEstExpire(session.expirationAt);
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

  return (
    <div className={`code-acces${expire ? ' code-acces--expire' : ''}`}>
      <div className="code-acces__entete">
        <span className="code-acces__libelle">Code de présence</span>
        <span className={`pastille pastille--${expire ? 'expire' : 'valide'}`}>
          {expire ? 'Expiré' : 'Valable 15 min'}
        </span>
      </div>

      <p className="code-acces__code" aria-live="polite">{session.code}</p>

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
