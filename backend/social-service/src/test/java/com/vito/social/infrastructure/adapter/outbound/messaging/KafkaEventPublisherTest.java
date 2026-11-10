package com.vito.social.infrastructure.adapter.outbound.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T020 (Ola 2a, R10): un envío fallido queda registrado, con el tipo de evento y sin ids de personas. */
@ExtendWith(OutputCaptureExtension.class)
class KafkaEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final KafkaEventPublisher publisher = new KafkaEventPublisher(kafkaTemplate);

    @Test
    void aFailedSendIsLoggedWithTheTypeAndWithoutPeopleIds(CapturedOutput output) {
        when(kafkaTemplate.send(eq(KafkaEventPublisher.TOPIC), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker caído")));

        publisher.publishFollowEvent("usr_ana_123", "usr_jose_456");

        assertTrue(output.getOut().contains("No se publicó USER_FOLLOWED en social-events"), output.getOut());
        assertTrue(output.getOut().contains("broker caído"));
        assertFalse(output.getOut().contains("usr_ana_123"));
        assertFalse(output.getOut().contains("usr_jose_456"));
    }

    @Test
    void sendsTheEventKeyedByTheFollower() {
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(new CompletableFuture<>());

        publisher.publishUnfollowEvent("usr_ana_123", "usr_jose_456");

        verify(kafkaTemplate).send(eq("social-events"), eq("usr_ana_123"),
                any(Map.class));
    }
}
