package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.payout.CreatePayoutCommand;
import iuh.fit.paymentservice.application.dto.response.payout.ProviderPayoutResult;
import iuh.fit.paymentservice.application.exception.PaymentApplicationException;
import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.exception.PaymentNotFoundException;
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
import iuh.fit.paymentservice.domain.entity.Payout;
import iuh.fit.paymentservice.domain.entity.Settlement;
import iuh.fit.paymentservice.domain.enums.BankAccountStatus;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;
import iuh.fit.paymentservice.domain.enums.SettlementStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OwnerPayoutServiceImpl implements OwnerPayoutService {
    static final double MINIMUM_WITHDRAWAL = 10_000D;
    private static final LocalDateTime EVER = LocalDateTime.of(9999, 12, 31, 0, 0);
    private static final EnumSet<PaymentStatus> COLLECTED = EnumSet.of(
            PaymentStatus.SUCCEEDED, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED);
    private static final EnumSet<SettlementStatus> NOT_PAID_OUT = EnumSet.of(SettlementStatus.FAILED, SettlementStatus.CANCELLED);
    private static final EnumSet<BankAccountStatus> USABLE_ACCOUNTS = EnumSet.of(
            BankAccountStatus.PENDING_VERIFICATION, BankAccountStatus.VERIFIED);

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final RefundRepository refundRepository;
    private final SettlementRepository settlementRepository;
    private final PayoutRepository payoutRepository;
    private final BankAccountRepository bankAccountRepository;
    private final BankAccountProtector bankAccountProtector;
    private final PayoutProviderPort payoutProviderPort;

    /** Tiền mới thu giữ lại vài ngày: khách hủy sân / rút khỏi giải thì còn tiền để hoàn. */
    @Value("${payment.settlement.hold-days:3}")
    private int holdDays = 3;

    @Override
    @Transactional
    public void assignPayee(UUID paymentId, UUID payeeId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        if (payeeId.equals(payment.getPayeeId())) return;
        if (payment.getPayeeId() != null) {
            throw new PaymentConflictException("PAYEE_ALREADY_ASSIGNED", "Khoản thu này đã thuộc về người nhận khác");
        }
        // Tiền mặt chủ sân đã tự thu tại quầy: không đi qua tài khoản nền tảng nên không tính vào số dư.
        boolean cash = paymentAttemptRepository.findLatestByPaymentIdAndStatuses(
                        paymentId, EnumSet.of(PaymentAttemptStatus.SUCCEEDED))
                .map(attempt -> attempt.getProvider() == PaymentProvider.MANUAL)
                .orElse(false);
        if (cash) return;
        payment.setPayeeId(payeeId);
        paymentRepository.save(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Balance getBalance(UUID payeeId) {
        double earned = paymentRepository.sumPayeeAmount(payeeId, COLLECTED, EVER);
        double matured = paymentRepository.sumPayeeAmount(payeeId, COLLECTED, LocalDateTime.now().minusDays(holdDays));
        double refunded = refundRepository.sumByPayeeId(payeeId);
        double withdrawn = settlementRepository.sumPayoutAmount(payeeId, NOT_PAID_OUT);
        double available = Math.max(0, Math.floor(matured - refunded - withdrawn));
        return new Balance(earned, refunded, withdrawn, earned - matured, available, holdDays, MINIMUM_WITHDRAWAL);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Withdrawal> getWithdrawals(UUID payeeId) {
        return settlementRepository.findRecentByPayeeId(payeeId).stream().map(this::view).toList();
    }

    @Override
    @Transactional
    public Withdrawal withdraw(UUID payeeId) {
        BankAccount account = bankAccountRepository.findDefaultByPayeeIdAndStatuses(payeeId, USABLE_ACCOUNTS)
                .orElseThrow(() -> new PaymentConflictException("BANK_ACCOUNT_REQUIRED",
                        "Vui lòng liên kết tài khoản ngân hàng để rút tiền"));
        // ponytail: khóa theo tài khoản mặc định để hai lần bấm "Rút" không cùng đọc một số dư. Đổi tài khoản
        // mặc định đúng lúc đang rút thì hai khóa khác nhau — chuyển sang khóa theo payee nếu việc đó xảy ra thật.
        bankAccountRepository.findByIdForUpdate(account.getBankAccountId());
        double amount = getBalance(payeeId).available();
        if (amount < MINIMUM_WITHDRAWAL) {
            throw new PaymentConflictException("BALANCE_TOO_LOW",
                    "Số dư khả dụng cần tối thiểu 10.000 đ để rút");
        }

        LocalDateTime now = LocalDateTime.now();
        Settlement settlement = settlementRepository.save(Settlement.builder()
                .payeeId(payeeId).currency("VND").grossAmount(amount).refundAmount(0D).platformFeeAmount(0D)
                .payoutAmount(amount).availableAt(now).status(SettlementStatus.PROCESSING).build());
        Payout payout = payoutRepository.save(Payout.builder()
                .settlementId(settlement.getSettlementId())
                .bankAccountId(account.getBankAccountId())
                .provider(PaymentProvider.PAYOS)
                .referenceId("settlement-" + settlement.getSettlementId())
                .idempotencyKey("settlement-payout-" + settlement.getSettlementId())
                .amount(amount)
                .destinationBankBin(account.getBankBin())
                .destinationAccountMasked("****" + account.getAccountNumberLast4())
                .destinationAccountName(account.getAccountName())
                .status(PayoutStatus.PENDING)
                .requestedAt(now)
                .build());

        ProviderPayoutResult result;
        try {
            result = payoutProviderPort.create(new CreatePayoutCommand(
                    payout.getReferenceId(), payout.getIdempotencyKey(), amount, account.getBankBin(),
                    bankAccountProtector.decrypt(account.getAccountNumberEncrypted()), "GoatSports doanh thu"));
        } catch (PaymentApplicationException exception) {
            // payOS từ chối rõ ràng: ghi lần rút thất bại, tiền trở lại số dư để rút lại sau.
            result = new ProviderPayoutResult(null, null, PayoutStatus.FAILED, exception.getCode(), exception.getMessage());
        }
        payout.setProviderPayoutId(result.providerPayoutId());
        applyProviderResult(payout, result);
        return view(settlementRepository.findByIdForUpdate(settlement.getSettlementId()).orElseThrow());
    }

    @Override
    @Transactional
    public void applyProviderResult(Payout payout, ProviderPayoutResult result) {
        payout.setStatus(result.status());
        payout.setFailureCode(result.failureCode());
        payout.setFailureReason(result.failureReason());
        if (result.status() == PayoutStatus.SUCCEEDED) payout.setCompletedAt(LocalDateTime.now());
        payoutRepository.save(payout);

        Settlement settlement = settlementRepository.findByIdForUpdate(payout.getSettlementId()).orElseThrow();
        if (result.status() == PayoutStatus.SUCCEEDED) {
            settlement.setStatus(SettlementStatus.PAID);
            settlement.setSettledAt(LocalDateTime.now());
        } else if (result.status() == PayoutStatus.FAILED || result.status() == PayoutStatus.CANCELLED) {
            settlement.setStatus(SettlementStatus.FAILED);
            if (PayoutFailureMessages.classify(result.failureCode(), result.failureReason())
                    == PayoutFailureMessages.Kind.DESTINATION) {
                // Tài khoản nhận không tồn tại: không để lần rút sau lại chọn nó làm mặc định.
                bankAccountRepository.findByIdForUpdate(payout.getBankAccountId()).ifPresent(account -> {
                    account.setStatus(BankAccountStatus.REJECTED);
                    account.setIsDefault(false);
                    account.setVerifiedAt(null);
                    bankAccountRepository.save(account);
                });
            }
        } else {
            // PENDING/PROCESSING/MANUAL_REVIEW: tiền vẫn coi như đã rút cho tới khi payOS trả kết quả cuối.
            settlement.setStatus(SettlementStatus.PROCESSING);
        }
        settlementRepository.save(settlement);
    }

    /** Lý do đọc được cho chủ sân; lý do thô của payOS vẫn ở payout để đối soát. */
    private String failureMessage(Settlement settlement, Payout payout) {
        if (payout == null || (payout.getFailureReason() == null && payout.getFailureCode() == null)) return null;
        String masked = payout.getDestinationAccountMasked();
        boolean verified = bankAccountRepository.findByPayeeId(settlement.getPayeeId()).stream()
                .anyMatch(account -> account.getBankAccountId().equals(payout.getBankAccountId())
                        && account.getStatus() == BankAccountStatus.VERIFIED);
        return PayoutFailureMessages.userMessage(payout.getFailureCode(), payout.getFailureReason(),
                masked == null ? null : masked.replace("*", ""), verified);
    }

    private Withdrawal view(Settlement settlement) {
        Payout payout = payoutRepository.findBySettlementId(settlement.getSettlementId()).orElse(null);
        return new Withdrawal(settlement.getSettlementId(), settlement.getPayoutAmount(), settlement.getStatus(),
                payout == null ? null : payout.getDestinationAccountMasked(),
                payout == null ? null : payout.getDestinationAccountName(),
                failureMessage(settlement, payout),
                settlement.getCreatedAt(), settlement.getSettledAt());
    }
}
