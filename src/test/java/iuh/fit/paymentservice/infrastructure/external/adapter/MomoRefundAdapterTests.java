package iuh.fit.paymentservice.infrastructure.external.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.paymentservice.application.dto.request.refund.ProviderRefundCommand;
import iuh.fit.paymentservice.application.dto.response.refund.ProviderRefundResult;
import iuh.fit.paymentservice.application.dto.response.refund.ProviderRefundStatus;
import iuh.fit.paymentservice.infrastructure.external.config.MomoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MomoRefundAdapterTests {
    private static final UUID REFUND_ID = UUID.fromString("8dc43b3f-1308-4e51-91d2-8dc42b099707");
    private static final String REFUND_URL = "https://test-payment.momo.vn/v2/gateway/api/refund";
    private static final String QUERY_URL = "https://test-payment.momo.vn/v2/gateway/api/refund/query";

    private MomoProperties properties;
    private MomoSignatureService signatureService;
    private MockRestServiceServer server;
    private MomoRefundAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = properties();
        signatureService = new MomoSignatureService();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new MomoRefundAdapter(properties, signatureService, builder.build());
    }

    @Test
    void sendsOfficialSignedRefundRequestAndMapsSuccess() {
        String reference = reference();
        String description = "Hoan tien dat san";
        String rawSignature = "accessKey=test-momo-access-key"
                + "&amount=50000"
                + "&description=" + description
                + "&orderId=" + reference
                + "&partnerCode=MOMO_TEST"
                + "&requestId=" + reference
                + "&transId=270820260001";
        String signature = signatureService.hmacSha256(properties.getSecretKey(), rawSignature);
        server.expect(requestTo(REFUND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "partnerCode":"MOMO_TEST",
                          "orderId":"%s",
                          "requestId":"%s",
                          "amount":50000,
                          "transId":270820260001,
                          "lang":"vi",
                          "description":"%s",
                          "signature":"%s"
                        }
                        """.formatted(reference, reference, description, signature)))
                .andRespond(withSuccess("""
                        {
                          "partnerCode":"MOMO_TEST",
                          "orderId":"%s",
                          "requestId":"%s",
                          "amount":50000,
                          "transId":280820260002,
                          "resultCode":0,
                          "message":"Successful.",
                          "responseTime":1787840000000
                        }
                        """.formatted(reference, reference), MediaType.APPLICATION_JSON));

        ProviderRefundResult result = adapter.refund(ProviderRefundCommand.builder()
                .refundId(REFUND_ID)
                .amount(50_000D)
                .providerTransactionId("270820260001")
                .description(description)
                .locale("vi")
                .build());

        assertThat(result.getStatus()).isEqualTo(ProviderRefundStatus.SUCCEEDED);
        assertThat(result.getProviderRefundId()).isEqualTo("280820260002");
        assertThat(result.getFailureReason()).isNull();
        server.verify();
    }

    @Test
    void queryUsesOfficialSignatureAndMapsPendingRefundTransaction() {
        String reference = reference();
        String[] requestId = new String[1];
        server.expect(requestTo(QUERY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    JsonNode body = new ObjectMapper().readTree(
                            ((MockClientHttpRequest) request).getBodyAsString()
                    );
                    requestId[0] = body.path("requestId").asText();
                    assertThat(requestId[0]).startsWith("Q").hasSize(33);
                    assertThat(body.path("partnerCode").asText()).isEqualTo("MOMO_TEST");
                    assertThat(body.path("orderId").asText()).isEqualTo(reference);
                    assertThat(body.path("lang").asText()).isEqualTo("vi");
                    String rawSignature = "accessKey=test-momo-access-key"
                            + "&orderId=" + reference
                            + "&partnerCode=MOMO_TEST"
                            + "&requestId=" + requestId[0];
                    assertThat(body.path("signature").asText()).isEqualTo(
                            signatureService.hmacSha256(properties.getSecretKey(), rawSignature)
                    );
                })
                .andRespond(request -> withSuccess("""
                        {
                          "partnerCode":"MOMO_TEST",
                          "orderId":"%s",
                          "requestId":"%s",
                          "resultCode":0,
                          "message":"Query successful.",
                          "responseTime":1787840000000,
                          "refundTrans":[{
                            "orderId":"%s",
                            "amount":50000,
                            "resultCode":7002,
                            "transId":280820260002,
                            "createdTime":1787840000000
                          }]
                        }
                        """.formatted(reference, requestId[0], reference), MediaType.APPLICATION_JSON)
                        .createResponse(request));

        ProviderRefundResult result = adapter.query(REFUND_ID);

        assertThat(result.getStatus()).isEqualTo(ProviderRefundStatus.PENDING);
        assertThat(result.getProviderRefundId()).isEqualTo("280820260002");
        assertThat(result.getFailureReason()).contains("MOMO_RESULT_7002");
        server.verify();
    }

    private String reference() {
        return REFUND_ID.toString().replace("-", "");
    }

    private MomoProperties properties() {
        MomoProperties value = new MomoProperties();
        value.setEnabled(true);
        value.setRefundUrl(REFUND_URL);
        value.setRefundQueryUrl(QUERY_URL);
        value.setPartnerCode("MOMO_TEST");
        value.setAccessKey("test-momo-access-key");
        value.setSecretKey("test-momo-secret-key");
        value.setTimeoutSeconds(30);
        return value;
    }
}
