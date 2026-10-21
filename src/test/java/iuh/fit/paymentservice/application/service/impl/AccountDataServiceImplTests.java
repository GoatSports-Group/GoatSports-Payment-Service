package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.port.persistence.BankAccountRepository;
import iuh.fit.paymentservice.application.port.persistence.RefundRepository;
import iuh.fit.paymentservice.domain.entity.BankAccount;
import iuh.fit.paymentservice.domain.entity.Refund;
import iuh.fit.paymentservice.domain.enums.BankAccountStatus;
import iuh.fit.paymentservice.domain.enums.RefundStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDataServiceImplTests {
    @Mock private RefundRepository refundRepository;
    @Mock private BankAccountRepository bankAccountRepository;
    @InjectMocks private AccountDataServiceImpl service;

    private final UUID userId = UUID.randomUUID();

    @Test
    void aRefundThatHasNotReachedThePlayerBlocksDeletion() {
        when(refundRepository.findByPayerId(userId)).thenReturn(List.of(
                Refund.builder().status(RefundStatus.SUCCEEDED).build(),
                Refund.builder().status(RefundStatus.FAILED).build()));
        assertThat(service.deletionBlockers(userId)).containsExactly("REFUND_IN_PROGRESS");

        when(refundRepository.findByPayerId(userId)).thenReturn(List.of(
                Refund.builder().status(RefundStatus.SUCCEEDED).build()));
        assertThat(service.deletionBlockers(userId)).isEmpty();
    }

    @Test
    void erasingDisablesLinkedAccountsAndDropsTheAccountNumberOnce() {
        BankAccount account = BankAccount.builder().bankAccountId(UUID.randomUUID()).payeeId(userId)
                .bankBin("970436").accountNumberEncrypted("cipher").accountNumberHash("hash")
                .accountNumberLast4("6789").accountName("NGUYEN MINH").status(BankAccountStatus.VERIFIED)
                .isDefault(true).build();
        when(bankAccountRepository.findByPayeeId(userId)).thenReturn(List.of(account));

        service.eraseUserData(userId);

        assertThat(account.getStatus()).isEqualTo(BankAccountStatus.DISABLED);
        assertThat(account.getIsDefault()).isFalse();
        assertThat(account.getAccountName()).isEmpty();
        assertThat(account.getAccountNumberEncrypted()).isEmpty();
        assertThat(account.getAccountNumberHash()).isEqualTo("erased:" + account.getBankAccountId());
        assertThat(account.getAccountNumberLast4()).isEqualTo("6789");
        verify(bankAccountRepository).save(account);

        // Kafka giao lại cùng sự kiện: không ghi thêm lần nữa.
        org.mockito.Mockito.clearInvocations(bankAccountRepository);
        service.eraseUserData(userId);
        verify(bankAccountRepository, never()).save(any());
    }
}
