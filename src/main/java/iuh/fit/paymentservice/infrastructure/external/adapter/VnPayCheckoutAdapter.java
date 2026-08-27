package iuh.fit.paymentservice.infrastructure.external.adapter;

import iuh.fit.paymentservice.application.dto.request.ProviderCheckoutCommand;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.application.port.client.PaymentCheckoutPort;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.infrastructure.external.config.VnPayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class VnPayCheckoutAdapter implements PaymentCheckoutPort {
    private static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final VnPayProperties properties;

    @Override
    public String createCheckoutUrl(ProviderCheckoutCommand command) {
        properties.requireEnabled();

        String amount = providerAmount(command.getAmount());
        LocalDateTime expiresAt = checkoutExpiresAt(command);

        Map<String, String> parameters = new TreeMap<>();
        parameters.put("vnp_Version", properties.getVersion());
        parameters.put("vnp_Command", properties.getCommand());
        parameters.put("vnp_TmnCode", properties.getTmnCode());
        parameters.put("vnp_Amount", amount);
        parameters.put("vnp_BankCode", bankCode(command.getMethod()));
        parameters.put("vnp_CreateDate", command.getInitiatedAt().format(VNPAY_DATE_FORMAT));
        parameters.put("vnp_CurrCode", command.getCurrency());
        parameters.put("vnp_IpAddr", required(command.getIpAddress(), "VNPAY_IP_ADDRESS_REQUIRED"));
        parameters.put("vnp_Locale", locale(command.getLocale()));
        parameters.put("vnp_OrderInfo", orderInfo(command.getOrderInfo()));
        parameters.put("vnp_OrderType", properties.getOrderType());
        parameters.put("vnp_ReturnUrl", properties.getReturnUrl());
        parameters.put("vnp_TxnRef", command.getAttemptId().toString().replace("-", ""));
        parameters.put("vnp_ExpireDate", expiresAt.format(VNPAY_DATE_FORMAT));

        String canonicalQuery = canonicalQuery(parameters);
        String signature = hmacSha512(properties.getHashSecret(), canonicalQuery);
        String separator = properties.getPaymentUrl().contains("?") ? "&" : "?";
        return properties.getPaymentUrl()
                + separator
                + canonicalQuery
                + "&vnp_SecureHash="
                + signature;
    }

    String canonicalQuery(Map<String, String> parameters) {
        return parameters.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    String hmacSha512(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new PaymentProviderException(
                    "VNPAY_SIGNATURE_ERROR",
                    "Không thể ký yêu cầu thanh toán VNPAY",
                    exception
            );
        }
    }

    private String providerAmount(Double amount) {
        if (amount == null || !Double.isFinite(amount) || amount <= 0) {
            throw new PaymentProviderException("VNPAY_INVALID_AMOUNT", "Số tiền gửi VNPAY không hợp lệ");
        }
        try {
            String value = BigDecimal.valueOf(amount)
                    .movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .toBigIntegerExact()
                    .toString();
            if (value.length() > 12) {
                throw new PaymentProviderException(
                        "VNPAY_INVALID_AMOUNT",
                        "Số tiền vượt giới hạn 12 chữ số của VNPAY"
                );
            }
            return value;
        } catch (ArithmeticException exception) {
            throw new PaymentProviderException(
                    "VNPAY_INVALID_AMOUNT",
                    "Số tiền có quá hai chữ số thập phân",
                    exception
            );
        }
    }

    private LocalDateTime checkoutExpiresAt(ProviderCheckoutCommand command) {
        LocalDateTime initiatedAt = command.getInitiatedAt();
        LocalDateTime providerExpiry = initiatedAt.plusMinutes(properties.getExpirationMinutes());
        LocalDateTime paymentExpiry = command.getPaymentExpiresAt();
        LocalDateTime expiresAt = paymentExpiry != null && paymentExpiry.isBefore(providerExpiry)
                ? paymentExpiry
                : providerExpiry;
        if (!expiresAt.isAfter(initiatedAt)) {
            throw new PaymentProviderException("PAYMENT_EXPIRED", "Payment đã hết hạn");
        }
        return expiresAt;
    }

    private String bankCode(PaymentMethod method) {
        if (method == null) {
            throw new PaymentProviderException("PAYMENT_METHOD_REQUIRED", "Phương thức thanh toán là bắt buộc");
        }
        return switch (method) {
            case BANK_TRANSFER -> "VNBANK";
            case E_WALLET -> "VNPAYQR";
            case CASH -> throw new PaymentProviderException(
                    "UNSUPPORTED_PAYMENT_METHOD",
                    "VNPAY không hỗ trợ phương thức CASH"
            );
        };
    }

    private String locale(String locale) {
        String normalized = locale == null || locale.isBlank() ? "vn" : locale.toLowerCase();
        if (!normalized.equals("vn") && !normalized.equals("en")) {
            throw new PaymentProviderException("VNPAY_INVALID_LOCALE", "VNPAY chỉ hỗ trợ locale vn hoặc en");
        }
        return normalized;
    }

    private String orderInfo(String value) {
        String orderInfo = required(value, "VNPAY_ORDER_INFO_REQUIRED");
        if (orderInfo.length() > 255 || !orderInfo.matches("[A-Za-z0-9 ]+")) {
            throw new PaymentProviderException(
                    "VNPAY_INVALID_ORDER_INFO",
                    "Nội dung thanh toán VNPAY phải không dấu, không ký tự đặc biệt và tối đa 255 ký tự"
            );
        }
        return orderInfo;
    }

    private String required(String value, String code) {
        if (value == null || value.isBlank()) {
            throw new PaymentProviderException(code, "Thiếu dữ liệu bắt buộc để tạo URL VNPAY");
        }
        return value;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
