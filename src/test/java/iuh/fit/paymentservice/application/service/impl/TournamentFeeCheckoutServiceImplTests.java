package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.response.attempt.CreatePaymentAttemptResult;
import iuh.fit.paymentservice.application.dto.response.attempt.PaymentAttemptResponseCommand;
import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.service.BookingRemainingPaymentRetryService;
import iuh.fit.paymentservice.application.service.PaymentAttemptService;
import iuh.fit.paymentservice.application.service.PaymentService;
import iuh.fit.paymentservice.application.service.TournamentFeeCheckoutService.Command;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TournamentFeeCheckoutServiceImplTests {
    @Mock private PaymentService paymentService;
    @Mock private PaymentAttemptService paymentAttemptService;
    @Mock private PaymentRepository paymentRepository;
    @Mock private BookingRemainingPaymentRetryService retryService;

    private final UUID payerId = UUID.randomUUID();
    private final UUID registrationId = UUID.randomUUID();

    private TournamentFeeCheckoutServiceImpl service() {
        return new TournamentFeeCheckoutServiceImpl(paymentService, paymentAttemptService, paymentRepository, retryService);
    }

    private Payment existing(UUID payer, double amount, PaymentStatus status) {
        return Payment.builder().paymentId(UUID.randomUUID()).payerId(payer)
                .referenceType(PaymentReferenceType.TOURNAMENT_REGISTRATION).referenceId(registrationId)
                .purpose(PaymentPurpose.TOURNAMENT_FEE).amount(amount).currency("VND").status(status).build();
    }

    @Test
    void retryReusesPaymentAndIssuesNewCode() {
        LocalDateTime deadline = LocalDateTime.now().plusHours(20);
        Payment payment = existing(payerId, 150_000D, PaymentStatus.EXPIRED);
        Payment prepared = existing(payerId, 150_000D, PaymentStatus.CREATED);
        prepared.setPaymentId(payment.getPaymentId());
        prepared.setExpiresAt(deadline);
        when(paymentRepository.findByReference(PaymentReferenceType.TOURNAMENT_REGISTRATION, registrationId,
                PaymentPurpose.TOURNAMENT_FEE)).thenReturn(Optional.of(payment));
        when(retryService.prepare(payment.getPaymentId(), deadline)).thenReturn(prepared);
        when(paymentAttemptService.createPaymentAttempt(any())).thenReturn(new CreatePaymentAttemptResult(
                PaymentAttemptResponseCommand.builder().paymentId(payment.getPaymentId())
                        .checkoutUrl("https://pay.payos.vn/web/t").qrCodeContent("qr").build(), false));
        when(paymentRepository.findById(payment.getPaymentId())).thenReturn(Optional.of(prepared));

        var result = service().create(new Command(payerId, registrationId, 150_000D, "Lệ phí", "vn", null, deadline));

        assertThat(result.checkoutUrl()).isEqualTo("https://pay.payos.vn/web/t");
        assertThat(result.expiresAt()).isEqualTo(deadline);
        verify(paymentService, never()).createPayment(any());
    }

    @Test
    void anotherPayerCannotReuseTheRegistrationPayment() {
        when(paymentRepository.findByReference(any(), any(), any()))
                .thenReturn(Optional.of(existing(UUID.randomUUID(), 150_000D, PaymentStatus.PENDING)));

        assertThatThrownBy(() -> service().create(new Command(payerId, registrationId, 150_000D, "Lệ phí", "vn",
                null, LocalDateTime.now().plusHours(1))))
                .isInstanceOf(PaymentConflictException.class)
                .hasMessageContaining("không khớp");
        verify(paymentAttemptService, never()).createPaymentAttempt(any());
    }

    @Test
    void checkoutAfterDeadlineIsRefused() {
        assertThatThrownBy(() -> service().create(new Command(payerId, registrationId, 150_000D, "Lệ phí", "vn",
                null, LocalDateTime.now().minusMinutes(1))))
                .isInstanceOf(PaymentConflictException.class)
                .hasMessageContaining("quá hạn");
    }
}
