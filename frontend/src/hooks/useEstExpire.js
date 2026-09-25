import { useEffect, useState } from 'react';

/**
 * Indique si une date d'expiration (ISO-8601) est dépassée, et bascule à true au moment où elle l'est.
 * Affichage indicatif : c'est l'horloge du serveur qui fait foi pour refuser un code expiré (ENF6).
 */
export function useEstExpire(expirationAt) {
  const [expire, setExpire] = useState(() => estDepassee(expirationAt));

  useEffect(() => {
    setExpire(estDepassee(expirationAt));
    if (!expirationAt) {
      return undefined;
    }
    const delai = new Date(expirationAt).getTime() - Date.now();
    if (delai <= 0) {
      return undefined;
    }
    const minuteur = setTimeout(() => setExpire(true), delai);
    return () => clearTimeout(minuteur);
  }, [expirationAt]);

  return expire;
}

function estDepassee(expirationAt) {
  return Boolean(expirationAt) && new Date(expirationAt).getTime() <= Date.now();
}
