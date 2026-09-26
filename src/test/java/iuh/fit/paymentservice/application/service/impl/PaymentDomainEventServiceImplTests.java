package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.event.DomainEventEnvelope;
import iuh.fit.paymentservice.application.dto.event.PaymentEventPayload;
import iuh.fit.paymentservice.application.dto.event.RefundEventPayload;
import iuh.fit.paymentservice.application.port.event.DomainEventPublisherPort;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.entity.Refund;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.RefundStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PaymentDomainEventServiceImplTests {
    private final DomainEventPublisherPort eventPublisherPort = mock(DomainEventPublisherPort.class);
    private final PaymentDomainEventServiceImpl service = new PaymentDomainEventServiceImpl(eventPublisherPort);

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"SUCCEEDED", "FAILED", "CANCELLED", "EXPIRED"})
    void recordsVersionedPaymentResultContract(PaymentStatus status) {
        UUID paymentId = UUID.randomUUID();
        UUID payerId = UUID.randomUUID();
        LocalDateTime paidAt = LocalDateTime.now();
        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .payerId(payerId)
                .purpose(PaymentPurpose.BOOKING_DEPOSIT)
                .referenceType(PaymentReferenceType.BOOKING)
                .referenceId(UUID.randomUUID())
                .amount(150_000D)
                .currency("VND")
                .status(status)
                .paidAt(paidAt)
                .build();
        PaymentAttempt attempt = PaymentAttempt.builder()
                .attemptId(UUID.randomUUID())
                .paymentId(paymentId)
                .provider(PaymentProvider.PAYOS)
                .method(PaymentMethod.BANK_TRANSFER)
                .providerTransactionId("payos-transaction")
                .status(status == PaymentStatus.SUCCEEDED
                        ? PaymentAttemptStatus.SUCCEEDED
                        : PaymentAttemptStatus.FAILED)
                .failureCode(status == PaymentStatus.FAILED ? "1006" : null)
                .failureReason(status == PaymentStatus.FAILED ? "User denied" : null)
                .build();

        Instant before = Instant.now();
        service.recordPaymentResult(payment, attempt, payerId);

        DomainEventEnvelope event = capturedEvent();
        assertThat(event.getEventId()).isNotNull();
        String expectedType = switch (status) {
            case SUCCEEDED -> "payment.succeeded.v1";
            case FAILED -> "payment.failed.v1";
            case CANCELLED -> "payment.cancelled.v1";
            case EXPIRED -> "payment.expired.v1";
            default -> throw new IllegalStateException("Unexpected status");
        };
        assertThat(event.getEventType()).isEqualTo(expectedType);
        assertThat(event.getAggregateId()).isEqualTo(paymentId);
        assertThat(event.getProducer()).isEqualTo("payment-service");
        assertThat(event.getOccurredAt()).isBetween(before, Instant.now());
        assertThat(event.getCorrelationId()).isEqualTo(paymentId);
        assertThat(event.getActorId()).isEqualTo(payerId);
        assertThat(event.getPayload()).isInstanceOf(PaymentEventPayload.class);
        PaymentEventPayload payload = (PaymentEventPayload) event.getPayload();
        assertThat(payload.paymentId()).isEqualTo(paymentId);
        assertThat(payload.status()).isEqualTo(status);
        assertThat(payload.provider()).isEqualTo(PaymentProvider.PAYOS);
        verify(eventPublisherPort).publish(event);
    }

    @ParameterizedTest
    @EnumSource(value = RefundStatus.class, names = {"SUCCEEDED", "FAILED", "MANUAL_REVIEW"})
    void recordsVersionedRefundResultContract(RefundStatus status) {
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        Refund refund = Refund.builder()
                .refundId(refundId)
                .paymentId(paymentId)
                .requestedBy(requestedBy)
                .reason("Booking cancelled")
                .amount(50_000D)
                .providerRefundId("provider-refund")
                .status(status)
                .failureReason(status == RefundStatus.FAILED ? "Provider rejected" : null)
                .refundedAt(status == RefundStatus.SUCCEEDED ? LocalDateTime.now() : null)
                .build();

        service.recordRefundResult(refund, PaymentStatus.PARTIALLY_REFUNDED, UUID.randomUUID());

        DomainEventEnvelope event = capturedEvent();
        String expectedType = switch (status) {
            case SUCCEEDED -> "refund.succeeded.v1";
            case FAILED -> "refund.failed.v1";
            case MANUAL_REVIEW -> "refund.manual-review.v1";
            default -> throw new IllegalStateException("Unexpected status");
        };
        assertThat(event.getEventType()).isEqualTo(expectedType);
        assertThat(event.getAggregateId()).isEqualTo(refundId);
        assertThat(event.getCorrelationId()).isEqualTo(paymentId);
        assertThat(event.getActorId()).isEqualTo(requestedBy);
        assertThat(event.getPayload()).isInstanceOf(RefundEventPayload.class);
        RefundEventPayload payload = (RefundEventPayload) event.getPayload();
        assertThat(payload.refundId()).isEqualTo(refundId);
        assertThat(payload.paymentId()).isEqualTo(paymentId);
        assertThat(payload.status()).isEqualTo(status);
        assertThat(payload.paymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        verify(eventPublisherPort).publish(event);
    }

    @Test
    void ignoresNonTerminalResults() {
        Payment payment = Payment.builder()
                .paymentId(UUID.randomUUID())
                .status(PaymentStatus.PENDING)
                .build();
        PaymentAttempt attempt = PaymentAttempt.builder().build();
        Refund refund = Refund.builder()
                .refundId(UUID.randomUUID())
                .paymentId(payment.getPaymentId())
                .status(RefundStatus.PENDING)
                .build();

        service.recordPaymentResult(payment, attempt, UUID.randomUUID());
        service.recordRefundResult(refund, PaymentStatus.SUCCEEDED, UUID.randomUUID());

        verify(eventPublisherPort, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    private DomainEventEnvelope capturedEvent() {
        ArgumentCaptor<DomainEventEnvelope> eventCaptor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(eventPublisherPort).publish(eventCaptor.capture());
        return eventCaptor.getValue();
    }
}
