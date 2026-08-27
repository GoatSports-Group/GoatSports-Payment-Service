package iuh.fit.paymentservice.application.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainEventEnvelope {
    private UUID eventId;
    private String eventType;
    private UUID aggregateId;
    private String producer;
    private Instant occurredAt;
    private UUID correlationId;
    private UUID actorId;
    private Object payload;
}
