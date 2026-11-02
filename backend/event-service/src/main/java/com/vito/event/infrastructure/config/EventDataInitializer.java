package com.vito.event.infrastructure.config;

import com.vito.event.infrastructure.adapter.outbound.persistence.InvitationMongoAdapter;
import com.vito.event.infrastructure.adapter.outbound.persistence.RSVPMongoAdapter;
import com.vito.event.infrastructure.migration.Ola1DataMigration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Al arrancar: primero la migración de la Ola 1 (T066), que quita duplicados, y después los
 * índices (T021), porque un índice único no se puede crear sobre datos duplicados. Las pruebas
 * que levantan el contexto sin MongoDB lo apagan con {@code vito.data-init.enabled=false}.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vito.data-init.enabled", havingValue = "true", matchIfMissing = true)
public class EventDataInitializer implements ApplicationRunner {

    private final Ola1DataMigration ola1DataMigration;
    private final RSVPMongoAdapter rsvpMongoAdapter;
    private final InvitationMongoAdapter invitationMongoAdapter;

    @Override
    public void run(ApplicationArguments args) {
        ola1DataMigration.run();
        rsvpMongoAdapter.ensureIndexes();
        invitationMongoAdapter.ensureIndexes();
    }
}
