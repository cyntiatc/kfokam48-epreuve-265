import { useId } from 'react';

export default function Carte({ icone, titre, sousTitre, children }) {
  const idTitre = useId();

  return (
    <section className="carte" aria-labelledby={idTitre}>
      <header className="carte__entete">
        <span className="carte__icone">{icone}</span>
        <div>
          <h2 id={idTitre} className="carte__titre">{titre}</h2>
          <p className="carte__sous-titre">{sousTitre}</p>
        </div>
      </header>
      {children}
    </section>
  );
}
