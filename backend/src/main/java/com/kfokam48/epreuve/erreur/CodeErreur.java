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

    /** Le lien de l'exercice n'est pas une URL http ou https valide (RG5). */
    LIEN_INVALIDE(HttpStatus.BAD_REQUEST),

    /** L'étudiant a déjà déposé un exercice pour cette session (RG4). */
    EXERCICE_DEJA_DEPOSE(HttpStatus.CONFLICT),

    /** Note absente, non entière ou hors de [0 ; 20] (RG6). */
    NOTE_INVALIDE(HttpStatus.BAD_REQUEST),

    /** Le relecteur attribué est l'auteur de l'exercice (RG2). */
    AUTO_RELECTURE(HttpStatus.FORBIDDEN),

    /** Relecture déjà rendue et session clôturée : la note est définitive (RG9). */
    RELECTURE_DEJA_RENDUE(HttpStatus.CONFLICT),

    /** Aucune promotion ne correspond à l'identifiant demandé (EF7). */
    PROMOTION_INCONNUE(HttpStatus.NOT_FOUND),

    /** La session est déjà clôturée (EF8, extension H3). */
    SESSION_DEJA_CLOTUREE(HttpStatus.CONFLICT),

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
