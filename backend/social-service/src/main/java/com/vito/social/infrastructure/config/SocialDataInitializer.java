package com.vito.social.infrastructure.config;

import com.vito.social.infrastructure.adapter.outbound.persistence.FollowMongoAdapter;
import com.vito.social.infrastructure.migration.Ola2aSocialMigration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Al arrancar: primero la migración de la Ola 2a (T014), que quita relaciones duplicadas, y
 * después los índices (T015), porque un índice único no se puede crear sobre duplicados. Mismo
 * patrón que {@code EventDataInitializer}. Las pruebas que levantan el contexto sin MongoDB lo
 * apagan con {@code vito.data-init.enabled=false}.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vito.data-init.enabled", havingValue = "true", matchIfMissing = true)
public class SocialDataInitializer implements ApplicationRunner {

    private final Ola2aSocialMigration ola2aSocialMigration;
    private final FollowMongoAdapter followMongoAdapter;

    @Override
    public void run(ApplicationArguments args) {
        ola2aSocialMigration.run();
        followMongoAdapter.ensureIndexes();
    }
}
