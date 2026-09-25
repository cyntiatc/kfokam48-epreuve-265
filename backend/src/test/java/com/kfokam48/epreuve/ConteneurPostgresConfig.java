package com.kfokam48.epreuve;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQL de test partagé par les tests d'intégration : les classes qui importent cette configuration
 * avec les mêmes annotations réutilisent le même contexte Spring, donc le même conteneur.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ConteneurPostgresConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:17-alpine");
    }
}
