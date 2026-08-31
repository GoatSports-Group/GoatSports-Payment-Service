package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.attempt.CreatePaymentAttemptCommand;
import iuh.fit.paymentservice.application.dto.response.attempt.ProviderCheckoutResult;
import iuh.fit.paymentservice.application.dto.response.webhook.ProviderTransactionStatus;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.application.mapper.PaymentAttemptMapper;
import iuh.fit.paymentservice.application.port.client.PaymentCheckoutPort;
import iuh.fit.paymentservice.application.port.client.ProviderTransactionQueryPort;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.service.ProviderPaymentResultService;
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
    private ProviderTransactionQueryPort transactionQueryPort;

    @Mock
    private ProviderPaymentResultService providerPaymentResultService;

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
        when(paymentCheckoutPort.createCheckout(any())).thenThrow(new PaymentProviderException(
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

    @Test
    void expiredActiveMomoAttemptIsClosedBeforeCreatingFreshCheckout() {
        UUID paymentId = UUID.randomUUID();
        UUID payerId = UUID.randomUUID();
        UUID expiredAttemptId = UUID.randomUUID();
        UUID freshAttemptId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .payerId(payerId)
                .amount(100_000D)
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .build();
        PaymentAttempt active = PaymentAttempt.builder()
                .attemptId(expiredAttemptId)
                .paymentId(paymentId)
                .method(PaymentMethod.E_WALLET)
                .status(PaymentAttemptStatus.PENDING)
                .build();
        PaymentAttempt expired = PaymentAttempt.builder()
                .attemptId(expiredAttemptId)
                .paymentId(paymentId)
                .method(PaymentMethod.E_WALLET)
                .status(PaymentAttemptStatus.EXPIRED)
                .build();
        CreatePaymentAttemptCommand command = command(paymentId, payerId);

        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findLatestByPaymentIdAndStatuses(any(), anyCollection()))
                .thenReturn(Optional.of(active));
        when(transactionQueryPort.query(any())).thenReturn(ProviderTransactionStatus.builder()
                .amount(100_000L)
                .resultCode(1005)
                .message("Expired QR Code")
                .build());
        when(paymentAttemptRepository.findById(expiredAttemptId)).thenReturn(Optional.of(expired));
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(invocation -> {
            PaymentAttempt attempt = invocation.getArgument(0);
            if (attempt.getAttemptId() == null) {
                attempt.setAttemptId(freshAttemptId);
            }
            return attempt;
        });
        when(paymentCheckoutPort.createCheckout(any())).thenReturn(new ProviderCheckoutResult(
                "https://test-payment.momo.vn/pay/new",
                "momo://new",
                "momo-direct-qr"
        ));

        paymentAttemptService.createPaymentAttempt(command);

        verify(providerPaymentResultService).apply(any());
        verify(paymentCheckoutPort).createCheckout(any());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void stillPendingMomoAttemptIsReusedAfterProviderStatusCheck() {
        UUID paymentId = UUID.randomUUID();
        UUID payerId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .payerId(payerId)
                .amount(100_000D)
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .build();
        PaymentAttempt active = PaymentAttempt.builder()
                .attemptId(UUID.randomUUID())
                .paymentId(paymentId)
                .method(PaymentMethod.E_WALLET)
                .status(PaymentAttemptStatus.PENDING)
                .build();

        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findLatestByPaymentIdAndStatuses(any(), anyCollection()))
                .thenReturn(Optional.of(active));
        when(transactionQueryPort.query(any())).thenReturn(ProviderTransactionStatus.builder()
                .amount(100_000L)
                .resultCode(1000)
                .message("Transaction is being processed")
                .build());
        when(paymentAttemptRepository.findById(active.getAttemptId())).thenReturn(Optional.of(active));

        paymentAttemptService.createPaymentAttempt(command(paymentId, payerId));

        verify(providerPaymentResultService).apply(any());
        verify(paymentCheckoutPort, never()).createCheckout(any());
    }

    @Test
    void recoversLegacyDirectQrFailureWithTheSameProviderAttemptId() {
        UUID paymentId = UUID.randomUUID();
        UUID payerId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .payerId(payerId)
                .amount(100_000D)
                .currency("VND")
                .status(PaymentStatus.CREATED)
                .build();
        PaymentAttempt failed = PaymentAttempt.builder()
                .attemptId(attemptId)
                .paymentId(paymentId)
                .method(PaymentMethod.E_WALLET)
                .status(PaymentAttemptStatus.FAILED)
                .failureCode("MOMO_DIRECT_QR_UNAVAILABLE")
                .build();

        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository.findLatestByPaymentIdAndStatuses(any(), anyCollection()))
                .thenReturn(Optional.empty(), Optional.of(failed));
        when(paymentCheckoutPort.createCheckout(any())).thenReturn(new ProviderCheckoutResult(
                "https://test-payment.momo.vn/pay/recovered", null, null
        ));
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentAttemptService.createPaymentAttempt(command(paymentId, payerId));

        ArgumentCaptor<iuh.fit.paymentservice.application.dto.request.webhook.ProviderCheckoutCommand> checkoutCaptor =
                ArgumentCaptor.forClass(iuh.fit.paymentservice.application.dto.request.webhook.ProviderCheckoutCommand.class);
        verify(paymentCheckoutPort).createCheckout(checkoutCaptor.capture());
        assertThat(checkoutCaptor.getValue().getAttemptId()).isEqualTo(attemptId);
        assertThat(failed.getStatus()).isEqualTo(PaymentAttemptStatus.REDIRECT_READY);
        assertThat(failed.getCheckoutUrl()).contains("recovered");
        assertThat(failed.getFailureCode()).isNull();
        assertThat(failed.getCompletedAt()).isNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    private CreatePaymentAttemptCommand command(UUID paymentId, UUID payerId) {
        return CreatePaymentAttemptCommand.builder()
                .paymentId(paymentId)
                .payerId(payerId)
                .method(PaymentMethod.E_WALLET)
                .locale("vn")
                .ipAddress("203.0.113.10")
                .build();
    }
}
