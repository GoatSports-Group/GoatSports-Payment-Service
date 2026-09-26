package iuh.fit.paymentservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một sự kiện chờ gửi Kafka. Ghi cùng transaction nghiệp vụ nên không bao giờ mất khi Kafka lỗi. */
@Entity
@Table(name = "payment_event_outbox")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEventOutboxEntity {
    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Column(name = "message_key")
    private String messageKey;
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
    @Column(name = "attempts", nullable = false)
    private int attempts;
    @Column(name = "last_error")
    private String lastError;
}
