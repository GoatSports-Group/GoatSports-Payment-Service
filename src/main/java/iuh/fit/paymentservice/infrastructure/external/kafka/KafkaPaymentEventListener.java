package iuh.fit.paymentservice.infrastructure.external.kafka;

import iuh.fit.paymentservice.application.dto.event.DomainEventEnvelope;
import iuh.fit.paymentservice.infrastructure.external.config.PaymentEventProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "payment.events",
        name = "publisher-enabled",
        havingValue = "true"
)
public class KafkaPaymentEventListener {
    private final PaymentEventProperties properties;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(DomainEventEnvelope event) {
        try {
            kafkaTemplate.send(properties.getTopic(), event.getCorrelationId().toString(), event)
                    .get(properties.getSendTimeoutSeconds(), TimeUnit.SECONDS);
            log.info("Published payment event {} of type {}", event.getEventId(), event.getEventType());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while publishing payment event {}", event.getEventId(), exception);
        } catch (Exception exception) {
            log.error("Failed to publish payment event {}: {}",
                    event.getEventId(), exception.getMessage(), exception);
        }
    }
}
