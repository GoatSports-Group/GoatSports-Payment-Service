package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.webhook.PayOsWebhookCommand;
import iuh.fit.paymentservice.application.dto.response.webhook.VerifiedPayOsWebhook;
import iuh.fit.paymentservice.application.port.client.PayOsWebhookVerificationPort;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.port.persistence.ProviderWebhookEventRepository;
import iuh.fit.paymentservice.application.service.PaymentDomainEventService;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.entity.ProviderWebhookEvent;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.WebhookProcessingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayOsWebhookServiceImplTests {
    private final PayOsWebhookVerificationPort verificationPort = mock(PayOsWebhookVerificationPort.class);
    private final ProviderWebhookEventRepository eventRepository = mock(ProviderWebhookEventRepository.class);
    private final PaymentAttemptRepository attemptRepository = mock(PaymentAttemptRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentDomainEventService eventService = mock(PaymentDomainEventService.class);
    private PayOsWebhookServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PayOsWebhookServiceImpl(
                verificationPort, eventRepository, attemptRepository, paymentRepository, eventService
        );
        when(eventRepository.findByProviderAndProviderEventId(any(), any())).thenReturn(Optional.empty());
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationPort.payloadHash(any())).thenReturn("a".repeat(64));
    }

    @Test
    void validWebhookMarksAttemptAndPaymentSucceededAndPublishesEvent() {
        UUID attemptId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID payerId = UUID.randomUUID();
        PayOsWebhookCommand command = command();
        when(verificationPort.verify(command)).thenReturn(verified(
                PaymentAttemptServiceImpl.providerOrderCode(attemptId)
        ));

        PaymentAttempt attempt = PaymentAttempt.builder()
                .attemptId(attemptId)
                .paymentId(paymentId)
                .provider(PaymentProvider.PAYOS)
                .method(PaymentMethod.BANK_TRANSFER)
                .providerTransactionId("payment-link-1")
                .status(PaymentAttemptStatus.PENDING)
                .build();
        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .payerId(payerId)
                .amount(100_000D)
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .build();
        when(attemptRepository.findByProviderAndProviderTransactionIdForUpdate(
                PaymentProvider.PAYOS, "payment-link-1"
        )).thenReturn(Optional.of(attempt));
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(attemptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.process(command);

        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getPaidAt()).isNotNull();
        verify(eventService).recordPaymentResult(payment, attempt, payerId);
        ArgumentCaptor<ProviderWebhookEvent> eventCaptor = ArgumentCaptor.forClass(ProviderWebhookEvent.class);
        verify(eventRepository, Mockito.atLeast(2)).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo(WebhookProcessingStatus.PROCESSED);
    }

    @Test
    void lateMoneyForExpiredPaymentIsRecordedSoTheOwnerServiceCanRefundIt() {
        UUID attemptId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID payerId = UUID.randomUUID();
        PayOsWebhookCommand command = command();
        when(verificationPort.verify(command)).thenReturn(verified(PaymentAttemptServiceImpl.providerOrderCode(attemptId)));
        PaymentAttempt attempt = PaymentAttempt.builder().attemptId(attemptId).paymentId(paymentId)
                .provider(PaymentProvider.PAYOS).method(PaymentMethod.BANK_TRANSFER)
                .providerTransactionId("payment-link-1").status(PaymentAttemptStatus.EXPIRED).build();
        Payment payment = Payment.builder().paymentId(paymentId).payerId(payerId).amount(100_000D).currency("VND")
                .status(PaymentStatus.EXPIRED).build();
        when(attemptRepository.findByProviderAndProviderTransactionIdForUpdate(PaymentProvider.PAYOS, "payment-link-1"))
                .thenReturn(Optional.of(attempt));
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(attemptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.process(command);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(eventService).recordPaymentResult(payment, attempt, payerId);
    }

    @Test
    void registrationSampleWithoutMatchingAttemptIsAcknowledgedAndIgnored() {
        PayOsWebhookCommand command = command();
        when(verificationPort.verify(command)).thenReturn(verified(123L));
        when(attemptRepository.findByProviderAndProviderTransactionIdForUpdate(
                PaymentProvider.PAYOS, "payment-link-1"
        )).thenReturn(Optional.empty());

        service.process(command);

        ArgumentCaptor<ProviderWebhookEvent> eventCaptor = ArgumentCaptor.forClass(ProviderWebhookEvent.class);
        verify(eventRepository, Mockito.atLeast(2)).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo(WebhookProcessingStatus.IGNORED);
        verify(paymentRepository, never()).save(any());
        verify(eventService, never()).recordPaymentResult(any(), any(), any());
    }

    private PayOsWebhookCommand command() {
        return new PayOsWebhookCommand(
                "00", "success", true,
                new PayOsWebhookCommand.Data(
                        123L, 100_000L, "GS123", "123456",
                        "bank-reference-1", "2026-09-01 02:00:00", "VND",
                        "payment-link-1", "00", "success",
                        null, null, null, null, null, null
                ),
                "signed-payload"
        );
    }

    private VerifiedPayOsWebhook verified(long orderCode) {
        return new VerifiedPayOsWebhook(
                orderCode, 100_000L, "GS123", "bank-reference-1",
                "2026-09-01 02:00:00", "VND", "payment-link-1", "00", "success"
        );
    }
}
