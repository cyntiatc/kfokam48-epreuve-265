import { useId } from 'react';

/** Champ de formulaire : libellé, saisie et texte d'aide reliés pour l'accessibilité. */
export default function Champ({ libelle, aide, className = '', ...proprietesSaisie }) {
  const id = useId();
  const idAide = `${id}-aide`;

  return (
    <div className="champ">
      <label className="champ__libelle" htmlFor={id}>{libelle}</label>
      <input
        id={id}
        className={`champ__saisie ${className}`.trim()}
        aria-describedby={aide ? idAide : undefined}
        {...proprietesSaisie}
      />
      {aide && <p id={idAide} className="champ__aide">{aide}</p>}
    </div>
  );
}
