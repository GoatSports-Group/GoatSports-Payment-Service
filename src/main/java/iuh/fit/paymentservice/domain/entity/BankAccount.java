package iuh.fit.paymentservice.domain.entity;

import iuh.fit.paymentservice.domain.enums.BankAccountStatus;
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
public class BankAccount extends BaseEntity {
    private UUID bankAccountId;
    private UUID payeeId;
    private String bankBin;
    private String accountNumberEncrypted;
    private String accountNumberHash;
    private String accountNumberLast4;
    private String accountName;
    private BankAccountStatus status;
    private Boolean isDefault;
    private LocalDateTime verifiedAt;
    private LocalDateTime disabledAt;
}
