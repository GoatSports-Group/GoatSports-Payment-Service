package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.response.payout.ProviderPayoutResult;
import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.port.client.PayoutProviderPort;
import iuh.fit.paymentservice.application.port.persistence.BankAccountRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.port.persistence.PayoutRepository;
import iuh.fit.paymentservice.application.port.persistence.RefundRepository;
import iuh.fit.paymentservice.application.port.persistence.SettlementRepository;
import iuh.fit.paymentservice.application.port.security.BankAccountProtector;
import iuh.fit.paymentservice.application.service.OwnerPayoutService;
import iuh.fit.paymentservice.domain.entity.BankAccount;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.entity.Payout;
import iuh.fit.paymentservice.domain.entity.Settlement;
import iuh.fit.paymentservice.domain.enums.BankAccountStatus;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;
import iuh.fit.paymentservice.domain.enums.SettlementStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerPayoutServiceImplTests {
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentAttemptRepository paymentAttemptRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private SettlementRepository settlementRepository;
    @Mock private PayoutRepository payoutRepository;
    @Mock private BankAccountRepository bankAccountRepository;
    @Mock private BankAccountProtector bankAccountProtector;
    @Mock private PayoutProviderPort payoutProviderPort;
    @InjectMocks private OwnerPayoutServiceImpl service;

    private final UUID owner = UUID.randomUUID();

    private void ledger(double earned, double matured, double refunded, double withdrawn) {
        when(paymentRepository.sumPayeeAmount(eq(owner), anyCollection(), any())).thenAnswer(call ->
                call.<LocalDateTime>getArgument(2).getYear() == 9999 ? earned : matured);
        when(refundRepository.sumByPayeeId(owner)).thenReturn(refunded);
        when(settlementRepository.sumPayoutAmount(eq(owner), anyCollection())).thenReturn(withdrawn);
    }

    @Test
    void balanceSubtractsRefundsAndWithdrawalsAndHoldsFreshMoney() {
        ledger(500_000, 300_000, 50_000, 100_000);

        var balance = service.getBalance(owner);

        assertThat(balance.onHold()).isEqualTo(200_000);
        assertThat(balance.available()).isEqualTo(150_000);
    }

    @Test
    void earningsListPayeeShareAndFlagMoneyStillOnHold() {
        Payment fresh = Payment.builder().paymentId(UUID.randomUUID()).payeeAmount(90_000.0)
                .status(iuh.fit.paymentservice.domain.enums.PaymentStatus.SUCCEEDED)
                .paidAt(LocalDateTime.now().minusDays(1)).build();
        Payment matured = Payment.builder().paymentId(UUID.randomUUID()).payeeAmount(60_000.0)
                .status(iuh.fit.paymentservice.domain.enums.PaymentStatus.PARTIALLY_REFUNDED)
                .paidAt(LocalDateTime.now().minusDays(10)).build();
        when(paymentRepository.findRecentByPayee(eq(owner), anyCollection())).thenReturn(java.util.List.of(fresh, matured));

        var earnings = service.getEarnings(owner);

        // Moc giu tien mac dinh 3 ngay giong getBalance(): khoan 1 ngay truoc con dang giu, khoan 10 ngay truoc da rut duoc.
        assertThat(earnings).extracting(OwnerPayoutService.Earning::amount).containsExactly(90_000.0, 60_000.0);
        assertThat(earnings).extracting(OwnerPayoutService.Earning::onHold).containsExactly(true, false);
    }

    @Test
    void refundsBiggerThanMaturedMoneyNeverShowNegativeBalance() {
        ledger(100_000, 0, 60_000, 0);

        assertThat(service.getBalance(owner).available()).isZero();
    }

    @Test
    void withdrawPaysTheWholeAvailableBalanceToTheDefaultAccount() {
        ledger(300_000, 300_000, 0, 0);
        BankAccount account = BankAccount.builder().bankAccountId(UUID.randomUUID()).payeeId(owner).bankBin("970415")
                .accountNumberEncrypted("enc").accountNumberLast4("6077").accountName("CHU SAN")
                .status(BankAccountStatus.VERIFIED).isDefault(true).build();
        when(bankAccountRepository.findDefaultByPayeeIdAndStatuses(eq(owner), anyCollection())).thenReturn(Optional.of(account));
        UUID settlementId = UUID.randomUUID();
        Settlement[] stored = new Settlement[1];
        when(settlementRepository.save(any())).thenAnswer(call -> {
            Settlement settlement = call.getArgument(0);
            if (settlement.getSettlementId() == null) settlement.setSettlementId(settlementId);
            stored[0] = settlement;
            return settlement;
        });
        when(settlementRepository.findByIdForUpdate(settlementId)).thenAnswer(call -> Optional.of(stored[0]));
        when(payoutRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(bankAccountProtector.decrypt("enc")).thenReturn("0123456077");
        when(payoutProviderPort.create(any())).thenReturn(
                new ProviderPayoutResult("po-1", "CHU SAN", PayoutStatus.SUCCEEDED, null, null));

        var withdrawal = service.withdraw(owner, null);

        assertThat(withdrawal.amount()).isEqualTo(300_000);
        assertThat(withdrawal.status()).isEqualTo(SettlementStatus.PAID);
        verify(payoutProviderPort).create(any());
    }

    @Test
    void withdrawBelowMinimumIsRefusedWithoutCallingPayOs() {
        ledger(5_000, 5_000, 0, 0);
        when(bankAccountRepository.findDefaultByPayeeIdAndStatuses(eq(owner), anyCollection()))
                .thenReturn(Optional.of(BankAccount.builder().bankAccountId(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> service.withdraw(owner, null)).isInstanceOf(PaymentConflictException.class);
        verify(payoutProviderPort, never()).create(any());
    }

    @Test
    void cashCollectedByTheOwnerIsNotCreditedToTheirBalance() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(Payment.builder().paymentId(paymentId).build()));
        when(paymentAttemptRepository.findLatestByPaymentIdAndStatuses(eq(paymentId), anyCollection()))
                .thenReturn(Optional.of(PaymentAttempt.builder().provider(PaymentProvider.MANUAL).build()));

        service.assignPayee(paymentId, owner);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void failedPayoutReturnsTheMoneyToTheBalance() {
        UUID settlementId = UUID.randomUUID();
        Settlement settlement = Settlement.builder().settlementId(settlementId).status(SettlementStatus.PROCESSING).build();
        when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.of(settlement));

        service.applyProviderResult(Payout.builder().settlementId(settlementId).build(),
                new ProviderPayoutResult("po-1", null, PayoutStatus.FAILED, "X", "Sai tài khoản"));

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.FAILED);
    }

    private Settlement[] withdrawalSetup(UUID settlementId, Payout[] payoutHolder) {
        ledger(300_000, 300_000, 0, 0);
        BankAccount account = BankAccount.builder().bankAccountId(UUID.randomUUID()).payeeId(owner).bankBin("970415")
                .accountNumberEncrypted("enc").accountNumberLast4("6077").accountName("CHU SAN")
                .status(BankAccountStatus.VERIFIED).isDefault(true).build();
        when(bankAccountRepository.findDefaultByPayeeIdAndStatuses(eq(owner), anyCollection())).thenReturn(Optional.of(account));
        Settlement[] stored = new Settlement[1];
        when(settlementRepository.save(any())).thenAnswer(call -> {
            Settlement settlement = call.getArgument(0);
            if (settlement.getSettlementId() == null) settlement.setSettlementId(settlementId);
            stored[0] = settlement;
            return settlement;
        });
        when(settlementRepository.findByIdForUpdate(settlementId)).thenAnswer(call -> Optional.of(stored[0]));
        when(payoutRepository.save(any())).thenAnswer(call -> {
            payoutHolder[0] = call.getArgument(0);
            return payoutHolder[0];
        });
        when(bankAccountProtector.decrypt("enc")).thenReturn("0123456077");
        return stored;
    }

    /** Timeout khi gọi payOS: chưa biết tiền đã chuyển chưa, nên KHÔNG trả về số dư (chủ sân rút lại là nhận hai lần). */
    @Test
    void withdrawalWithUnknownOutcomeStaysProcessingInsteadOfReturningMoneyToBalance() {
        UUID settlementId = UUID.randomUUID();
        Payout[] payout = new Payout[1];
        Settlement[] settlement = withdrawalSetup(settlementId, payout);
        when(payoutProviderPort.create(any())).thenThrow(new iuh.fit.paymentservice.application.exception.PaymentProviderException(
                "PAYOS_PAYOUT_ERROR", "payOS không thể xác minh hoặc tạo giao dịch hoàn tiền"));

        var withdrawal = service.withdraw(owner, null);

        assertThat(withdrawal.status()).isEqualTo(SettlementStatus.PROCESSING);
        assertThat(settlement[0].getStatus()).isEqualTo(SettlementStatus.PROCESSING);
        assertThat(payout[0].getStatus()).isEqualTo(PayoutStatus.PENDING);
        assertThat(withdrawal.failureReason()).isNull();
    }

    /** payOS từ chối rõ ràng (sai số tài khoản): vẫn thất bại ngay, tiền về số dư như trước. */
    @Test
    void withdrawalRejectedByPayOsStillFailsImmediately() {
        UUID settlementId = UUID.randomUUID();
        Payout[] payout = new Payout[1];
        Settlement[] settlement = withdrawalSetup(settlementId, payout);
        when(payoutProviderPort.create(any())).thenThrow(new iuh.fit.paymentservice.application.exception.PaymentProviderException(
                "PAYOS_PAYOUT_INVALID_TO_ACCOUNT_NUMBER", "Không tìm thấy tài khoản nhận chi"));

        service.withdraw(owner, null);

        assertThat(settlement[0].getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(payout[0].getStatus()).isEqualTo(PayoutStatus.FAILED);
    }

    private Payout uncertainPayout(UUID settlementId, Settlement settlement, LocalDateTime requestedAt) {
        Payout payout = Payout.builder().payoutId(UUID.randomUUID()).settlementId(settlementId)
                .referenceId("settlement-" + settlementId).status(PayoutStatus.PENDING).requestedAt(requestedAt).build();
        when(payoutRepository.findByStatuses(anyCollection())).thenReturn(java.util.List.of(payout));
        when(payoutRepository.findByIdForUpdate(payout.getPayoutId())).thenReturn(Optional.of(payout));
        when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.of(settlement));
        return payout;
    }

    @Test
    void reconciliationRecordsTheTransferPayOsActuallyMade() {
        UUID settlementId = UUID.randomUUID();
        Settlement settlement = Settlement.builder().settlementId(settlementId).status(SettlementStatus.PROCESSING).build();
        Payout payout = uncertainPayout(settlementId, settlement, LocalDateTime.now().minusMinutes(3));
        when(payoutProviderPort.findByReference("settlement-" + settlementId)).thenReturn(Optional.of(
                new ProviderPayoutResult("po-9", "CHU SAN", PayoutStatus.SUCCEEDED, null, null)));

        service.reconcileUncertainWithdrawals();

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(payout.getProviderPayoutId()).isEqualTo("po-9");
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.SUCCEEDED);
    }

    @Test
    void reconciliationOnlyGivesUpAfterPayOsNeverReceivedTheTransfer() {
        UUID settlementId = UUID.randomUUID();
        Settlement settlement = Settlement.builder().settlementId(settlementId).status(SettlementStatus.PROCESSING).build();
        Payout payout = uncertainPayout(settlementId, settlement, LocalDateTime.now().minusMinutes(3));
        when(payoutProviderPort.findByReference("settlement-" + settlementId)).thenReturn(Optional.empty());

        service.reconcileUncertainWithdrawals();
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PROCESSING); // mới 3 phút: chờ tiếp

        payout.setRequestedAt(LocalDateTime.now().minusMinutes(11));
        service.reconcileUncertainWithdrawals();
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.FAILED); // quá 10 phút payOS vẫn không có lệnh
        assertThat(payout.getFailureCode()).isEqualTo("PAYOS_PAYOUT_TIMEOUT");
    }

    @Test
    void withdrawRefusesAmountAboveAvailableOrBelowMinimumWithoutCallingPayOs() {
        ledger(500_000, 300_000, 0, 0);
        BankAccount account = BankAccount.builder().bankAccountId(UUID.randomUUID()).build();
        when(bankAccountRepository.findDefaultByPayeeIdAndStatuses(eq(owner), anyCollection())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.withdraw(owner, 300_001.0))
                .isInstanceOf(PaymentConflictException.class).hasMessageContaining("lớn hơn số dư");
        assertThatThrownBy(() -> service.withdraw(owner, 9_000.0))
                .isInstanceOf(PaymentConflictException.class).hasMessageContaining("tối thiểu");
        assertThatThrownBy(() -> service.withdraw(owner, 50_000.5))
                .isInstanceOf(PaymentConflictException.class).hasMessageContaining("số nguyên");
        verify(payoutProviderPort, never()).create(any());
    }
}
