package iuh.fit.paymentservice.infrastructure.persistence.mapper;

import iuh.fit.paymentservice.domain.entity.Payout;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PayoutEntity;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PayoutEntityMapperTests {
    private final PayoutEntityMapper mapper = new PayoutEntityMapperImpl();

    /**
     * Chi lại sau lỗi đổi sang tài khoản mặc định mới. Trước đây updateEntity bỏ qua bankAccountId: payOS chuyển tới
     * tài khoản mới nhưng payout vẫn ghi tài khoản cũ, và việc xác minh / từ chối tài khoản rơi nhầm vào tài khoản cũ.
     */
    @Test
    void retryingAPayoutStoresTheNewDestinationAccount() {
        UUID refundId = UUID.randomUUID();
        UUID oldAccount = UUID.randomUUID();
        UUID newAccount = UUID.randomUUID();
        PayoutEntity stored = new PayoutEntity();
        stored.setRefundId(refundId);
        stored.setBankAccountId(oldAccount);
        stored.setDestinationAccountMasked("****6077");
        stored.setStatus(PayoutStatus.FAILED);

        mapper.updateEntity(Payout.builder()
                .refundId(UUID.randomUUID())
                .bankAccountId(newAccount)
                .destinationAccountMasked("****4723")
                .status(PayoutStatus.PENDING)
                .build(), stored);

        assertThat(stored.getBankAccountId()).isEqualTo(newAccount);
        assertThat(stored.getDestinationAccountMasked()).isEqualTo("****4723");
        assertThat(stored.getRefundId()).isEqualTo(refundId); // payout vẫn thuộc khoản hoàn cũ
    }
}
