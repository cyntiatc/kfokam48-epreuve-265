package com.kfokam48.epreuve.erreur;

import org.springframework.http.HttpStatus;

/** Codes d'erreur métier renvoyés dans le champ {@code code} du format {@code Erreur} (RG12). */
public enum CodeErreur {

    /** Champ manquant ou incorrect, identifiant inconnu (H2). */
    REQUETE_INVALIDE(HttpStatus.BAD_REQUEST),

    /** Erreur inattendue côté serveur (ENF2). */
    ERREUR_INTERNE(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus statut;

    CodeErreur(HttpStatus statut) {
        this.statut = statut;
    }

    public HttpStatus getStatut() {
        return statut;
    }
}
