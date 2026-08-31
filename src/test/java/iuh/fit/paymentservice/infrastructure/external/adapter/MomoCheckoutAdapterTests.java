package iuh.fit.paymentservice.infrastructure.external.adapter;

import iuh.fit.paymentservice.application.dto.request.webhook.ProviderCheckoutCommand;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.infrastructure.external.config.MomoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MomoCheckoutAdapterTests {
    private static final UUID ATTEMPT_ID = UUID.fromString("27bbad9f-70c8-4482-94d1-207b50339a61");
    private static final String API_URL = "https://test-payment.momo.vn/v2/gateway/api/create";
    private static final String PAY_URL = "https://test-payment.momo.vn/v2/gateway/pay?t=test-token";

    private MomoProperties properties;
    private MockRestServiceServer server;
    private MomoCheckoutAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = properties();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new MomoCheckoutAdapter(properties, builder.build());
    }

    @Test
    void createsSignedCaptureWalletRequestAndReturnsPayUrl() {
        String orderId = ATTEMPT_ID.toString().replace("-", "");
        String orderInfo = "GoatSports payment 123";
        String rawSignature = adapter.rawRequestSignature(150_000L, orderId, orderInfo, orderId);
        String signature = adapter.hmacSha256(properties.getSecretKey(), rawSignature);
        String responseSignature = responseSignature(orderId, "Successful.");

        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "partnerCode":"MOMO_TEST",
                          "storeName":"GoatSports Test",
                          "storeId":"GoatSportsTest",
                          "requestId":"%s",
                          "amount":150000,
                          "orderId":"%s",
                          "orderInfo":"GoatSports payment 123",
                          "redirectUrl":"https://client.goatsports.test/payment/momo-return",
                          "ipnUrl":"https://api.goatsports.test/payment-service/api/v1/payments/providers/momo/webhook",
                          "requestType":"captureWallet",
                          "extraData":"",
                          "autoCapture":true,
                          "lang":"vi",
                          "signature":"%s"
                        }
                        """.formatted(orderId, orderId, signature)))
                .andRespond(withSuccess("""
                        {
                          "partnerCode":"MOMO_TEST",
                          "requestId":"%s",
                          "orderId":"%s",
                          "amount":150000,
                          "responseTime":1787840000000,
                          "message":"Successful.",
                          "resultCode":0,
                          "payUrl":"%s",
                          "signature":"%s"
                        }
                        """.formatted(orderId, orderId, PAY_URL, responseSignature), MediaType.APPLICATION_JSON));

        String checkoutUrl = adapter.createCheckoutUrl(command(orderInfo));

        assertThat(checkoutUrl).isEqualTo(PAY_URL);
        server.verify();
    }

    @Test
    void rejectsProviderResponseWithInvalidSignature() {
        String orderId = ATTEMPT_ID.toString().replace("-", "");
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess("""
                        {
                          "partnerCode":"MOMO_TEST",
                          "requestId":"%s",
                          "orderId":"%s",
                          "amount":150000,
                          "responseTime":1787840000000,
                          "message":"Successful.",
                          "resultCode":0,
                          "payUrl":"%s",
                          "signature":"%s"
                        }
                        """.formatted(orderId, orderId, PAY_URL, "0".repeat(64)), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.createCheckoutUrl(command("GoatSports payment 123")))
                .isInstanceOf(PaymentProviderException.class)
                .hasMessage("Chữ ký response từ MoMo không hợp lệ");
        server.verify();
    }

    @Test
    void rejectsProviderResponseThatDoesNotMatchRequest() {
        String orderId = ATTEMPT_ID.toString().replace("-", "");
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess("""
                        {
                          "partnerCode":"MOMO_TEST",
                          "requestId":"%s",
                          "orderId":"another-order",
                          "amount":150000,
                          "responseTime":1787840000000,
                          "message":"Successful.",
                          "resultCode":0,
                          "payUrl":"%s"
                        }
                        """.formatted(orderId, PAY_URL), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.createCheckoutUrl(command("GoatSports payment 123")))
                .isInstanceOf(PaymentProviderException.class)
                .hasMessage("Thông tin response từ MoMo không khớp với request");
        server.verify();
    }

    private ProviderCheckoutCommand command(String orderInfo) {
        return ProviderCheckoutCommand.builder()
                .attemptId(ATTEMPT_ID)
                .amount(150_000D)
                .currency("VND")
                .orderInfo(orderInfo)
                .ipAddress("203.0.113.10")
                .locale("vn")
                .method(PaymentMethod.E_WALLET)
                .initiatedAt(LocalDateTime.of(2026, 8, 27, 20, 0))
                .build();
    }

    private MomoProperties properties() {
        MomoProperties value = new MomoProperties();
        value.setEnabled(true);
        value.setApiUrl(API_URL);
        value.setRedirectUrl("https://client.goatsports.test/payment/momo-return");
        value.setIpnUrl("https://api.goatsports.test/payment-service/api/v1/payments/providers/momo/webhook");
        value.setPartnerCode("MOMO_TEST");
        value.setAccessKey("test-momo-access-key");
        value.setSecretKey("test-momo-secret-key");
        value.setStoreName("GoatSports Test");
        value.setStoreId("GoatSportsTest");
        value.setRequestType("captureWallet");
        value.setTimeoutSeconds(30);
        return value;
    }

    private String responseSignature(String orderId, String message) {
        String rawSignature = "accessKey=" + properties.getAccessKey()
                + "&amount=150000"
                + "&message=" + message
                + "&orderId=" + orderId
                + "&partnerCode=" + properties.getPartnerCode()
                + "&payUrl=" + PAY_URL
                + "&requestId=" + orderId
                + "&responseTime=1787840000000"
                + "&resultCode=0";
        return adapter.hmacSha256(properties.getSecretKey(), rawSignature);
    }
}
