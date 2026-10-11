package iuh.fit.paymentservice.infrastructure.persistence.repository;

import iuh.fit.paymentservice.application.port.persistence.AdminFinanceQueryPort;
import iuh.fit.paymentservice.application.service.AdminFinanceService.FinancePage;
import iuh.fit.paymentservice.application.service.AdminFinanceService.PaymentRow;
import iuh.fit.paymentservice.application.service.AdminFinanceService.PayoutRow;
import iuh.fit.paymentservice.application.service.AdminFinanceService.RefundRow;
import iuh.fit.paymentservice.application.service.AdminFinanceService.Totals;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;
import iuh.fit.paymentservice.domain.enums.RefundStatus;
import iuh.fit.paymentservice.infrastructure.persistence.repository.jpa.JpaAdminFinanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AdminFinanceQueryAdapter implements AdminFinanceQueryPort {
    private final JpaAdminFinanceRepository repository;

    @Override
    public Totals sumCollected(Collection<PaymentStatus> statuses, LocalDateTime from, LocalDateTime to) {
        return repository.sumCollected(statuses, from, to);
    }

    @Override
    public double sumRefunded(LocalDateTime from, LocalDateTime to) {
        return repository.sumRefunds(RefundStatus.SUCCEEDED, from, to);
    }

    @Override
    public double sumPaidOutToOwners(LocalDateTime from, LocalDateTime to) {
        return repository.sumOwnerPayouts(PayoutStatus.SUCCEEDED, from, to);
    }

    @Override
    public long countRefunds(Collection<RefundStatus> statuses) {
        return repository.countRefunds(statuses);
    }

    @Override
    public long countOwnerPayouts(Collection<PayoutStatus> statuses) {
        return repository.countOwnerPayouts(statuses);
    }

    @Override
    public FinancePage<PaymentRow> findPayments(Collection<PaymentStatus> statuses, Collection<PaymentPurpose> purposes,
                                                LocalDateTime from, LocalDateTime to, int page, int size) {
        return page(repository.findPayments(statuses, purposes, from, to, PageRequest.of(page, size)));
    }

    @Override
    public FinancePage<RefundRow> findRefunds(Collection<RefundStatus> statuses, int page, int size) {
        return page(repository.findRefunds(statuses, PageRequest.of(page, size)));
    }

    @Override
    public Optional<RefundRow> findRefund(UUID refundId) {
        return repository.findRefund(refundId);
    }

    @Override
    public FinancePage<PayoutRow> findPayouts(boolean ownerWithdrawals, Collection<PayoutStatus> statuses, int page, int size) {
        return page(repository.findPayouts(ownerWithdrawals, statuses, PageRequest.of(page, size)));
    }

    private static <T> FinancePage<T> page(Page<T> result) {
        return new FinancePage<>(result.getContent(), result.getTotalElements());
    }
}
