import { useEffect, useState } from 'react';

/**
 * Millisecondes restantes avant une échéance (ISO-8601), mises à jour chaque seconde, 0 une fois dépassée.
 * Affichage indicatif fondé sur l'horloge du navigateur : c'est le serveur qui refuse un code expiré (ENF6).
 */
export function useTempsRestant(echeance) {
  const [restant, setRestant] = useState(() => calculerRestant(echeance));

  useEffect(() => {
    setRestant(calculerRestant(echeance));
    if (calculerRestant(echeance) === 0) {
      return undefined;
    }
    const minuteur = setInterval(() => {
      const valeur = calculerRestant(echeance);
      setRestant(valeur);
      if (valeur === 0) {
        clearInterval(minuteur);
      }
    }, 1000);
    return () => clearInterval(minuteur);
  }, [echeance]);

  return restant;
}

function calculerRestant(echeance) {
  return echeance ? Math.max(0, new Date(echeance).getTime() - Date.now()) : 0;
}
