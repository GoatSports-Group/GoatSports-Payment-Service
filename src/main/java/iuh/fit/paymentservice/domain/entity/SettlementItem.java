package iuh.fit.paymentservice.domain.entity;

import iuh.fit.paymentservice.domain.enums.SettlementItemType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementItem extends BaseEntity {
    private UUID settlementItemId;
    private UUID settlementId;
    private UUID paymentId;
    private UUID refundId;
    private SettlementItemType type;
    private Double amount;
}
