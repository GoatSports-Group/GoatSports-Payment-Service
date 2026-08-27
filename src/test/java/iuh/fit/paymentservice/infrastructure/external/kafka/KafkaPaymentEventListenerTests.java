package iuh.fit.paymentservice.infrastructure.external.kafka;

import iuh.fit.paymentservice.application.dto.event.DomainEventEnvelope;
import iuh.fit.paymentservice.infrastructure.external.config.PaymentEventProperties;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaPaymentEventListenerTests {
    private final PaymentEventProperties properties = properties();
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final KafkaPaymentEventListener listener = new KafkaPaymentEventListener(properties, kafkaTemplate);

    @Test
    void publishesEventWithPaymentCorrelationIdAsKafkaKey() {
        DomainEventEnvelope event = event();
        when(kafkaTemplate.send(properties.getTopic(), event.getCorrelationId().toString(), event))
                .thenReturn(CompletableFuture.completedFuture(null));

        listener.publish(event);

        verify(kafkaTemplate).send(
                properties.getTopic(),
                event.getCorrelationId().toString(),
                event
        );
    }

    @Test
    void publishesOnlyAfterBusinessTransactionCommits() throws Exception {
        Method method = KafkaPaymentEventListener.class.getMethod("publish", DomainEventEnvelope.class);
        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    private PaymentEventProperties properties() {
        PaymentEventProperties value = new PaymentEventProperties();
        value.setTopic("payment-events-topic-test");
        value.setSendTimeoutSeconds(1);
        return value;
    }

    private DomainEventEnvelope event() {
        return DomainEventEnvelope.builder()
                .eventId(UUID.randomUUID())
                .eventType("payment.succeeded.v1")
                .aggregateId(UUID.randomUUID())
                .producer("payment-service")
                .occurredAt(Instant.now())
                .correlationId(UUID.randomUUID())
                .payload(new Object())
                .build();
    }
}
