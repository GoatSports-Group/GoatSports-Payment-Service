package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.payment.ProviderPaymentResultCommand;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.service.PaymentDomainEventService;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderPaymentResultServiceImplTests {
    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentDomainEventService paymentDomainEventService;

    @InjectMocks
    private ProviderPaymentResultServiceImpl service;

    @Test
    void momoResult1005MarksAttemptExpiredAndAllowsPaymentRetry() {
        UUID attemptId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentAttempt attempt = PaymentAttempt.builder()
                .attemptId(attemptId)
                .paymentId(paymentId)
                .provider(PaymentProvider.MOMO)
                .status(PaymentAttemptStatus.PENDING)
                .build();
        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .payerId(UUID.randomUUID())
                .amount(100_000D)
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .build();
        when(paymentAttemptRepository.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        service.apply(ProviderPaymentResultCommand.builder()
                .attemptId(attemptId)
                .amount(100_000L)
                .resultCode(1005)
                .message("Expired QR Code")
                .build());

        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.EXPIRED);
        assertThat(attempt.getFailureCode()).isEqualTo("MOMO_RESULT_1005");
        assertThat(attempt.getCompletedAt()).isNotNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }
}
