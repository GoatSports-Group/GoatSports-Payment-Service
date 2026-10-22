package iuh.fit.paymentservice.infrastructure.external.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import iuh.fit.paymentservice.application.dto.event.DomainEventEnvelope;
import iuh.fit.paymentservice.infrastructure.external.config.PaymentEventProperties;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PaymentEventOutboxEntity;
import iuh.fit.paymentservice.infrastructure.persistence.repository.jpa.JpaPaymentEventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class PaymentEventOutboxRelayTests {
    private final JpaPaymentEventOutboxRepository repository = mock(JpaPaymentEventOutboxRepository.class);
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final PaymentEventProperties properties = new PaymentEventProperties();
    private PaymentEventOutboxRelay relay;

    @BeforeEach
    void setUp() {
        properties.setTopic("payment-events-topic");
        properties.setSendTimeoutSeconds(5);
        relay = new PaymentEventOutboxRelay(repository, kafkaTemplate, properties);
    }

    @Test
    void sendsPendingEventsInOrderAndMarksThemPublished() {
        PaymentEventOutboxEntity first = event("payment.succeeded.v1");
        PaymentEventOutboxEntity second = event("refund.succeeded.v1");
        when(repository.lockPending(PaymentEventOutboxRelay.BATCH_SIZE)).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(eq("payment-events-topic"), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        relay.relay();

        assertThat(first.getPublishedAt()).isNotNull();
        assertThat(second.getPublishedAt()).isNotNull();
        verify(kafkaTemplate).send(eq("payment-events-topic"), eq(first.getMessageKey()), any(JsonNode.class));
    }

    @Test
    void keepsEventForRetryAndStopsTheBatchWhenKafkaFails() {
        PaymentEventOutboxEntity first = event("payment.succeeded.v1");
        PaymentEventOutboxEntity second = event("refund.succeeded.v1");
        when(repository.lockPending(PaymentEventOutboxRelay.BATCH_SIZE)).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        relay.relay();

        assertThat(first.getPublishedAt()).isNull();
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).contains("broker down");
        verify(kafkaTemplate, never()).send(any(), eq(second.getMessageKey()), any());
    }

    @Test
    void storedEventWritesDatesAsIsoStrings() throws Exception {
        DomainEventEnvelope envelope = DomainEventEnvelope.builder()
                .eventId(UUID.randomUUID()).eventType("payment.succeeded.v1").occurredAt(Instant.parse("2026-09-26T11:07:31Z"))
                .payload(Map.of("paidAt", LocalDateTime.of(2026, 9, 26, 18, 7, 31))).build();

        JsonNode json = OutboxDomainEventPublisher.EVENT_MAPPER.readTree(OutboxDomainEventPublisher.EVENT_MAPPER.writeValueAsString(envelope));

        assertThat(json.path("occurredAt").asText()).isEqualTo("2026-09-26T11:07:31Z");
        assertThat(json.path("payload").path("paidAt").asText()).isEqualTo("2026-09-26T18:07:31");
    }

    private PaymentEventOutboxEntity event(String type) {
        return PaymentEventOutboxEntity.builder().eventId(UUID.randomUUID()).eventType(type)
                .messageKey(UUID.randomUUID().toString()).payload("{\"eventType\":\"" + type + "\",\"payload\":{}}")
                .createdAt(LocalDateTime.now()).attempts(0).build();
    }
}
