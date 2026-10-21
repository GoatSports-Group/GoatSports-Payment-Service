package iuh.fit.paymentservice.application.service;

import java.util.List;
import java.util.UUID;

/** Phần của payment-service trong luồng xóa tài khoản (auth-service điều phối). */
public interface AccountDataService {
    /** Mã các việc còn dang dở ở payment-service chặn việc xóa tài khoản. */
    List<String> deletionBlockers(UUID userId);

    /** Tài khoản đã bị ẩn danh hóa: tắt và xóa số tài khoản ngân hàng đã liên kết. Gọi lại nhiều lần vẫn an toàn. */
    void eraseUserData(UUID userId);
}
