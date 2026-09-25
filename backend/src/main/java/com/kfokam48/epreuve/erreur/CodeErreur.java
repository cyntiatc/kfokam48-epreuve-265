package com.kfokam48.epreuve.erreur;

import org.springframework.http.HttpStatus;

/** Codes d'erreur métier renvoyés dans le champ {@code code} du format {@code Erreur} (RG12). */
public enum CodeErreur {

    /** Champ manquant ou incorrect, identifiant inconnu (H2). */
    REQUETE_INVALIDE(HttpStatus.BAD_REQUEST),

    /** Aucune session ne correspond au code de présence saisi (EF2). */
    CODE_INCONNU(HttpStatus.BAD_REQUEST),

    /** L'étudiant est déjà présent à cette session (RG3). */
    DEJA_PRESENT(HttpStatus.CONFLICT),

    /** Le code de présence a expiré ou la session est clôturée (RG1). */
    CODE_EXPIRE(HttpStatus.GONE),

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
