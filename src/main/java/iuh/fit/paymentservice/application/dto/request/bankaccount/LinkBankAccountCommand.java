package iuh.fit.paymentservice.application.dto.request.bankaccount;

import iuh.fit.paymentservice.application.exception.InvalidPaymentException;

import java.util.UUID;
import java.util.regex.Pattern;

public record LinkBankAccountCommand(
        UUID payeeId,
        String bankBin,
        String accountNumber,
        String accountName
) {
    private static final Pattern BIN_PATTERN = Pattern.compile("\\d{6,12}");
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("\\d{6,19}");

    public void validate() {
        if (payeeId == null
                || bankBin == null || !BIN_PATTERN.matcher(bankBin.trim()).matches()
                || accountNumber == null
                || !ACCOUNT_PATTERN.matcher(accountNumber.replaceAll("\\s+", "")).matches()
                || accountName == null || accountName.isBlank()
                || accountName.trim().length() > 255) {
            throw new InvalidPaymentException(
                    "BANK_ACCOUNT_INVALID",
                    "Ngân hàng, số tài khoản và tên chủ tài khoản không hợp lệ"
            );
        }
    }
}
