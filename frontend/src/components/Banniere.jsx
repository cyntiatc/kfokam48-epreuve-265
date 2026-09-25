import { IconeErreur } from './Icones.jsx';

/** Bannière d'erreur : titre, message renvoyé par l'API et code d'erreur. */
export default function Banniere({ titre, code, children }) {
  return (
    <div className="banniere" role="alert">
      <IconeErreur className="banniere__icone" />
      <div>
        <p className="banniere__titre">{titre}</p>
        <p>{children}</p>
        {code && <span className="banniere__code">{code}</span>}
      </div>
    </div>
  );
}
