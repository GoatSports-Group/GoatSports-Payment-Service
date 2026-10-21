package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.port.persistence.BankAccountRepository;
import iuh.fit.paymentservice.application.port.persistence.RefundRepository;
import iuh.fit.paymentservice.application.service.AccountDataService;
import iuh.fit.paymentservice.domain.entity.BankAccount;
import iuh.fit.paymentservice.domain.enums.BankAccountStatus;
import iuh.fit.paymentservice.domain.enums.RefundStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountDataServiceImpl implements AccountDataService {
    private final RefundRepository refundRepository;
    private final BankAccountRepository bankAccountRepository;

    @Override
    public List<String> deletionBlockers(UUID userId) {
        // Khoản hoàn chưa về tới người chơi (kể cả đang lỗi, chờ duyệt tay): xóa tài khoản là mất luôn chỗ nhận tiền.
        boolean refundOwed = refundRepository.findByPayerId(userId).stream()
                .anyMatch(refund -> refund.getStatus() != RefundStatus.SUCCEEDED);
        return refundOwed ? List.of("REFUND_IN_PROGRESS") : List.of();
    }

    @Override
    @Transactional
    public void eraseUserData(UUID userId) {
        // Giữ bản ghi (lịch sử chi tiền trỏ tới bankAccountId), chỉ xóa phần định danh; 4 số cuối + ngân hàng ở lại cho
        // lịch sử. Hash phải khác nhau vì unique (payee, bank, hash).
        for (BankAccount account : bankAccountRepository.findByPayeeId(userId)) {
            if (account.getAccountNumberHash() != null && account.getAccountNumberHash().startsWith("erased:")) continue;
            account.setStatus(BankAccountStatus.DISABLED);
            account.setIsDefault(false);
            if (account.getDisabledAt() == null) account.setDisabledAt(LocalDateTime.now());
            account.setAccountName("");
            account.setAccountNumberEncrypted("");
            account.setAccountNumberHash("erased:" + account.getBankAccountId());
            bankAccountRepository.save(account);
        }
    }
}
