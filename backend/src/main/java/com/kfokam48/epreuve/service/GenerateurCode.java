package com.kfokam48.epreuve.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Génère un code de présence de 6 caractères parmi A-Z et 0-9 (RG10). */
@Component
public class GenerateurCode {

    static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    static final int LONGUEUR = 6;

    private final SecureRandom aleatoire = new SecureRandom();

    public String generer() {
        StringBuilder code = new StringBuilder(LONGUEUR);
        for (int i = 0; i < LONGUEUR; i++) {
            code.append(ALPHABET.charAt(aleatoire.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
