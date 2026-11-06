package com.vito.event.infrastructure.adapter.outbound.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** T026 (R12): el resultado de cada envío queda en el log; el error, sin datos de personas. */
@ExtendWith(OutputCaptureExtension.class)
class KafkaEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

    private final KafkaEventPublisher publisher = new KafkaEventPublisher(kafkaTemplate);

    @Test
    void aFailedSendIsLoggedWithTypeAndEventIdOnly(CapturedOutput output) {
        when(kafkaTemplate.send(eq(KafkaEventPublisher.TOPIC), eq("evt_1"), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker usr_secreto caído")));

        publisher.publishRSVPChanged("evt_1", "usr_secreto", "ACCEPTED");

        String log = output.getAll();
        assertTrue(log.contains("No se pudo publicar RSVP_CHANGED del evento evt_1"), log);
        assertFalse(log.contains("usr_secreto"), "el log no debe llevar el userId");
    }

    @Test
    void aSuccessfulSendIsLoggedOnlyAfterTheBrokerConfirms(CapturedOutput output) {
        CompletableFuture<SendResult<String, Object>> pending = new CompletableFuture<>();
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(pending);

        publisher.publishEventCreated("evt_2", "usr_org");
        assertFalse(output.getAll().contains("Published EVENT_CREATED"));

        pending.complete(null);
        assertTrue(output.getAll().contains("Published EVENT_CREATED for event: evt_2"));
    }
}
