import { useId } from 'react';

/**
 * Champ de formulaire : libellé, saisie et texte d'aide reliés pour l'accessibilité.
 * Avec `multiligne`, la saisie est une zone de texte au lieu d'une ligne.
 */
export default function Champ({ libelle, aide, multiligne = false, className = '', ...proprietesSaisie }) {
  const id = useId();
  const idAide = `${id}-aide`;
  const Saisie = multiligne ? 'textarea' : 'input';

  return (
    <div className="champ">
      <label className="champ__libelle" htmlFor={id}>{libelle}</label>
      <Saisie
        id={id}
        className={`champ__saisie ${className}`.trim()}
        aria-describedby={aide ? idAide : undefined}
        {...proprietesSaisie}
      />
      {aide && <p id={idAide} className="champ__aide">{aide}</p>}
    </div>
  );
}
