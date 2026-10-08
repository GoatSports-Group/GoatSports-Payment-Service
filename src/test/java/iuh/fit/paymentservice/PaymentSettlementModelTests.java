package iuh.fit.paymentservice;

import iuh.fit.paymentservice.domain.entity.BankAccount;
import iuh.fit.paymentservice.domain.entity.Payout;
import iuh.fit.paymentservice.domain.entity.Settlement;
import iuh.fit.paymentservice.domain.enums.BankAccountStatus;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;
import iuh.fit.paymentservice.domain.enums.SettlementStatus;
import iuh.fit.paymentservice.infrastructure.persistence.entity.BankAccountEntity;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PaymentAttemptEntity;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PayoutEntity;
import iuh.fit.paymentservice.infrastructure.persistence.entity.ProviderWebhookEventEntity;
import iuh.fit.paymentservice.infrastructure.persistence.entity.RefundEntity;
import iuh.fit.paymentservice.infrastructure.persistence.entity.SettlementEntity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentSettlementModelTests {

    @Test
    void settlementDomainMatchesClassDiagram() {
        assertThat(declaredFields(BankAccount.class)).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("bankAccountId", UUID.class),
                Map.entry("payeeId", UUID.class),
                Map.entry("bankBin", String.class),
                Map.entry("accountNumberEncrypted", String.class),
                Map.entry("accountNumberHash", String.class),
                Map.entry("accountNumberLast4", String.class),
                Map.entry("accountName", String.class),
                Map.entry("status", BankAccountStatus.class),
                Map.entry("isDefault", Boolean.class),
                Map.entry("verifiedAt", LocalDateTime.class),
                Map.entry("disabledAt", LocalDateTime.class)
        ));
        assertThat(declaredFields(Settlement.class)).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("settlementId", UUID.class),
                Map.entry("payeeId", UUID.class),
                Map.entry("currency", String.class),
                Map.entry("grossAmount", Double.class),
                Map.entry("refundAmount", Double.class),
                Map.entry("platformFeeAmount", Double.class),
                Map.entry("payoutAmount", Double.class),
                Map.entry("availableAt", LocalDateTime.class),
                Map.entry("status", SettlementStatus.class),
                Map.entry("settledAt", LocalDateTime.class)
        ));
        assertThat(declaredFields(Payout.class)).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("payoutId", UUID.class),
                Map.entry("settlementId", UUID.class),
                Map.entry("refundId", UUID.class),
                Map.entry("bankAccountId", UUID.class),
                Map.entry("provider", PaymentProvider.class),
                Map.entry("providerPayoutId", String.class),
                Map.entry("referenceId", String.class),
                Map.entry("idempotencyKey", String.class),
                Map.entry("amount", Double.class),
                Map.entry("destinationBankBin", String.class),
                Map.entry("destinationAccountMasked", String.class),
                Map.entry("destinationAccountName", String.class),
                Map.entry("status", PayoutStatus.class),
                Map.entry("requestedAt", LocalDateTime.class),
                Map.entry("completedAt", LocalDateTime.class),
                Map.entry("failureCode", String.class),
                Map.entry("failureReason", String.class)
        ));
    }

    @Test
    void persistenceRelationshipsMatchClassDiagram() throws Exception {
        assertManyToOne(PaymentAttemptEntity.class, "payment", "payment_id", false);
        assertManyToOne(RefundEntity.class, "payment", "payment_id", false);
        assertManyToOne(ProviderWebhookEventEntity.class, "attempt", "attempt_id", true);
        assertOneToOne(PayoutEntity.class, "settlement", "settlement_id", true);
        assertOneToOne(PayoutEntity.class, "refund", "refund_id", true);
        assertManyToOne(PayoutEntity.class, "bankAccount", "bank_account_id", false);

        assertThat(declaredFields(BankAccountEntity.class))
                .containsAllEntriesOf(declaredFields(BankAccount.class));
        assertThat(declaredFields(SettlementEntity.class))
                .containsAllEntriesOf(declaredFields(Settlement.class));
    }

    @Test
    void settlementEnumsMatchClassDiagram() {
        assertThat(enumNames(BankAccountStatus.values())).containsExactly(
                "PENDING_VERIFICATION", "VERIFIED", "REJECTED", "DISABLED"
        );
        assertThat(enumNames(SettlementStatus.values())).containsExactly(
                "PENDING", "HELD", "READY", "PROCESSING", "PAID", "FAILED", "CANCELLED"
        );
        assertThat(enumNames(PayoutStatus.values())).containsExactly(
                "PENDING", "PROCESSING", "SUCCEEDED", "FAILED", "CANCELLED", "MANUAL_REVIEW"
        );
    }

    private void assertManyToOne(
            Class<?> entityType,
            String fieldName,
            String columnName,
            boolean optional
    ) throws Exception {
        Field field = entityType.getDeclaredField(fieldName);
        ManyToOne relation = field.getAnnotation(ManyToOne.class);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);

        assertThat(relation).isNotNull();
        assertThat(relation.optional()).isEqualTo(optional);
        assertThat(joinColumn).isNotNull();
        assertThat(joinColumn.name()).isEqualTo(columnName);
        assertThat(joinColumn.insertable()).isFalse();
        assertThat(joinColumn.updatable()).isFalse();
    }

    private void assertOneToOne(
            Class<?> entityType,
            String fieldName,
            String columnName,
            boolean optional
    ) throws Exception {
        Field field = entityType.getDeclaredField(fieldName);
        OneToOne relation = field.getAnnotation(OneToOne.class);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);

        assertThat(relation).isNotNull();
        assertThat(relation.optional()).isEqualTo(optional);
        assertThat(joinColumn).isNotNull();
        assertThat(joinColumn.name()).isEqualTo(columnName);
        assertThat(joinColumn.insertable()).isFalse();
        assertThat(joinColumn.updatable()).isFalse();
    }

    private Map<String, Class<?>> declaredFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .collect(Collectors.toMap(Field::getName, Field::getType));
    }

    private String[] enumNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toArray(String[]::new);
    }
}
