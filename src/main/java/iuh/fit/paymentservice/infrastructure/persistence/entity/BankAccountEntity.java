package iuh.fit.paymentservice.infrastructure.persistence.entity;

import iuh.fit.paymentservice.domain.enums.BankAccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bank_accounts")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "bank_account_id", nullable = false)
    private UUID bankAccountId;
    @Column(name = "payee_id", nullable = false)
    private UUID payeeId;
    @Column(name = "bank_bin", nullable = false, length = 12)
    private String bankBin;
    @Column(name = "account_number_encrypted", nullable = false, columnDefinition = "TEXT")
    private String accountNumberEncrypted;
    @Column(name = "account_number_hash", nullable = false, length = 128)
    private String accountNumberHash;
    @Column(name = "account_number_last4", nullable = false, length = 4)
    private String accountNumberLast4;
    @Column(name = "account_name", nullable = false, length = 255)
    private String accountName;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private BankAccountStatus status;
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
    @Column(name = "disabled_at")
    private LocalDateTime disabledAt;
}
