package iuh.fit.paymentservice.application.port.persistence;

import iuh.fit.paymentservice.domain.entity.Settlement;
import iuh.fit.paymentservice.domain.enums.SettlementStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementRepository {
    Settlement save(Settlement settlement);

    Optional<Settlement> findByIdForUpdate(UUID settlementId);

    List<Settlement> findRecentByPayeeId(UUID payeeId);

    /** Tổng tiền đã/đang chi cho chủ nhận, bỏ qua các lần rút ở trạng thái {@code excluded} (thất bại/hủy). */
    double sumPayoutAmount(UUID payeeId, Collection<SettlementStatus> excluded);
}
