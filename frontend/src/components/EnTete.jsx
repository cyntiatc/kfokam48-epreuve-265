import { useEstExpire } from '../hooks/useEstExpire.js';

const formatHeure = new Intl.DateTimeFormat('fr-FR', { timeStyle: 'short' });

/** Barre de navigation : marque et statut de la dernière session ouverte depuis ce navigateur. */
export default function EnTete({ session }) {
  const expire = useEstExpire(session?.expirationAt);

  let statut;
  if (!session) {
    statut = { variante: 'neutre', texte: 'Aucune session ouverte' };
  } else if (expire) {
    statut = { variante: 'alerte', texte: 'Code de présence expiré' };
  } else {
    const heure = formatHeure.format(new Date(session.expirationAt));
    statut = { variante: 'actif', texte: `Session ouverte · code valable jusqu'à ${heure}` };
  }

  return (
    <header className="navbar">
      <div className="navbar__contenu">
        <div className="navbar__marque">
          <span className="navbar__logo" aria-hidden="true">K</span>
          <div>
            <span className="navbar__titre">KFOKAM48</span>
            <span className="navbar__sous-titre">Présences et relectures</span>
          </div>
        </div>

        <span className={`badge badge--${statut.variante}`} role="status">
          <span className="badge__point" aria-hidden="true" />
          {statut.texte}
        </span>
      </div>
    </header>
  );
}
