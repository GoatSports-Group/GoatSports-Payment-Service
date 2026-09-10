package iuh.fit.paymentservice.infrastructure.external.adapter;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import iuh.fit.paymentservice.application.dto.request.webhook.ProviderCheckoutCommand;
import iuh.fit.paymentservice.application.dto.response.webhook.ProviderCheckoutResult;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.application.port.client.PaymentCheckoutPort;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.infrastructure.external.config.PayOsClientProvider;
import iuh.fit.paymentservice.infrastructure.external.config.PayOsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class PayOsCheckoutAdapter implements PaymentCheckoutPort {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int QR_SIZE = 420;

    private final PayOsProperties properties;
    private final PayOsClientProvider clientProvider;

    @Override
    public ProviderCheckoutResult createCheckout(ProviderCheckoutCommand command) {
        properties.requireEnabled();
        if (command.getMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new PaymentProviderException(
                    "UNSUPPORTED_PAYMENT_METHOD",
                    "payOS chỉ hỗ trợ phương thức BANK_TRANSFER"
            );
        }
        if (command.getOrderCode() == null || command.getOrderCode() <= 0) {
            throw new PaymentProviderException("PAYOS_ORDER_CODE_REQUIRED", "Thiếu orderCode payOS");
        }

        long amount = providerAmount(command.getAmount());
        CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                .orderCode(command.getOrderCode())
                .amount(amount)
                .description(description(command.getOrderCode()))
                .returnUrl(properties.getReturnUrl())
                .cancelUrl(properties.getCancelUrl())
                .expiredAt(command.getPaymentExpiresAt() == null
                        ? null
                        : command.getPaymentExpiresAt().atZone(BUSINESS_ZONE).toEpochSecond())
                .build();

        try {
            CreatePaymentLinkResponse response = clientProvider.get().paymentRequests().create(request);
            ensureValidResponse(response, command.getOrderCode(), amount);
            return new ProviderCheckoutResult(
                    response.getCheckoutUrl(),
                    null,
                    qrDataUri(response.getQrCode()),
                    response.getPaymentLinkId()
            );
        } catch (PaymentProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PaymentProviderException(
                    "PAYOS_CHECKOUT_ERROR",
                    "Không thể tạo liên kết thanh toán payOS",
                    exception
            );
        }
    }

    private void ensureValidResponse(CreatePaymentLinkResponse response, long orderCode, long amount) {
        if (response == null
                || response.getOrderCode() == null
                || response.getOrderCode() != orderCode
                || response.getAmount() == null
                || response.getAmount() != amount
                || response.getPaymentLinkId() == null
                || response.getPaymentLinkId().isBlank()
                || response.getCheckoutUrl() == null
                || response.getCheckoutUrl().isBlank()
                || response.getQrCode() == null
                || response.getQrCode().isBlank()) {
            throw new PaymentProviderException(
                    "PAYOS_INVALID_RESPONSE",
                    "payOS trả về dữ liệu checkout không hợp lệ"
            );
        }
    }

    private long providerAmount(Double amount) {
        try {
            long value = BigDecimal.valueOf(amount)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
            if (value <= 0) {
                throw new ArithmeticException("non-positive");
            }
            return value;
        } catch (RuntimeException exception) {
            throw new PaymentProviderException("PAYOS_INVALID_AMOUNT", "Số tiền payOS không hợp lệ");
        }
    }

    private String description(long orderCode) {
        String token = Long.toUnsignedString(orderCode, 36).toUpperCase();
        return "GS" + token.substring(Math.max(0, token.length() - 7));
    }

    private String qrDataUri(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            BufferedImage image = new BufferedImage(QR_SIZE, QR_SIZE, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < QR_SIZE; x++) {
                for (int y = 0; y < QR_SIZE; y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (WriterException | java.io.IOException exception) {
            throw new PaymentProviderException(
                    "PAYOS_QR_RENDER_ERROR",
                    "Không thể render mã QR payOS",
                    exception
            );
        }
    }
}
