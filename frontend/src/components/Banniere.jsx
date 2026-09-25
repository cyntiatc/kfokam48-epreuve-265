import { IconeErreur, IconeSucces } from './Icones.jsx';

/** Bannière de réponse : "erreur" (rouge, par défaut) ou "succes" (vert). */
export default function Banniere({ type = 'erreur', titre, code, children }) {
  const estErreur = type === 'erreur';
  const IconeBanniere = estErreur ? IconeErreur : IconeSucces;

  return (
    <div className={`banniere banniere--${type}`} role={estErreur ? 'alert' : 'status'}>
      <IconeBanniere className="banniere__icone" />
      <div>
        <p className="banniere__titre">{titre}</p>
        {children && <p>{children}</p>}
        {code && <span className="banniere__code">{code}</span>}
      </div>
    </div>
  );
}
