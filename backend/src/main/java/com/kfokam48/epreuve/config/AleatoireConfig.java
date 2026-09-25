package com.kfokam48.epreuve.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/** Générateur du tirage au sort des relecteurs (RG8). Injecté pour pouvoir le rendre déterministe dans les tests. */
@Configuration
public class AleatoireConfig {

    @Bean
    public RandomGenerator aleatoire() {
        return new SecureRandom();
    }
}
