package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.payout.CreatePayoutCommand;
import iuh.fit.paymentservice.application.dto.response.payout.ProviderPayoutResult;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.application.port.client.PayoutProviderPort;
import iuh.fit.paymentservice.application.port.persistence.BankAccountRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.port.persistence.PayoutRepository;
import iuh.fit.paymentservice.application.port.persistence.RefundRepository;
import iuh.fit.paymentservice.application.port.security.BankAccountProtector;
import iuh.fit.paymentservice.application.service.PaymentDomainEventService;
import iuh.fit.paymentservice.domain.entity.BankAccount;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.Payout;
import iuh.fit.paymentservice.domain.entity.Refund;
import iuh.fit.paymentservice.domain.enums.BankAccountStatus;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;
import iuh.fit.paymentservice.domain.enums.RefundStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundDisbursementServiceImplTests {
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private BankAccountRepository bankAccountRepository;
    @Mock
    private PayoutRepository payoutRepository;
    @Mock
    private BankAccountProtector bankAccountProtector;
    @Mock
    private PayoutProviderPort payoutProviderPort;
    @Mock
    private PaymentDomainEventService paymentDomainEventService;
    @InjectMocks
    private RefundDisbursementServiceImpl service;

    @Test
    void reportsProviderFailureInsteadOfClaimingBankAccountIsMissing() {
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID payerId = UUID.randomUUID();
        Refund refund = Refund.builder()
                .refundId(refundId)
                .paymentId(paymentId)
                .requestedBy(payerId)
                .amount(30_000D)
                .status(RefundStatus.AWAITING_BANK_ACCOUNT)
                .build();
        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .payerId(payerId)
                .amount(30_000D)
                .status(PaymentStatus.SUCCEEDED)
                .build();
        BankAccount account = BankAccount.builder()
                .bankAccountId(UUID.randomUUID())
                .payeeId(payerId)
                .bankBin("970415")
                .accountNumberEncrypted("encrypted-account")
                .accountNumberLast4("6077")
                .accountName("NGUYEN VAN A")
                .status(BankAccountStatus.PENDING_VERIFICATION)
                .isDefault(true)
                .build();

        when(refundRepository.findByIdForUpdate(refundId)).thenReturn(Optional.of(refund));
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(bankAccountRepository.findDefaultByPayeeIdAndStatuses(eq(payerId), anyCollection()))
                .thenReturn(Optional.of(account));
        when(payoutRepository.findByRefundId(refundId)).thenReturn(Optional.empty());
        when(payoutRepository.save(any(Payout.class))).thenAnswer(invocation -> {
            Payout payout = invocation.getArgument(0);
            if (payout.getPayoutId() == null) payout.setPayoutId(UUID.randomUUID());
            return payout;
        });
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bankAccountProtector.decrypt("encrypted-account")).thenReturn("0123456077");
        when(payoutProviderPort.create(any())).thenThrow(new PaymentProviderException(
                "PAYOS_PAYOUT_403",
                "Địa chỉ IP không được phép truy cập hệ thống"
        ));

        Refund result = service.claim(refundId, payerId);

        assertThat(result.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(result.getFailureReason()).isEqualTo("Địa chỉ IP không được phép truy cập hệ thống");
        verify(paymentDomainEventService).recordRefundResult(eq(result), eq(PaymentStatus.SUCCEEDED), any());
    }

    @Test
    void retriesAFailedPayoutWithTheNewBankAccountAndANewIdempotencyKey() {
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID payerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Refund refund = Refund.builder()
                .refundId(refundId)
                .paymentId(paymentId)
                .requestedBy(payerId)
                .amount(30_000D)
                .status(RefundStatus.AWAITING_BANK_ACCOUNT)
                .build();
        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .payerId(payerId)
                .amount(30_000D)
                .status(PaymentStatus.SUCCEEDED)
                .build();
        BankAccount account = BankAccount.builder()
                .bankAccountId(accountId)
                .payeeId(payerId)
                .bankBin("970436")
                .accountNumberEncrypted("new-encrypted-account")
                .accountNumberLast4("6789")
                .accountName("NGUYEN VAN B")
                .status(BankAccountStatus.PENDING_VERIFICATION)
                .isDefault(true)
                .build();
        Payout failedPayout = Payout.builder()
                .payoutId(UUID.randomUUID())
                .refundId(refundId)
                .bankAccountId(UUID.randomUUID())
                .referenceId("old-reference")
                .idempotencyKey("old-idempotency-key")
                .status(PayoutStatus.FAILED)
                .failureReason("Không tìm thấy tài khoản nhận chi")
                .build();

        when(refundRepository.findByIdForUpdate(refundId)).thenReturn(Optional.of(refund));
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(bankAccountRepository.findDefaultByPayeeIdAndStatuses(eq(payerId), anyCollection()))
                .thenReturn(Optional.of(account));
        when(bankAccountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        when(payoutRepository.findByRefundId(refundId)).thenReturn(Optional.of(failedPayout));
        when(payoutRepository.save(any(Payout.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bankAccountProtector.decrypt("new-encrypted-account")).thenReturn("0123456789");
        when(payoutProviderPort.create(any())).thenReturn(new ProviderPayoutResult(
                "new-provider-payout", "NGUYEN VAN B", PayoutStatus.PROCESSING, null, null
        ));

        Refund result = service.claim(refundId, payerId);

        ArgumentCaptor<CreatePayoutCommand> command = ArgumentCaptor.forClass(CreatePayoutCommand.class);
        verify(payoutProviderPort).create(command.capture());
        assertThat(command.getValue().idempotencyKey()).isNotEqualTo("old-idempotency-key");
        assertThat(command.getValue().bankBin()).isEqualTo("970436");
        assertThat(command.getValue().accountNumber()).isEqualTo("0123456789");
        assertThat(failedPayout.getBankAccountId()).isEqualTo(accountId);
        assertThat(result.getStatus()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(account.getStatus()).isEqualTo(BankAccountStatus.PENDING_VERIFICATION);
    }

    @Test
    void rejectsTheBankAccountWhenReconciliationReportsAnInvalidDestination() {
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Refund refund = Refund.builder()
                .refundId(refundId)
                .paymentId(paymentId)
                .amount(30_000D)
                .status(RefundStatus.PROCESSING)
                .build();
        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .amount(30_000D)
                .status(PaymentStatus.SUCCEEDED)
                .build();
        BankAccount account = BankAccount.builder()
                .bankAccountId(accountId)
                .status(BankAccountStatus.VERIFIED)
                .isDefault(true)
                .build();
        Payout payout = Payout.builder()
                .payoutId(UUID.randomUUID())
                .refundId(refundId)
                .bankAccountId(accountId)
                .providerPayoutId("provider-payout")
                .status(PayoutStatus.PROCESSING)
                .build();

        when(payoutRepository.findByStatuses(anyCollection())).thenReturn(List.of(payout));
        when(payoutRepository.findByIdForUpdate(payout.getPayoutId())).thenReturn(Optional.of(payout));
        when(payoutProviderPort.get("provider-payout")).thenReturn(new ProviderPayoutResult(
                "provider-payout", null, PayoutStatus.FAILED,
                "ACCOUNT_NOT_FOUND", "Không tìm thấy tài khoản nhận chi"
        ));
        when(bankAccountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        when(refundRepository.findByIdForUpdate(refundId)).thenReturn(Optional.of(refund));
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(payoutRepository.save(any(Payout.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reconcileProcessingPayouts();

        assertThat(account.getStatus()).isEqualTo(BankAccountStatus.REJECTED);
        assertThat(account.getIsDefault()).isFalse();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.AWAITING_BANK_ACCOUNT);
        assertThat(refund.getFailureReason()).isEqualTo("Không tìm thấy tài khoản nhận chi");
        verify(bankAccountRepository).save(account);
        verify(paymentDomainEventService).recordRefundResult(eq(refund), eq(PaymentStatus.SUCCEEDED), any());
    }
}
