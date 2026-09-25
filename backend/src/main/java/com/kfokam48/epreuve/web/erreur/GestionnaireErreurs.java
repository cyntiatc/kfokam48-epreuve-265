package com.kfokam48.epreuve.web.erreur;

import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduit toute erreur au format unique {@code {code, message}} (RG12, ENF2) :
 * erreurs métier, erreurs de requête détectées par Spring MVC, erreurs inattendues.
 */
@RestControllerAdvice
public class GestionnaireErreurs extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GestionnaireErreurs.class);

    static final String MESSAGE_ERREUR_INTERNE = "Une erreur interne est survenue.";

    @ExceptionHandler(ErreurMetierException.class)
    public ResponseEntity<Erreur> gererErreurMetier(ErreurMetierException ex) {
        return ResponseEntity.status(ex.getCode().getStatut())
                .body(new Erreur(ex.getCode().name(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Erreur> gererErreurInattendue(Exception ex) {
        log.error("Erreur inattendue", ex);
        return ResponseEntity.internalServerError()
                .body(new Erreur(CodeErreur.ERREUR_INTERNE.name(), MESSAGE_ERREUR_INTERNE));
    }

    /** Erreurs levées par Spring MVC : corps invalide ou illisible, méthode non supportée, etc. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statut, WebRequest requete) {
        Erreur erreur;
        if (statut.is4xxClientError()) {
            erreur = new Erreur(CodeErreur.REQUETE_INVALIDE.name(), messageRequeteInvalide(ex));
        } else {
            log.error("Erreur inattendue", ex);
            erreur = new Erreur(CodeErreur.ERREUR_INTERNE.name(), MESSAGE_ERREUR_INTERNE);
        }
        return ResponseEntity.status(statut).headers(headers).body(erreur);
    }

    private static String messageRequeteInvalide(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException invalide && invalide.getBindingResult().hasFieldErrors()) {
            return invalide.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return "Le corps de la requête est absent ou mal formé.";
        }
        if (ex instanceof TypeMismatchException) {
            return "Un paramètre de la requête n'a pas le bon format.";
        }
        return "Cette requête n'est pas prise en charge par l'API.";
    }
}
