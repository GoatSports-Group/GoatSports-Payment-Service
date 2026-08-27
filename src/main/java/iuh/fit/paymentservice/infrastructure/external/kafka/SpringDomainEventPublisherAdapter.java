package iuh.fit.paymentservice.infrastructure.external.kafka;

import iuh.fit.paymentservice.application.dto.event.DomainEventEnvelope;
import iuh.fit.paymentservice.application.port.event.DomainEventPublisherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpringDomainEventPublisherAdapter implements DomainEventPublisherPort {
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(DomainEventEnvelope event) {
        applicationEventPublisher.publishEvent(event);
    }
}
