package iuh.fit.paymentservice.domain.entity;

import iuh.fit.paymentservice.domain.enums.SettlementStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Settlement extends BaseEntity {
    private UUID settlementId;
    private UUID payeeId;
    private String currency;
    private Double grossAmount;
    private Double refundAmount;
    private Double platformFeeAmount;
    private Double payoutAmount;
    private LocalDateTime availableAt;
    private SettlementStatus status;
    private LocalDateTime settledAt;
}
