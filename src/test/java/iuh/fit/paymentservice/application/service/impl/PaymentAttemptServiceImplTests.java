package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.attempt.CreatePaymentAttemptCommand;
import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.application.mapper.PaymentAttemptMapper;
import iuh.fit.paymentservice.application.port.client.PaymentCheckoutPort;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentAttemptServiceImplTests {
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentAttemptRepository paymentAttemptRepository;
    @Mock private PaymentCheckoutPort paymentCheckoutPort;
    @Mock private PaymentAttemptMapper paymentAttemptMapper;
    @InjectMocks private PaymentAttemptServiceImpl paymentAttemptService;

    @Test
    void providerFailureIsPersistedAndPaymentDoesNotMoveToPending() {
        UUID paymentId = UUID.randomUUID();
        UUID payerId = UUID.randomUUID();
        Payment payment = payment(paymentId, payerId, PaymentStatus.CREATED);
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findLatestByPaymentIdAndStatuses(any(), anyCollection()))
                .thenReturn(Optional.empty());
        saveAttemptsWithGeneratedIds();
        when(paymentCheckoutPort.createCheckout(any())).thenThrow(new PaymentProviderException(
                "VIETQR_CONNECTION_ERROR", "Không thể kết nối tới VietQR"
        ));

        assertThatThrownBy(() -> paymentAttemptService.createPaymentAttempt(command(paymentId, payerId)))
                .isInstanceOf(PaymentProviderException.class)
                .hasMessage("Không thể kết nối tới VietQR");

        ArgumentCaptor<PaymentAttempt> captor = ArgumentCaptor.forClass(PaymentAttempt.class);
        verify(paymentAttemptRepository, times(2)).save(captor.capture());
        PaymentAttempt failed = captor.getAllValues().getLast();
        assertThat(failed.getProvider()).isEqualTo(PaymentProvider.VIETQR);
        assertThat(failed.getMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(failed.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
        assertThat(failed.getFailureCode()).isEqualTo("VIETQR_CONNECTION_ERROR");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void activeAttemptWithDifferentMethodBlocksVietQrCreation() {
        UUID paymentId = UUID.randomUUID();
        UUID payerId = UUID.randomUUID();
        Payment payment = payment(paymentId, payerId, PaymentStatus.PENDING);
        PaymentAttempt manual = PaymentAttempt.builder()
                .attemptId(UUID.randomUUID())
                .paymentId(paymentId)
                .provider(PaymentProvider.MANUAL)
                .method(PaymentMethod.CASH)
                .status(PaymentAttemptStatus.PENDING)
                .build();
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findLatestByPaymentIdAndStatuses(any(), anyCollection()))
                .thenReturn(Optional.of(manual));

        assertThatThrownBy(() -> paymentAttemptService.createPaymentAttempt(command(paymentId, payerId)))
                .isInstanceOf(PaymentConflictException.class)
                .hasMessageContaining("attempt");

        verify(paymentCheckoutPort, never()).createCheckout(any());
    }

    @Test
    void activeVietQrAttemptIsReusedWithoutCallingProviderAgain() {
        UUID paymentId = UUID.randomUUID();
        UUID payerId = UUID.randomUUID();
        Payment payment = payment(paymentId, payerId, PaymentStatus.PENDING);
        PaymentAttempt active = PaymentAttempt.builder()
                .attemptId(UUID.randomUUID())
                .paymentId(paymentId)
                .provider(PaymentProvider.VIETQR)
                .method(PaymentMethod.BANK_TRANSFER)
                .qrCodeContent("data:image/png;base64,TEST")
                .status(PaymentAttemptStatus.PENDING)
                .build();
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findLatestByPaymentIdAndStatuses(any(), anyCollection()))
                .thenReturn(Optional.of(active));

        paymentAttemptService.createPaymentAttempt(command(paymentId, payerId));

        verify(paymentCheckoutPort, never()).createCheckout(any());
        verify(paymentAttemptMapper).toPaymentAttemptResponseCommand(active);
    }

    private Payment payment(UUID paymentId, UUID payerId, PaymentStatus status) {
        return Payment.builder()
                .paymentId(paymentId)
                .payerId(payerId)
                .amount(100_000D)
                .currency("VND")
                .status(status)
                .build();
    }

    private CreatePaymentAttemptCommand command(UUID paymentId, UUID payerId) {
        return CreatePaymentAttemptCommand.builder()
                .paymentId(paymentId)
                .payerId(payerId)
                .method(PaymentMethod.BANK_TRANSFER)
                .locale("vn")
                .ipAddress("203.0.113.10")
                .build();
    }

    private void saveAttemptsWithGeneratedIds() {
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(invocation -> {
            PaymentAttempt attempt = invocation.getArgument(0);
            if (attempt.getAttemptId() == null) {
                attempt.setAttemptId(UUID.randomUUID());
            }
            return attempt;
        });
    }
}
