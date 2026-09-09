package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingRemainingPaymentRetryServiceImplTests {
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentAttemptRepository paymentAttemptRepository;
    @InjectMocks private BookingRemainingPaymentRetryServiceImpl retryService;

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"FAILED", "CANCELLED", "EXPIRED"})
    void reopensTerminalBookingRemainingPayment(PaymentStatus status) {
        UUID paymentId = UUID.randomUUID();
        LocalDateTime newExpiresAt = LocalDateTime.now().plusMinutes(15);
        Payment payment = bookingRemainingPayment(paymentId, status, LocalDateTime.now().minusMinutes(1));
        payment.setPaidAt(LocalDateTime.now().minusMinutes(2));
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        if (status == PaymentStatus.FAILED) {
            when(paymentAttemptRepository.findLatestByPaymentIds(List.of(paymentId))).thenReturn(List.of());
        }
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = retryService.prepare(paymentId, newExpiresAt);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(result.getExpiresAt()).isEqualTo(newExpiresAt);
        assertThat(result.getPaidAt()).isNull();
        verify(paymentRepository).save(payment);
    }

    @Test
    void renewsExpiredCreatedPaymentAfterCheckoutProviderFailure() {
        UUID paymentId = UUID.randomUUID();
        LocalDateTime newExpiresAt = LocalDateTime.now().plusMinutes(15);
        Payment payment = bookingRemainingPayment(
                paymentId, PaymentStatus.CREATED, LocalDateTime.now().minusSeconds(1)
        );
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = retryService.prepare(paymentId, newExpiresAt);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(result.getExpiresAt()).isEqualTo(newExpiresAt);
        verify(paymentRepository).save(payment);
    }

    @Test
    void keepsActivePendingPaymentAndItsOriginalExpiry() {
        UUID paymentId = UUID.randomUUID();
        LocalDateTime originalExpiresAt = LocalDateTime.now().plusMinutes(5);
        Payment payment = bookingRemainingPayment(paymentId, PaymentStatus.PENDING, originalExpiresAt);
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        Payment result = retryService.prepare(paymentId, LocalDateTime.now().plusMinutes(15));

        assertThat(result.getExpiresAt()).isEqualTo(originalExpiresAt);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void blocksRetryWhenPreviousAttemptWasUnderpaid() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = bookingRemainingPayment(
                paymentId, PaymentStatus.FAILED, LocalDateTime.now().minusMinutes(1)
        );
        PaymentAttempt attempt = PaymentAttempt.builder()
                .paymentId(paymentId)
                .failureCode("PAYOS_UNDERPAID")
                .build();
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findLatestByPaymentIds(List.of(paymentId)))
                .thenReturn(List.of(attempt));

        assertThatThrownBy(() -> retryService.prepare(paymentId, LocalDateTime.now().plusMinutes(15)))
                .isInstanceOf(PaymentConflictException.class)
                .hasMessageContaining("kiểm tra thủ công");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void doesNotAllowRetryForBookingDeposit() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = bookingRemainingPayment(
                paymentId, PaymentStatus.EXPIRED, LocalDateTime.now().minusMinutes(1)
        );
        payment.setPurpose(PaymentPurpose.BOOKING_DEPOSIT);
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> retryService.prepare(paymentId, LocalDateTime.now().plusMinutes(15)))
                .isInstanceOf(PaymentConflictException.class)
                .hasMessageContaining("phần còn lại");

        verify(paymentRepository, never()).save(any());
    }

    private Payment bookingRemainingPayment(
            UUID paymentId,
            PaymentStatus status,
            LocalDateTime expiresAt
    ) {
        return Payment.builder()
                .paymentId(paymentId)
                .payerId(UUID.randomUUID())
                .referenceType(PaymentReferenceType.BOOKING)
                .referenceId(UUID.randomUUID())
                .purpose(PaymentPurpose.BOOKING_REMAINING)
                .amount(70_000D)
                .currency("VND")
                .status(status)
                .expiresAt(expiresAt)
                .build();
    }
}
