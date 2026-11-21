package com.vito.auth.infrastructure.config;

import com.vito.auth.infrastructure.adapter.outbound.persistence.UserMongoAdapter;
import com.vito.auth.infrastructure.migration.Ola2aSearchMigration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Al arrancar: primero la migración de la búsqueda (T028), que llena {@code search.*} en los
 * usuarios existentes, y después sus índices. Mismo patrón que {@code SocialDataInitializer}. Las
 * pruebas que levantan el contexto sin MongoDB lo apagan con {@code vito.data-init.enabled=false}.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vito.data-init.enabled", havingValue = "true", matchIfMissing = true)
public class AuthDataInitializer implements ApplicationRunner {

    private final Ola2aSearchMigration ola2aSearchMigration;
    private final UserMongoAdapter userMongoAdapter;

    @Override
    public void run(ApplicationArguments args) {
        ola2aSearchMigration.run();
        userMongoAdapter.ensureSearchIndexes();
    }
}
