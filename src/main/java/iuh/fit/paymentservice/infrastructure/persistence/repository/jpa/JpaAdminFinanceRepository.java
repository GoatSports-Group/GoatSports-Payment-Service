package iuh.fit.paymentservice.infrastructure.persistence.repository.jpa;

import iuh.fit.paymentservice.application.service.AdminFinanceService.PaymentRow;
import iuh.fit.paymentservice.application.service.AdminFinanceService.PayoutRow;
import iuh.fit.paymentservice.application.service.AdminFinanceService.RefundRow;
import iuh.fit.paymentservice.application.service.AdminFinanceService.Totals;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;
import iuh.fit.paymentservice.domain.enums.RefundStatus;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Trang Tài chính của admin: mọi truy vấn là projection (select new ...) hoặc tổng hợp, không nạp entity
 * nên không kéo theo quan hệ (refund.payment, payout.bankAccount...) từng dòng.
 */
public interface JpaAdminFinanceRepository extends Repository<PaymentEntity, UUID> {
    String PAYMENT_ROW = "select new iuh.fit.paymentservice.application.service.AdminFinanceService$PaymentRow("
            + "p.paymentId, p.payerId, p.payeeId, p.purpose, p.referenceType, p.referenceId, p.amount, "
            + "p.platformFeeAmount, p.status, p.description, p.paidAt, p.createdAt) ";
    String REFUND_ROW = "select new iuh.fit.paymentservice.application.service.AdminFinanceService$RefundRow("
            + "r.refundId, r.paymentId, p.payerId, p.purpose, p.referenceType, p.referenceId, r.amount, r.status, "
            + "r.reason, r.failureReason, r.retryCount, r.createdAt, r.updatedAt, r.refundedAt) ";
    String PAYOUT_ROW = "select new iuh.fit.paymentservice.application.service.AdminFinanceService$PayoutRow("
            + "po.payoutId, po.settlementId, ba.payeeId, po.amount, po.status, po.destinationBankBin, "
            + "po.destinationAccountMasked, po.destinationAccountName, po.failureCode, po.failureReason, "
            + "po.requestedAt, po.completedAt) ";

    @Query("select new iuh.fit.paymentservice.application.service.AdminFinanceService$Totals("
            + "sum(p.amount), sum(p.platformFeeAmount), count(p)) from PaymentEntity p "
            + "where p.status in :statuses and p.paidAt >= :from and p.paidAt < :to")
    Totals sumCollected(Collection<PaymentStatus> statuses, LocalDateTime from, LocalDateTime to);

    @Query("select coalesce(sum(r.amount), 0) from RefundEntity r "
            + "where r.status = :status and r.refundedAt >= :from and r.refundedAt < :to")
    Double sumRefunds(RefundStatus status, LocalDateTime from, LocalDateTime to);

    @Query("select coalesce(sum(po.amount), 0) from PayoutEntity po "
            + "where po.settlementId is not null and po.status = :status and po.completedAt >= :from and po.completedAt < :to")
    Double sumOwnerPayouts(PayoutStatus status, LocalDateTime from, LocalDateTime to);

    @Query("select count(r) from RefundEntity r where r.status in :statuses")
    long countRefunds(Collection<RefundStatus> statuses);

    @Query("select count(po) from PayoutEntity po where po.settlementId is not null and po.status in :statuses")
    long countOwnerPayouts(Collection<PayoutStatus> statuses);

    @Query(value = PAYMENT_ROW + "from PaymentEntity p where p.status in :statuses and p.purpose in :purposes "
            + "and p.createdAt >= :from and p.createdAt < :to order by p.createdAt desc, p.paymentId",
            countQuery = "select count(p) from PaymentEntity p where p.status in :statuses and p.purpose in :purposes "
                    + "and p.createdAt >= :from and p.createdAt < :to")
    Page<PaymentRow> findPayments(Collection<PaymentStatus> statuses, Collection<PaymentPurpose> purposes,
                                  LocalDateTime from, LocalDateTime to, Pageable pageable);

    @Query(value = REFUND_ROW + "from RefundEntity r join r.payment p where r.status in :statuses "
            + "order by r.updatedAt desc, r.refundId",
            countQuery = "select count(r) from RefundEntity r where r.status in :statuses")
    Page<RefundRow> findRefunds(Collection<RefundStatus> statuses, Pageable pageable);

    @Query(REFUND_ROW + "from RefundEntity r join r.payment p where r.refundId = :refundId")
    Optional<RefundRow> findRefund(UUID refundId);

    @Query(value = PAYOUT_ROW + "from PayoutEntity po join po.bankAccount ba where po.status in :statuses "
            + "and ((:ownerWithdrawals = true and po.settlementId is not null) "
            + "or (:ownerWithdrawals = false and po.settlementId is null)) "
            + "order by po.requestedAt desc, po.payoutId",
            countQuery = "select count(po) from PayoutEntity po where po.status in :statuses "
                    + "and ((:ownerWithdrawals = true and po.settlementId is not null) "
                    + "or (:ownerWithdrawals = false and po.settlementId is null))")
    Page<PayoutRow> findPayouts(boolean ownerWithdrawals, Collection<PayoutStatus> statuses, Pageable pageable);
}
