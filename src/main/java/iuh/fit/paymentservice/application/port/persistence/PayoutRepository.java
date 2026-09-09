package iuh.fit.paymentservice.application.port.persistence;

import iuh.fit.paymentservice.domain.entity.Payout;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutRepository {
    Payout save(Payout payout);

    Optional<Payout> findByRefundId(UUID refundId);

    Optional<Payout> findByIdForUpdate(UUID payoutId);

    List<Payout> findByStatuses(Collection<PayoutStatus> statuses);
}
