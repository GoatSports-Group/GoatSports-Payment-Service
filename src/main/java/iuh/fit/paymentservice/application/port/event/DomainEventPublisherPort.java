package iuh.fit.paymentservice.application.port.event;

import iuh.fit.paymentservice.application.dto.event.DomainEventEnvelope;

public interface DomainEventPublisherPort {
    void publish(DomainEventEnvelope event);
}
