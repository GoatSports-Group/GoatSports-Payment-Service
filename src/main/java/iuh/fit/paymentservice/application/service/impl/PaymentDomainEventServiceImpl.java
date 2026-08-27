package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.event.DomainEventEnvelope;
import iuh.fit.paymentservice.application.dto.event.PaymentEventPayload;
import iuh.fit.paymentservice.application.dto.event.RefundEventPayload;
import iuh.fit.paymentservice.application.port.event.DomainEventPublisherPort;
import iuh.fit.paymentservice.application.service.PaymentDomainEventService;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.entity.Refund;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.RefundStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentDomainEventServiceImpl implements PaymentDomainEventService {
    static final String PRODUCER = "payment-service";
    static final String PAYMENT_SUCCEEDED = "payment.succeeded.v1";
    static final String PAYMENT_FAILED = "payment.failed.v1";
    static final String REFUND_SUCCEEDED = "refund.succeeded.v1";
    static final String REFUND_FAILED = "refund.failed.v1";
    static final String REFUND_MANUAL_REVIEW = "refund.manual-review.v1";

    private final DomainEventPublisherPort domainEventPublisherPort;

    @Override
    public void recordPaymentResult(Payment payment, PaymentAttempt attempt, UUID actorId) {
        String eventType = switch (payment.getStatus()) {
            case SUCCEEDED -> PAYMENT_SUCCEEDED;
            case FAILED -> PAYMENT_FAILED;
            default -> null;
        };
        if (eventType == null) {
            return;
        }
        PaymentEventPayload payload = new PaymentEventPayload(
                payment.getPaymentId(),
                payment.getPayerId(),
                payment.getPurpose(),
                payment.getReferenceType(),
                payment.getReferenceId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getPaidAt(),
                attempt.getProvider(),
                attempt.getMethod(),
                attempt.getProviderTransactionId(),
                attempt.getFailureCode(),
                attempt.getFailureReason()
        );
        append(eventType, payment.getPaymentId(), payment.getPaymentId(), actorId, payload);
    }

    @Override
    public void recordRefundResult(Refund refund, PaymentStatus paymentStatus) {
        String eventType = switch (refund.getStatus()) {
            case SUCCEEDED -> REFUND_SUCCEEDED;
            case FAILED -> REFUND_FAILED;
            case MANUAL_REVIEW -> REFUND_MANUAL_REVIEW;
            default -> null;
        };
        if (eventType == null) {
            return;
        }
        RefundEventPayload payload = new RefundEventPayload(
                refund.getRefundId(),
                refund.getPaymentId(),
                refund.getRequestedBy(),
                refund.getReason(),
                refund.getAmount(),
                refund.getProviderRefundId(),
                refund.getStatus(),
                paymentStatus,
                refund.getFailureReason(),
                refund.getRefundedAt()
        );
        append(eventType, refund.getRefundId(), refund.getPaymentId(), refund.getRequestedBy(), payload);
    }

    private void append(
            String eventType,
            UUID aggregateId,
            UUID paymentId,
            UUID actorId,
            Object payload
    ) {
        DomainEventEnvelope event = DomainEventEnvelope.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .aggregateId(aggregateId)
                .producer(PRODUCER)
                .occurredAt(Instant.now())
                .correlationId(paymentId)
                .actorId(actorId)
                .payload(payload)
                .build();
        domainEventPublisherPort.publish(event);
    }
}
