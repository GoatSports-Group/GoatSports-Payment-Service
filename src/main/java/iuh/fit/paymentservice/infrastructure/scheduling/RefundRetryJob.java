package iuh.fit.paymentservice.infrastructure.scheduling;

import iuh.fit.paymentservice.application.service.RefundDisbursementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tự chi lại các khoản hoàn bị kẹt: payOS lỗi tạm (vd. IP chưa được whitelist) hoặc payout bị từ chối dù tài khoản
 * vẫn hợp lệ. Mỗi khoản xử lý riêng qua proxy của service để có transaction riêng; lỗi một khoản không chặn khoản khác.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundRetryJob {
    private final RefundDisbursementService refundDisbursementService;

    @Scheduled(fixedDelayString = "${payment.refund.retry-delay-ms:300000}", initialDelay = 60_000)
    public void retryStuckRefunds() {
        for (UUID refundId : refundDisbursementService.findRetryableRefundIds(LocalDateTime.now())) {
            try {
                if (!refundDisbursementService.recordRetryAttempt(refundId)) continue;
                refundDisbursementService.retry(refundId);
                log.info("Đã tự chi lại khoản hoàn {}", refundId);
            } catch (RuntimeException exception) {
                log.warn("Tự chi lại khoản hoàn {} chưa được: {}", refundId, exception.getMessage());
            }
        }
    }
}
