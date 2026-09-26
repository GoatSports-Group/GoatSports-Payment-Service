package iuh.fit.paymentservice.infrastructure.external.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import iuh.fit.paymentservice.application.dto.event.DomainEventEnvelope;
import iuh.fit.paymentservice.application.port.event.DomainEventPublisherPort;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PaymentEventOutboxEntity;
import iuh.fit.paymentservice.infrastructure.persistence.repository.jpa.JpaPaymentEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Transactional outbox: sự kiện được lưu vào bảng payment_event_outbox trong CÙNG transaction với thay đổi
 * payment/refund. Transaction rollback thì sự kiện cũng mất (đúng), commit thì chắc chắn còn để
 * {@link PaymentEventOutboxRelay} gửi Kafka — Kafka lỗi hay service tắt giữa chừng cũng không mất.
 */
@Component
@RequiredArgsConstructor
public class OutboxDomainEventPublisher implements DomainEventPublisherPort {
    /** Định dạng cố định cho hợp đồng sự kiện: ngày giờ là chuỗi ISO-8601, không phụ thuộc cấu hình Jackson của app. */
    static final ObjectMapper EVENT_MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final JpaPaymentEventOutboxRepository outboxRepository;

    @Override
    public void publish(DomainEventEnvelope event) {
        try {
            outboxRepository.save(PaymentEventOutboxEntity.builder()
                    .eventId(event.getEventId())
                    .eventType(event.getEventType())
                    .messageKey(event.getCorrelationId() == null ? null : event.getCorrelationId().toString())
                    .payload(EVENT_MAPPER.writeValueAsString(event))
                    .createdAt(LocalDateTime.now())
                    .attempts(0)
                    .build());
        } catch (JsonProcessingException exception) {
            // Không ghi được sự kiện thì hủy luôn giao dịch nghiệp vụ, không để trạng thái đổi mà không ai biết.
            throw new IllegalStateException("Không ghi được payment event " + event.getEventType(), exception);
        }
    }
}
