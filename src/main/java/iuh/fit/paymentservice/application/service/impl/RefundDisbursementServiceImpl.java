package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.payout.CreatePayoutCommand;
import iuh.fit.paymentservice.application.dto.response.payout.ProviderPayoutResult;
import iuh.fit.paymentservice.application.exception.PaymentAccessDeniedException;
import iuh.fit.paymentservice.application.exception.PaymentApplicationException;
import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.exception.PaymentNotFoundException;
import iuh.fit.paymentservice.application.port.client.PayoutProviderPort;
import iuh.fit.paymentservice.application.port.persistence.BankAccountRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.port.persistence.PayoutRepository;
import iuh.fit.paymentservice.application.port.persistence.RefundRepository;
import iuh.fit.paymentservice.application.port.security.BankAccountProtector;
import iuh.fit.paymentservice.application.service.PaymentDomainEventService;
import iuh.fit.paymentservice.application.service.RefundDisbursementService;
import iuh.fit.paymentservice.domain.entity.BankAccount;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.Payout;
import iuh.fit.paymentservice.domain.entity.Refund;
import iuh.fit.paymentservice.domain.enums.BankAccountStatus;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;
import iuh.fit.paymentservice.domain.enums.RefundStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundDisbursementServiceImpl implements RefundDisbursementService {
    private static final EnumSet<BankAccountStatus> USABLE_ACCOUNTS = EnumSet.of(
            BankAccountStatus.PENDING_VERIFICATION,
            BankAccountStatus.VERIFIED
    );

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final BankAccountRepository bankAccountRepository;
    private final PayoutRepository payoutRepository;
    private final BankAccountProtector bankAccountProtector;
    private final PayoutProviderPort payoutProviderPort;
    private final PaymentDomainEventService paymentDomainEventService;

    @Override
    @Transactional
    public Refund claim(UUID refundId, UUID payerId) {
        Refund refund = lockedRefund(refundId);
        Payment payment = paymentRepository.findByIdForUpdate(refund.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(refund.getPaymentId()));
        if (!payment.getPayerId().equals(payerId)) throw new PaymentAccessDeniedException();
        if (refund.getStatus() == RefundStatus.SUCCEEDED || refund.getStatus() == RefundStatus.PROCESSING) {
            return refund;
        }
        if (refund.getStatus() != RefundStatus.AWAITING_CLAIM
                && refund.getStatus() != RefundStatus.AWAITING_BANK_ACCOUNT
                && refund.getStatus() != RefundStatus.FAILED) {
            throw new PaymentConflictException("REFUND_NOT_CLAIMABLE", "Refund hiện không thể nhận");
        }
        return disburse(refund, payment);
    }

    @Override
    @Transactional
    public void processAutomaticRefund(UUID refundId) {
        Refund refund = lockedRefund(refundId);
        if (!Boolean.TRUE.equals(refund.getAutoDisburse())
                || refund.getStatus() != RefundStatus.AWAITING_BANK_ACCOUNT) return;
        Payment payment = paymentRepository.findByIdForUpdate(refund.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(refund.getPaymentId()));
        disburse(refund, payment);
    }

    @Override
    @Transactional
    public void processWaitingAutomaticRefunds(UUID payerId) {
        List<Refund> waiting = refundRepository.findAutomaticByPayerIdAndStatus(
                payerId, RefundStatus.AWAITING_BANK_ACCOUNT
        );
        for (Refund refund : waiting) {
            try {
                processAutomaticRefund(refund.getRefundId());
            } catch (RuntimeException ignored) {
                // A reserved refund is never dropped; a later link/retry can continue it.
            }
        }
    }

    @Override
    @Scheduled(fixedDelayString = "${payment.payout.reconciliation-delay-ms:30000}")
    @Transactional
    public void reconcileProcessingPayouts() {
        payoutRepository.findByStatuses(EnumSet.of(PayoutStatus.PROCESSING)).stream()
                .limit(100)
                .forEach(this::reconcile);
    }

    protected void reconcile(Payout candidate) {
        Payout payout = payoutRepository.findByIdForUpdate(candidate.getPayoutId()).orElse(null);
        if (payout == null || payout.getStatus() != PayoutStatus.PROCESSING) return;
        ProviderPayoutResult result = payoutProviderPort.get(payout.getProviderPayoutId());
        applyProviderResult(payout, result);
    }

    private Refund disburse(Refund refund, Payment payment) {
        BankAccount account = bankAccountRepository.findDefaultByPayeeIdAndStatuses(
                payment.getPayerId(), USABLE_ACCOUNTS
        ).orElseThrow(() -> {
            refund.setStatus(RefundStatus.AWAITING_BANK_ACCOUNT);
            refund.setFailureReason("Vui lòng liên kết tài khoản ngân hàng để nhận hoàn tiền");
            refundRepository.save(refund);
            return new PaymentConflictException(
                    "BANK_ACCOUNT_REQUIRED",
                    "Vui lòng liên kết tài khoản ngân hàng để nhận hoàn tiền"
            );
        });

        Payout payout = payoutRepository.findByRefundId(refund.getRefundId()).orElseGet(() -> payoutRepository.save(
                Payout.builder()
                        .refundId(refund.getRefundId())
                        .bankAccountId(account.getBankAccountId())
                        .provider(PaymentProvider.PAYOS)
                        .referenceId("refund-" + refund.getRefundId())
                        .idempotencyKey("refund-payout-" + refund.getRefundId())
                        .amount(refund.getAmount())
                        .destinationBankBin(account.getBankBin())
                        .destinationAccountMasked("****" + account.getAccountNumberLast4())
                        .destinationAccountName(account.getAccountName())
                        .status(PayoutStatus.PENDING)
                        .requestedAt(LocalDateTime.now())
                        .build()
        ));
        if (payout.getStatus() == PayoutStatus.SUCCEEDED) return refund;
        if (payout.getStatus() == PayoutStatus.FAILED || payout.getStatus() == PayoutStatus.CANCELLED) {
            prepareRetry(payout, account, refund);
            payout = payoutRepository.save(payout);
        }

        refund.setStatus(RefundStatus.PROCESSING);
        refund.setFailureReason(null);
        refundRepository.save(refund);
        ProviderPayoutResult result;
        try {
            result = payoutProviderPort.create(new CreatePayoutCommand(
                    payout.getReferenceId(), payout.getIdempotencyKey(), payout.getAmount(), account.getBankBin(),
                    bankAccountProtector.decrypt(account.getAccountNumberEncrypted()),
                    "Hoan tien GoatSports"
            ));
        } catch (PaymentApplicationException exception) {
            payout.setStatus(PayoutStatus.FAILED);
            payout.setFailureCode(exception.getCode());
            payout.setFailureReason(exception.getMessage());
            payoutRepository.save(payout);
            refund.setStatus(RefundStatus.FAILED);
            refund.setFailureReason(exception.getMessage());
            Refund failed = refundRepository.save(refund);
            paymentDomainEventService.recordRefundResult(failed, payment.getStatus(), payment.getPayerId());
            return failed;
        }
        payout.setProviderPayoutId(result.providerPayoutId());
        payout.setStatus(result.status());
        payout.setDestinationAccountName(result.destinationAccountName() == null
                ? account.getAccountName() : result.destinationAccountName());
        payout.setFailureCode(result.failureCode());
        payout.setFailureReason(result.failureReason());
        Payout saved = payoutRepository.save(payout);
        return applyProviderResult(saved, result);
    }

    private void prepareRetry(Payout payout, BankAccount account, Refund refund) {
        String retryId = UUID.randomUUID().toString();
        payout.setBankAccountId(account.getBankAccountId());
        payout.setProviderPayoutId(null);
        payout.setReferenceId("refund-" + refund.getRefundId() + "-" + retryId);
        payout.setIdempotencyKey("refund-payout-" + refund.getRefundId() + "-" + retryId);
        payout.setAmount(refund.getAmount());
        payout.setDestinationBankBin(account.getBankBin());
        payout.setDestinationAccountMasked("****" + account.getAccountNumberLast4());
        payout.setDestinationAccountName(account.getAccountName());
        payout.setStatus(PayoutStatus.PENDING);
        payout.setRequestedAt(LocalDateTime.now());
        payout.setCompletedAt(null);
        payout.setFailureCode(null);
        payout.setFailureReason(null);
    }

    private Refund applyProviderResult(Payout payout, ProviderPayoutResult result) {
        payout.setStatus(result.status());
        payout.setFailureCode(result.failureCode());
        payout.setFailureReason(result.failureReason());
        if (result.status() == PayoutStatus.SUCCEEDED) payout.setCompletedAt(LocalDateTime.now());
        payoutRepository.save(payout);

        bankAccountRepository.findByIdForUpdate(payout.getBankAccountId())
                .ifPresent(account -> updateVerifiedAccount(account, result));

        Refund refund = lockedRefund(payout.getRefundId());
        Payment payment = paymentRepository.findByIdForUpdate(refund.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(refund.getPaymentId()));
        if (result.status() == PayoutStatus.SUCCEEDED) {
            refund.setStatus(RefundStatus.SUCCEEDED);
            refund.setProviderRefundId(result.providerPayoutId());
            refund.setRefundedAt(LocalDateTime.now());
            refund.setFailureReason(null);
        } else if (result.status() == PayoutStatus.FAILED || result.status() == PayoutStatus.CANCELLED) {
            refund.setStatus(RefundStatus.AWAITING_BANK_ACCOUNT);
            refund.setFailureReason(result.failureReason() == null
                    ? "Tài khoản nhận tiền không hợp lệ, vui lòng liên kết lại" : result.failureReason());
        } else {
            refund.setStatus(RefundStatus.PROCESSING);
        }
        Refund saved = refundRepository.save(refund);
        if (saved.getStatus() == RefundStatus.SUCCEEDED) updatePaymentRefundedAmount(payment);
        paymentDomainEventService.recordRefundResult(saved, payment.getStatus(), payment.getPayerId());
        return saved;
    }

    private void updateVerifiedAccount(BankAccount account, ProviderPayoutResult result) {
        if ((result.status() == PayoutStatus.FAILED || result.status() == PayoutStatus.CANCELLED)
                && isBankAccountVerificationFailure(result)) {
            account.setStatus(BankAccountStatus.REJECTED);
            account.setIsDefault(false);
            account.setVerifiedAt(null);
        } else if (result.status() == PayoutStatus.SUCCEEDED) {
            account.setStatus(BankAccountStatus.VERIFIED);
            account.setVerifiedAt(account.getVerifiedAt() == null ? LocalDateTime.now() : account.getVerifiedAt());
            if (result.destinationAccountName() != null && !result.destinationAccountName().isBlank()) {
                account.setAccountName(result.destinationAccountName());
            }
        } else {
            return;
        }
        bankAccountRepository.save(account);
    }

    private boolean isBankAccountVerificationFailure(ProviderPayoutResult result) {
        String failure = ((result.failureCode() == null ? "" : result.failureCode()) + " "
                + (result.failureReason() == null ? "" : result.failureReason())).toLowerCase();
        return failure.contains("account_not_found")
                || failure.contains("invalid_account")
                || failure.contains("destination_account")
                || failure.contains("tài khoản nhận")
                || failure.contains("tai khoan nhan");
    }

    private void updatePaymentRefundedAmount(Payment payment) {
        double refunded = refundRepository.findByPaymentIdAndStatuses(
                        payment.getPaymentId(), EnumSet.of(RefundStatus.SUCCEEDED)
                ).stream().map(Refund::getAmount).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
        payment.setStatus(refunded + 0.001 >= payment.getAmount()
                ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        paymentRepository.save(payment);
    }

    private Refund lockedRefund(UUID refundId) {
        return refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new PaymentNotFoundException(refundId));
    }
}
