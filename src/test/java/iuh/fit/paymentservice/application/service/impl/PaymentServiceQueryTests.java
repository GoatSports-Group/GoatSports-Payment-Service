package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.response.payment.PaymentResponseCommand;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.mapper.PaymentMapper;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.service.PaymentCreationTransactionService;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceQueryTests {
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentAttemptRepository paymentAttemptRepository;
    @Mock private PaymentMapper paymentMapper;
    @Mock private PaymentCreationTransactionService paymentCreationTransactionService;

    @Test
    void returnsPaymentSourceOfTruthForBookingReferences() {
        UUID bookingId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .paymentId(UUID.randomUUID()).referenceType(PaymentReferenceType.BOOKING)
                .referenceId(bookingId).purpose(PaymentPurpose.BOOKING_DEPOSIT)
                .amount(90_000.0).currency("VND").status(PaymentStatus.SUCCEEDED).build();
        PaymentResponseCommand response = PaymentResponseCommand.builder()
                .paymentId(payment.getPaymentId()).referenceType(payment.getReferenceType())
                .referenceId(bookingId).purpose(payment.getPurpose()).amount(payment.getAmount())
                .currency("VND").status(PaymentStatus.SUCCEEDED).build();
        when(paymentRepository.findByReferences(PaymentReferenceType.BOOKING, Set.of(bookingId)))
                .thenReturn(List.of(payment));
        when(paymentMapper.toPaymentResponseCommand(payment)).thenReturn(response);
        when(paymentAttemptRepository.findLatestByPaymentIds(List.of(payment.getPaymentId())))
                .thenReturn(List.of(PaymentAttempt.builder()
                        .paymentId(payment.getPaymentId())
                        .provider(PaymentProvider.PAYOS)
                        .method(PaymentMethod.BANK_TRANSFER)
                        .providerTransactionId("payos-transaction-123")
                        .build()));

        var service = new PaymentServiceImpl(
                paymentRepository, paymentAttemptRepository, paymentMapper, paymentCreationTransactionService
        );
        var result = service.getPaymentsByReferences(
                PaymentReferenceType.BOOKING, Set.of(bookingId)
        );

        assertThat(result).containsExactly(response);
        assertThat(result.getFirst().getProvider()).isEqualTo(PaymentProvider.PAYOS);
        assertThat(result.getFirst().getMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(result.getFirst().getProviderTransactionId()).isEqualTo("payos-transaction-123");
        verify(paymentRepository).findByReferences(PaymentReferenceType.BOOKING, Set.of(bookingId));
    }

    @Test
    void rejectsEmptyInternalReferenceQuery() {
        var service = new PaymentServiceImpl(
                paymentRepository, paymentAttemptRepository, paymentMapper, paymentCreationTransactionService
        );

        assertThatThrownBy(() -> service.getPaymentsByReferences(
                PaymentReferenceType.BOOKING, Set.of()
        )).isInstanceOf(InvalidPaymentException.class)
                .hasMessageContaining("reference ID");
    }
}
