// Appels à l'API REST. En développement, Vite relaie /api vers le backend (vite.config.js).

/** Erreur au format unique de l'API : { code, message } (RG12). */
export class ErreurApi extends Error {
  constructor(code, message, statut) {
    super(message);
    this.code = code;
    this.statut = statut;
  }
}

async function envoyer(chemin, corps) {
  let reponse;
  try {
    reponse = await fetch(chemin, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(corps),
    });
  } catch {
    throw new ErreurApi('SERVEUR_INJOIGNABLE',
      'Impossible de joindre le serveur. Vérifiez votre connexion.', 0);
  }

  const donnees = await reponse.json().catch(() => null);

  if (!reponse.ok) {
    if (donnees?.code && donnees?.message) {
      throw new ErreurApi(donnees.code, donnees.message, reponse.status);
    }
    // Réponse sans corps {code, message} : typiquement le proxy Vite quand le backend est arrêté.
    throw new ErreurApi('SERVEUR_INDISPONIBLE',
      `Le serveur est indisponible (statut ${reponse.status}). Vérifiez que le backend est démarré.`,
      reponse.status);
  }
  return donnees;
}

/** EF1 : POST /api/sessions -> { id, code, ouvertureAt, expirationAt } */
export function ouvrirSession(titre, promotionId) {
  return envoyer('/api/sessions', { titre, promotionId });
}

/** EF2 : POST /api/presences -> { id, sessionId, etudiantId, source } */
export function marquerPresence(code, etudiantId) {
  return envoyer('/api/presences', { code, etudiantId });
}
