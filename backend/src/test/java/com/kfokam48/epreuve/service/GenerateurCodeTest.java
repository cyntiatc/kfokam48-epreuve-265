package com.kfokam48.epreuve.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateurCodeTest {

    private final GenerateurCode generateur = new GenerateurCode();

    @Test
    void genereSixCaracteresParmiMajusculesEtChiffres() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(generateur.generer()).matches("^[A-Z0-9]{6}$");
        }
    }

    @Test
    void genereDesCodesVaries() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            codes.add(generateur.generer());
        }
        // 36^6 combinaisons : des doublons sur 1 000 tirages trahiraient un générateur défaillant.
        assertThat(codes).hasSizeGreaterThan(990);
    }
}
