import { useRef } from 'react';

const idOnglet = (id) => `onglet-${id}`;
const idPanneau = (id) => `panneau-${id}`;

/**
 * Barre d'onglets accessible (motif WAI-ARIA « Tabs ») :
 * flèches gauche/droite pour changer d'onglet, Début et Fin pour aller au premier ou au dernier.
 */
export default function Onglets({ onglets, actif, onChange, libelle }) {
  const boutons = useRef({});

  function activer(index) {
    const onglet = onglets[(index + onglets.length) % onglets.length];
    onChange(onglet.id);
    boutons.current[onglet.id]?.focus();
  }

  function gererClavier(evenement) {
    const index = onglets.findIndex((onglet) => onglet.id === actif);
    const cibles = { ArrowRight: index + 1, ArrowLeft: index - 1, Home: 0, End: onglets.length - 1 };
    if (Object.hasOwn(cibles, evenement.key)) {
      evenement.preventDefault();
      activer(cibles[evenement.key]);
    }
  }

  return (
    <div className="onglets" role="tablist" aria-label={libelle} onKeyDown={gererClavier}>
      {onglets.map((onglet) => {
        const estActif = onglet.id === actif;
        return (
          <button
            key={onglet.id}
            ref={(bouton) => {
              boutons.current[onglet.id] = bouton;
            }}
            type="button"
            role="tab"
            id={idOnglet(onglet.id)}
            className="onglet"
            aria-selected={estActif}
            aria-controls={idPanneau(onglet.id)}
            tabIndex={estActif ? 0 : -1}
            onClick={() => onChange(onglet.id)}
          >
            {onglet.icone}
            {onglet.libelle}
          </button>
        );
      })}
    </div>
  );
}

/** Contenu d'un onglet : masqué (et non démonté) quand l'onglet n'est pas actif, pour garder la saisie. */
export function PanneauOnglet({ id, actif, children }) {
  return (
    <div role="tabpanel" id={idPanneau(id)} aria-labelledby={idOnglet(id)} hidden={id !== actif}>
      {children}
    </div>
  );
}
