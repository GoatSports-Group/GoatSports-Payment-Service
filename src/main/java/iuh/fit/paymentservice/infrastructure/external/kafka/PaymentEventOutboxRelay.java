package iuh.fit.paymentservice.infrastructure.external.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import iuh.fit.paymentservice.infrastructure.external.config.PaymentEventProperties;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PaymentEventOutboxEntity;
import iuh.fit.paymentservice.infrastructure.persistence.repository.jpa.JpaPaymentEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Gửi các sự kiện trong outbox lên Kafka theo thứ tự tạo. Gửi lỗi thì dừng lượt này (giữ thứ tự), tăng
 * số lần thử và để lượt sau gửi lại. Người nhận khử trùng theo eventId nên gửi lại an toàn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "payment.events", name = "publisher-enabled", havingValue = "true")
public class PaymentEventOutboxRelay {
    static final int BATCH_SIZE = 100;

    private final JpaPaymentEventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentEventProperties properties;

    @Scheduled(fixedDelayString = "${payment.events.relay-delay-ms:2000}")
    @Transactional
    public void relay() {
        List<PaymentEventOutboxEntity> pending = outboxRepository.lockPending(BATCH_SIZE);
        for (PaymentEventOutboxEntity event : pending) {
            if (!send(event)) break;
        }
    }

    boolean send(PaymentEventOutboxEntity event) {
        try {
            // Gửi dạng JsonNode để message trên Kafka là object JSON (không phải chuỗi JSON lồng trong chuỗi).
            JsonNode message = OutboxDomainEventPublisher.EVENT_MAPPER.readTree(event.getPayload());
            kafkaTemplate.send(properties.getTopic(), event.getMessageKey(), message)
                    .get(properties.getSendTimeoutSeconds(), TimeUnit.SECONDS);
            event.setPublishedAt(LocalDateTime.now());
            event.setLastError(null);
            log.info("Published payment event {} of type {}", event.getEventId(), event.getEventType());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            markFailed(event, exception);
            return false;
        } catch (Exception exception) {
            markFailed(event, exception);
            return false;
        } finally {
            event.setAttempts(event.getAttempts() + 1);
            outboxRepository.save(event);
        }
    }

    private void markFailed(PaymentEventOutboxEntity event, Exception exception) {
        String message = String.valueOf(exception.getMessage());
        event.setLastError(message.length() > 1000 ? message.substring(0, 1000) : message);
        log.warn("Chưa gửi được payment event {} ({} lần), sẽ thử lại: {}",
                event.getEventId(), event.getAttempts() + 1, message);
    }
}
