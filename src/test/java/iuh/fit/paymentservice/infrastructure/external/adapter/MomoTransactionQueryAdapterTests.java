package iuh.fit.paymentservice.infrastructure.external.adapter;

import iuh.fit.paymentservice.application.dto.request.webhook.ProviderTransactionQueryCommand;
import iuh.fit.paymentservice.application.dto.response.webhook.ProviderTransactionStatus;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.infrastructure.external.config.MomoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MomoTransactionQueryAdapterTests {
    private static final UUID ATTEMPT_ID = UUID.fromString("27bbad9f-70c8-4482-94d1-207b50339a61");
    private static final String QUERY_URL = "https://test-payment.momo.vn/v2/gateway/api/query";

    private MomoProperties properties;
    private MomoSignatureService signatureService;
    private MockRestServiceServer server;
    private MomoTransactionQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = properties();
        signatureService = new MomoSignatureService();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new MomoTransactionQueryAdapter(properties, signatureService, builder.build());
    }

    @Test
    void sendsSignedQueryAndMapsProviderStatus() {
        String reference = ATTEMPT_ID.toString().replace("-", "");
        String signature = signatureService.hmacSha256(
                properties.getSecretKey(),
                adapter.rawRequestSignature(reference, reference)
        );
        server.expect(requestTo(QUERY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "partnerCode":"MOMO_TEST",
                          "requestId":"%s",
                          "orderId":"%s",
                          "signature":"%s",
                          "lang":"vi"
                        }
                        """.formatted(reference, reference, signature)))
                .andRespond(withSuccess("""
                        {
                          "partnerCode":"MOMO_TEST",
                          "requestId":"%s",
                          "orderId":"%s",
                          "amount":150000,
                          "transId":270820260001,
                          "payType":"qr",
                          "resultCode":0,
                          "message":"Successful.",
                          "responseTime":1787840000000
                        }
                        """.formatted(reference, reference), MediaType.APPLICATION_JSON));

        ProviderTransactionStatus status = adapter.query(command());

        assertThat(status.getProviderTransactionId()).isEqualTo("270820260001");
        assertThat(status.getAmount()).isEqualTo(150000L);
        assertThat(status.getResultCode()).isZero();
        assertThat(status.getMessage()).isEqualTo("Successful.");
        assertThat(status.getOccurredAt()).isNotNull();
        server.verify();
    }

    @Test
    void technicalProviderResultIsRetriedInsteadOfTurningPaymentIntoFailed() {
        String reference = ATTEMPT_ID.toString().replace("-", "");
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess("""
                        {
                          "partnerCode":"MOMO_TEST",
                          "requestId":"%s",
                          "orderId":"%s",
                          "amount":150000,
                          "transId":0,
                          "resultCode":10,
                          "message":"System maintenance.",
                          "responseTime":1787840000000
                        }
                        """.formatted(reference, reference), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.query(command()))
                .isInstanceOf(PaymentProviderException.class)
                .hasMessageContaining("MoMo chưa thể xác nhận giao dịch");
        server.verify();
    }

    private ProviderTransactionQueryCommand command() {
        return ProviderTransactionQueryCommand.builder()
                .attemptId(ATTEMPT_ID)
                .expectedAmount(150_000D)
                .locale("vi")
                .build();
    }

    private MomoProperties properties() {
        MomoProperties value = new MomoProperties();
        value.setEnabled(true);
        value.setQueryUrl(QUERY_URL);
        value.setPartnerCode("MOMO_TEST");
        value.setAccessKey("test-momo-access-key");
        value.setSecretKey("test-momo-secret-key");
        value.setTimeoutSeconds(30);
        return value;
    }
}
