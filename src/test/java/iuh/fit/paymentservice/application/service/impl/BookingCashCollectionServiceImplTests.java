package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.payment.CollectBookingCashCommand;
import iuh.fit.paymentservice.application.service.BookingRemainingPaymentRetryService;
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
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
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
    @Mock private BookingRemainingPaymentRetryService retryService;

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
        when(paymentService.createPayment(ArgumentMatchers.any()))
                .thenReturn(new CreatePaymentResult(created, false));
        when(cashCollectionService.collect(ArgumentMatchers.any()))
                .thenReturn(new CreatePaymentAttemptResult(null, false));
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentMapper.toPaymentResponseCommand(payment)).thenReturn(succeeded);

        BookingCashCollectionServiceImpl service = new BookingCashCollectionServiceImpl(
                paymentService, cashCollectionService, paymentRepository, paymentMapper, retryService
        );
        PaymentResponseCommand result = service.collect(new CollectBookingCashCommand(
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

    @Test
    void reopensCancelledPayosPaymentBeforeCollectingCash() {
        UUID bookingId = UUID.randomUUID();
        UUID payerId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Payment cancelled = Payment.builder().paymentId(paymentId).payerId(payerId).amount(140_000.0)
                .currency("VND").status(PaymentStatus.CANCELLED).build();
        when(paymentRepository.findByReference(
                PaymentReferenceType.BOOKING, bookingId,
                PaymentPurpose.BOOKING_REMAINING
        )).thenReturn(Optional.of(cancelled));
        when(cashCollectionService.collect(ArgumentMatchers.any()))
                .thenReturn(new CreatePaymentAttemptResult(null, false));
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(cancelled));
        when(paymentMapper.toPaymentResponseCommand(cancelled)).thenReturn(PaymentResponseCommand.builder()
                .paymentId(paymentId).status(PaymentStatus.SUCCEEDED).build());

        new BookingCashCollectionServiceImpl(paymentService, cashCollectionService, paymentRepository, paymentMapper, retryService)
                .collect(new CollectBookingCashCommand(payerId, bookingId, 140_000.0, "VND", UUID.randomUUID()));

        // Ma payOS da huy: mo lai payment truoc (giong luc tao lai ma), roi moi ghi nhan tien mat.
        InOrder order = Mockito.inOrder(retryService, cashCollectionService);
        order.verify(retryService).prepare(paymentId, null);
        order.verify(cashCollectionService).collect(ArgumentMatchers.any());
    }
}
