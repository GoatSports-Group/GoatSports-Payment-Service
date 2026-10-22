package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.response.payment.ProviderPaymentStatusResult;
import iuh.fit.paymentservice.application.port.client.PaymentStatusProviderPort;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.service.PaymentDomainEventService;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceImplTests {
    @Mock private PaymentAttemptRepository paymentAttemptRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentStatusProviderPort paymentStatusProviderPort;
    @Mock private PaymentDomainEventService paymentDomainEventService;

    private PaymentReconciliationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentReconciliationServiceImpl(
                paymentAttemptRepository,
                paymentRepository,
                paymentStatusProviderPort,
                paymentDomainEventService
        );
        when(paymentAttemptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void confirmsPaidPaymentWhenWebhookWasMissed() {
        TestData data = data(LocalDateTime.now().plusMinutes(5));
        when(paymentStatusProviderPort.get(data.paymentLinkId())).thenReturn(result(
                data, ProviderPaymentStatusResult.Status.PAID, 30_000L, null, null
        ));

        service.reconcile(data.attempt().getAttemptId());

        assertThat(data.attempt().getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
        assertThat(data.payment().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(data.payment().getPaidAt()).isNotNull();
        verify(paymentDomainEventService).recordPaymentResult(
                data.payment(), data.attempt(), data.payment().getPayerId()
        );
    }

    @Test
    void closesExpiredPendingLinkAndPublishesExpiredResult() {
        TestData data = data(LocalDateTime.now().minusSeconds(1));
        when(paymentStatusProviderPort.get(data.paymentLinkId())).thenReturn(result(
                data, ProviderPaymentStatusResult.Status.PENDING, 0L, null, null
        ));
        when(paymentStatusProviderPort.cancel(data.paymentLinkId(), "GoatSports payment expired"))
                .thenReturn(result(
                        data, ProviderPaymentStatusResult.Status.CANCELLED, 0L,
                        "PAYOS_CANCELLED", "cancelled"
                ));

        service.reconcile(data.attempt().getAttemptId());

        assertThat(data.attempt().getStatus()).isEqualTo(PaymentAttemptStatus.EXPIRED);
        assertThat(data.attempt().getFailureCode()).isEqualTo("PAYMENT_EXPIRED");
        assertThat(data.payment().getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(paymentDomainEventService).recordPaymentResult(
                data.payment(), data.attempt(), data.payment().getPayerId()
        );
    }

    @Test
    void recordsProviderCancellationWithReason() {
        TestData data = data(LocalDateTime.now().plusMinutes(5));
        when(paymentStatusProviderPort.get(data.paymentLinkId())).thenReturn(result(
                data, ProviderPaymentStatusResult.Status.CANCELLED, 0L,
                "PAYOS_CANCELLED", "Người dùng hủy thanh toán"
        ));

        service.reconcile(data.attempt().getAttemptId());

        assertThat(data.attempt().getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
        assertThat(data.attempt().getFailureReason()).isEqualTo("Người dùng hủy thanh toán");
        assertThat(data.payment().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void cancelsActivePaymentAndPublishesResult() {
        TestData data = data(LocalDateTime.now().plusMinutes(5));
        when(paymentAttemptRepository.findLatestByPaymentIdAndStatuses(
                data.payment().getPaymentId(),
                EnumSet.of(PaymentAttemptStatus.REDIRECT_READY, PaymentAttemptStatus.PENDING)
        )).thenReturn(Optional.of(data.attempt()));
        when(paymentStatusProviderPort.cancel(data.paymentLinkId(), "Người dùng hủy"))
                .thenReturn(result(
                        data, ProviderPaymentStatusResult.Status.CANCELLED, 0L,
                        "PAYOS_CANCELLED", "Người dùng hủy"
                ));

        service.cancel(data.payment().getPaymentId(), data.payment().getPayerId(), "Người dùng hủy");

        assertThat(data.attempt().getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
        assertThat(data.payment().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(paymentDomainEventService).recordPaymentResult(
                data.payment(), data.attempt(), data.payment().getPayerId()
        );
    }

    @Test
    void recordsSuccessWhenPaymentCompletesDuringCancellation() {
        TestData data = data(LocalDateTime.now().plusMinutes(5));
        when(paymentAttemptRepository.findLatestByPaymentIdAndStatuses(
                data.payment().getPaymentId(),
                EnumSet.of(PaymentAttemptStatus.REDIRECT_READY, PaymentAttemptStatus.PENDING)
        )).thenReturn(Optional.of(data.attempt()));
        when(paymentStatusProviderPort.cancel(data.paymentLinkId(), "Người dùng hủy"))
                .thenReturn(result(
                        data, ProviderPaymentStatusResult.Status.PAID, 30_000L, null, null
                ));

        service.cancel(data.payment().getPaymentId(), data.payment().getPayerId(), "Người dùng hủy");

        assertThat(data.attempt().getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
        assertThat(data.payment().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    private TestData data(LocalDateTime expiresAt) {
        UUID attemptId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String paymentLinkId = "payos-link-" + UUID.randomUUID();
        PaymentAttempt attempt = PaymentAttempt.builder()
                .attemptId(attemptId)
                .paymentId(paymentId)
                .providerTransactionId(paymentLinkId)
                .provider(PaymentProvider.PAYOS)
                .method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentAttemptStatus.PENDING)
                .build();
        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .payerId(UUID.randomUUID())
                .amount(30_000D)
                .status(PaymentStatus.PENDING)
                .expiresAt(expiresAt)
                .build();
        lenient().when(paymentAttemptRepository.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        return new TestData(attempt, payment, paymentLinkId);
    }

    private ProviderPaymentStatusResult result(
            TestData data,
            ProviderPaymentStatusResult.Status status,
            Long amountPaid,
            String failureCode,
            String failureReason
    ) {
        return new ProviderPaymentStatusResult(
                data.paymentLinkId(),
                PaymentAttemptServiceImpl.providerOrderCode(data.attempt().getAttemptId()),
                30_000L,
                amountPaid,
                status,
                failureCode,
                failureReason
        );
    }

    private record TestData(PaymentAttempt attempt, Payment payment, String paymentLinkId) {
    }
}
