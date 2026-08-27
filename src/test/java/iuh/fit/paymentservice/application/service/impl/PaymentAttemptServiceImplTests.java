package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.attempt.CreatePaymentAttemptCommand;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.application.mapper.PaymentAttemptMapper;
import iuh.fit.paymentservice.application.port.client.PaymentCheckoutPort;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentAttemptServiceImplTests {
    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private PaymentCheckoutPort paymentCheckoutPort;

    @Mock
    private PaymentAttemptMapper paymentAttemptMapper;

    @InjectMocks
    private PaymentAttemptServiceImpl paymentAttemptService;

    @Test
    void providerFailureIsPersistedOnAttemptAndDoesNotMovePaymentToPending() {
        UUID paymentId = UUID.randomUUID();
        UUID payerId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .payerId(payerId)
                .amount(100_000D)
                .currency("VND")
                .status(PaymentStatus.CREATED)
                .build();
        CreatePaymentAttemptCommand command = CreatePaymentAttemptCommand.builder()
                .paymentId(paymentId)
                .payerId(payerId)
                .method(PaymentMethod.E_WALLET)
                .locale("vn")
                .ipAddress("203.0.113.10")
                .build();

        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findLatestByPaymentIdAndStatuses(any(), anyCollection()))
                .thenReturn(Optional.empty());
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(invocation -> {
            PaymentAttempt attempt = invocation.getArgument(0);
            if (attempt.getAttemptId() == null) {
                attempt.setAttemptId(UUID.randomUUID());
            }
            return attempt;
        });
        when(paymentCheckoutPort.createCheckoutUrl(any())).thenThrow(new PaymentProviderException(
                "MOMO_SIGNATURE_ERROR",
                "Không thể ký yêu cầu thanh toán MoMo"
        ));

        assertThatThrownBy(() -> paymentAttemptService.createPaymentAttempt(command))
                .isInstanceOf(PaymentProviderException.class)
                .hasMessage("Không thể ký yêu cầu thanh toán MoMo");

        ArgumentCaptor<PaymentAttempt> attemptCaptor = ArgumentCaptor.forClass(PaymentAttempt.class);
        verify(paymentAttemptRepository, org.mockito.Mockito.times(2)).save(attemptCaptor.capture());
        PaymentAttempt failedAttempt = attemptCaptor.getAllValues().getLast();
        assertThat(failedAttempt.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
        assertThat(failedAttempt.getFailureCode()).isEqualTo("MOMO_SIGNATURE_ERROR");
        assertThat(failedAttempt.getFailureReason()).isEqualTo("Không thể ký yêu cầu thanh toán MoMo");
        assertThat(failedAttempt.getCompletedAt()).isNotNull();
        verify(paymentRepository, never()).save(any());
    }
}
