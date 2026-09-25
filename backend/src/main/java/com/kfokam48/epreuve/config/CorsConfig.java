package com.kfokam48.epreuve.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Autorise le frontend React à appeler l'API depuis une autre origine (ENF5 : CORS limité au frontend).
 * En développement, le proxy Vite rend ces appels same-origin ; cette configuration couvre les appels directs.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] originesAutorisees;

    public CorsConfig(@Value("${app.cors.origines-autorisees:http://localhost:5173}") String[] originesAutorisees) {
        this.originesAutorisees = originesAutorisees;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(originesAutorisees)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
