package iuh.fit.paymentservice.application.service.impl;

import java.util.List;
import java.util.Locale;

/**
 * Lý do chi tiền payOS trả về (vd. "Chi thất bại", mã lỗi thô) không cho người nhận biết phải làm gì.
 * Phân loại thành: tài khoản nhận có vấn đề (người nhận phải sửa), lỗi phía GoatSports (người nhận chỉ cần thử lại),
 * hoặc chưa rõ. Lý do thô vẫn giữ ở {@code payout.failureReason} để đối soát.
 */
public final class PayoutFailureMessages {
    public enum Kind { DESTINATION, PLATFORM, UNKNOWN }

    // ponytail: nhận diện theo từ khóa trong mã/lý do payOS; thêm mã cụ thể khi payOS công bố bảng mã lỗi payout.
    private static final List<String> DESTINATION_HINTS = List.of(
            "account_not_found", "invalid_account", "destination", "not exist",
            "không tồn tại", "khong ton tai", "không tìm thấy", "khong tim thay",
            "tài khoản đích", "tai khoan dich", "tài khoản nhận", "tai khoan nhan",
            "số tài khoản", "so tai khoan", "sai tài khoản", "sai tai khoan"
    );
    private static final List<String> PLATFORM_HINTS = List.of(
            "số dư", "so du", "insufficient", "balance", "hạn mức", "han muc", "limit",
            "unauthorized", "forbidden", "timeout", "unavailable", "không thể kết nối",
            "không được phép", "địa chỉ ip", "_401", "_403", "_429", "_500", "_502", "_503", "_504"
    );

    private PayoutFailureMessages() {
    }

    public static Kind classify(String code, String reason) {
        String text = ((code == null ? "" : code) + " " + (reason == null ? "" : reason)).toLowerCase(Locale.ROOT);
        if (DESTINATION_HINTS.stream().anyMatch(text::contains)) return Kind.DESTINATION;
        if (PLATFORM_HINTS.stream().anyMatch(text::contains)) return Kind.PLATFORM;
        return Kind.UNKNOWN;
    }

    /**
     * @param accountLast4    4 số cuối tài khoản nhận (null nếu không rõ)
     * @param accountVerified tài khoản đã từng nhận tiền thành công chưa: chưa từng thì lỗi chung chung gần như
     *                        luôn là sai số tài khoản hoặc sai ngân hàng
     */
    public static String userMessage(String code, String reason, String accountLast4, boolean accountVerified) {
        String account = accountLast4 == null || accountLast4.isBlank()
                ? "tài khoản đã chọn" : "tài khoản •••• " + accountLast4;
        return switch (classify(code, reason)) {
            case DESTINATION -> "Số tài khoản ngân hàng không tồn tại hoặc không nhận được tiền (" + account
                    + "). Kiểm tra lại số tài khoản và ngân hàng, hoặc chọn tài khoản khác.";
            case PLATFORM -> "GoatSports tạm thời chưa chuyển được khoản này. Lỗi ở phía hệ thống, không phải tài khoản "
                    + "của bạn. Vui lòng thử lại sau ít phút.";
            case UNKNOWN -> accountVerified
                    ? "Ngân hàng từ chối chuyển tiền vào " + account + ". Vui lòng thử lại sau ít phút hoặc chọn tài khoản khác."
                    : "Ngân hàng từ chối chuyển tiền vào " + account + ". Tài khoản này chưa từng nhận tiền thành công, "
                    + "thường là do số tài khoản không tồn tại hoặc chọn sai ngân hàng. Kiểm tra lại hoặc chọn tài khoản khác.";
        };
    }
}
