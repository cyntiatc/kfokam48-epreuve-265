package com.kfokam48.epreuve.erreur;

/** Erreur métier traduite en réponse HTTP au format {@code {code, message}}. */
public class ErreurMetierException extends RuntimeException {

    private final CodeErreur code;

    public ErreurMetierException(CodeErreur code, String message) {
        super(message);
        this.code = code;
    }

    public CodeErreur getCode() {
        return code;
    }
}
