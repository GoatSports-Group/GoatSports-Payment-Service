package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.attempt.CreatePaymentAttemptCommand;
import iuh.fit.paymentservice.application.dto.request.payment.CreateBookingCheckoutCommand;
import iuh.fit.paymentservice.application.dto.response.attempt.CreatePaymentAttemptResult;
import iuh.fit.paymentservice.application.dto.response.attempt.PaymentAttemptResponseCommand;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.service.BookingRemainingPaymentRetryService;
import iuh.fit.paymentservice.application.service.PaymentAttemptService;
import iuh.fit.paymentservice.application.service.PaymentService;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingCheckoutServiceImplTests {
    @Mock private PaymentService paymentService;
    @Mock private PaymentAttemptService paymentAttemptService;
    @Mock private PaymentRepository paymentRepository;
    @Mock private BookingRemainingPaymentRetryService retryService;

    @Test
    void retriesTerminalRemainingPaymentWithNewExpiryAndAttempt() {
        UUID paymentId = UUID.randomUUID();
        UUID payerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);
        Payment existing = payment(paymentId, payerId, bookingId, PaymentStatus.EXPIRED);
        Payment prepared = payment(paymentId, payerId, bookingId, PaymentStatus.CREATED);
        prepared.setExpiresAt(expiresAt);
        Payment pending = payment(paymentId, payerId, bookingId, PaymentStatus.PENDING);
        pending.setExpiresAt(expiresAt);
        PaymentAttemptResponseCommand attempt = PaymentAttemptResponseCommand.builder()
                .paymentId(paymentId)
                .checkoutUrl("https://pay.payos.vn/web/test")
                .deeplink("payos://test")
                .qrCodeContent("qr-content")
                .build();
        when(paymentRepository.findByReference(
                PaymentReferenceType.BOOKING, bookingId, PaymentPurpose.BOOKING_REMAINING
        )).thenReturn(Optional.of(existing));
        when(retryService.prepare(paymentId, expiresAt)).thenReturn(prepared);
        when(paymentAttemptService.createPaymentAttempt(any()))
                .thenReturn(new CreatePaymentAttemptResult(attempt, false));
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(pending));
        BookingCheckoutServiceImpl service = new BookingCheckoutServiceImpl(
                paymentService, paymentAttemptService, paymentRepository, retryService
        );

        var result = service.create(new CreateBookingCheckoutCommand(
                payerId, bookingId, 70_000D, "VND", "vn", "203.0.113.10", expiresAt
        ));

        assertThat(result.paymentId()).isEqualTo(paymentId);
        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING.name());
        assertThat(result.checkoutUrl()).isEqualTo("https://pay.payos.vn/web/test");
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
        verify(retryService).prepare(paymentId, expiresAt);
        ArgumentCaptor<CreatePaymentAttemptCommand> command =
                ArgumentCaptor.forClass(CreatePaymentAttemptCommand.class);
        verify(paymentAttemptService).createPaymentAttempt(command.capture());
        assertThat(command.getValue().getPaymentId()).isEqualTo(paymentId);
        assertThat(command.getValue().getPayerId()).isEqualTo(payerId);
    }

    private Payment payment(
            UUID paymentId,
            UUID payerId,
            UUID bookingId,
            PaymentStatus status
    ) {
        return Payment.builder()
                .paymentId(paymentId)
                .payerId(payerId)
                .referenceType(PaymentReferenceType.BOOKING)
                .referenceId(bookingId)
                .purpose(PaymentPurpose.BOOKING_REMAINING)
                .amount(70_000D)
                .currency("VND")
                .status(status)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
    }
}
