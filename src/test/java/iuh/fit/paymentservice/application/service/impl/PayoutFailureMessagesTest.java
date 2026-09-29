package iuh.fit.paymentservice.application.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PayoutFailureMessagesTest {

    @Test
    void genericPayosFailureOnAnAccountThatNeverReceivedMoneyPointsAtTheAccountNumber() {
        String message = PayoutFailureMessages.userMessage("PAYOS_PAYOUT_FAILED", "Chi thất bại", "4723", false);

        assertThat(message).contains("•••• 4723").contains("số tài khoản không tồn tại hoặc chọn sai ngân hàng");
        assertThat(message).doesNotContain("Chi thất bại");
    }

    @Test
    void classifiesDestinationPlatformAndUnknownFailures() {
        assertThat(PayoutFailureMessages.classify("ACCOUNT_NOT_FOUND", null))
                .isEqualTo(PayoutFailureMessages.Kind.DESTINATION);
        assertThat(PayoutFailureMessages.classify(null, "Tài khoản đích không hợp lệ"))
                .isEqualTo(PayoutFailureMessages.Kind.DESTINATION);
        assertThat(PayoutFailureMessages.classify("PAYOS_PAYOUT_20", "Số dư không đủ"))
                .isEqualTo(PayoutFailureMessages.Kind.PLATFORM);
        assertThat(PayoutFailureMessages.classify("PAYOS_PAYOUT_403", "Địa chỉ IP không được phép truy cập hệ thống"))
                .isEqualTo(PayoutFailureMessages.Kind.PLATFORM);
        assertThat(PayoutFailureMessages.classify(null, "Chi thất bại"))
                .isEqualTo(PayoutFailureMessages.Kind.UNKNOWN);
        assertThat(PayoutFailureMessages.userMessage(null, "Chi thất bại", "0014", true))
                .isEqualTo("Ngân hàng từ chối chuyển tiền vào tài khoản •••• 0014. Vui lòng thử lại sau ít phút hoặc chọn tài khoản khác.");
    }
}
