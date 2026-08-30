package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.payment.CollectBookingCashCommand;
import iuh.fit.paymentservice.application.dto.request.payment.CollectCashPaymentCommand;
import iuh.fit.paymentservice.application.dto.request.payment.CreatePaymentCommand;
import iuh.fit.paymentservice.application.dto.response.attempt.CreatePaymentAttemptResult;
import iuh.fit.paymentservice.application.dto.response.payment.CreatePaymentResult;
import iuh.fit.paymentservice.application.dto.response.payment.PaymentResponseCommand;
import iuh.fit.paymentservice.application.mapper.PaymentMapper;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.service.CashCollectionService;
import iuh.fit.paymentservice.application.service.PaymentService;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingCashCollectionServiceImplTests {
    @Mock private PaymentService paymentService;
    @Mock private CashCollectionService cashCollectionService;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentMapper paymentMapper;

    @Test
    void createsIdempotentRemainingPaymentAndCollectsItForAuthenticatedOwner() {
        UUID bookingId = UUID.randomUUID();
        UUID payerId = UUID.randomUUID();
        UUID collectorId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentResponseCommand created = PaymentResponseCommand.builder()
                .paymentId(paymentId).status(PaymentStatus.CREATED).build();
        Payment payment = Payment.builder().paymentId(paymentId).status(PaymentStatus.SUCCEEDED).build();
        PaymentResponseCommand succeeded = PaymentResponseCommand.builder()
                .paymentId(paymentId).status(PaymentStatus.SUCCEEDED).build();
        when(paymentService.createPayment(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new CreatePaymentResult(created, false));
        when(cashCollectionService.collect(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new CreatePaymentAttemptResult(null, false));
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentMapper.toPaymentResponseCommand(payment)).thenReturn(succeeded);

        var service = new BookingCashCollectionServiceImpl(
                paymentService, cashCollectionService, paymentRepository, paymentMapper
        );
        var result = service.collect(new CollectBookingCashCommand(
                payerId, bookingId, 140_000.0, "VND", collectorId
        ));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        ArgumentCaptor<CreatePaymentCommand> create = ArgumentCaptor.forClass(CreatePaymentCommand.class);
        verify(paymentService).createPayment(create.capture());
        assertThat(create.getValue().getIdempotencyKey()).isEqualTo("booking-remaining:" + bookingId);
        ArgumentCaptor<CollectCashPaymentCommand> cash = ArgumentCaptor.forClass(CollectCashPaymentCommand.class);
        verify(cashCollectionService).collect(cash.capture());
        assertThat(cash.getValue().getCollectorId()).isEqualTo(collectorId);
    }
}
