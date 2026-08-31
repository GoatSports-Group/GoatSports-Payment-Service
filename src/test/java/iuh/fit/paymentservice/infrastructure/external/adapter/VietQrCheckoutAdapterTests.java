package iuh.fit.paymentservice.infrastructure.external.adapter;

import iuh.fit.paymentservice.application.dto.request.webhook.ProviderCheckoutCommand;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.infrastructure.external.config.VietQrProperties;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VietQrCheckoutAdapterTests {
    private static final String API_URL = "https://api.vietqr.io/v2/generate";
    private static final UUID ATTEMPT_ID = UUID.fromString("27bbad9f-70c8-4482-94d1-207b50339a61");

    private MockRestServiceServer server;
    private VietQrCheckoutAdapter adapter;

    @BeforeEach
    void setUp() {
        VietQrProperties properties = properties();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new VietQrCheckoutAdapter(properties, builder.build());
    }

    @Test
    void sendsServerSideCredentialsAndReturnsQrDataUri() {
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-client-id", "client-id"))
                .andExpect(header("x-api-key", "api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "accountNo":"123456789",
                          "accountName":"GOAT SPORTS",
                          "acqId":"970415",
                          "amount":100000,
                          "addInfo":"GS27BBAD9F70C8448294D1",
                          "format":"text",
                          "template":"compact2"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "code":"00",
                          "desc":"Gen VietQR successful!",
                          "data":{
                            "acpId":"970415",
                            "accountName":"GOAT SPORTS",
                            "qrCode":"000201010212...",
                            "qrDataURL":"data:image/png;base64,TEST"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = adapter.createCheckout(command());

        assertThat(result.checkoutUrl()).isNull();
        assertThat(result.deeplink()).isNull();
        assertThat(result.qrCodeContent()).isEqualTo("data:image/png;base64,TEST");
        server.verify();
    }

    @Test
    void rejectsProviderBusinessError() {
        server.expect(requestTo(API_URL)).andRespond(withSuccess("""
                {"code":"01","desc":"Invalid account","data":null}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.createCheckout(command()))
                .isInstanceOf(PaymentProviderException.class)
                .hasMessageContaining("Invalid account");
        server.verify();
    }

    @Test
    void fallsBackToOfficialQuickLinkWhenApiCredentialsAreNotConfigured() {
        VietQrProperties properties = properties();
        properties.setClientId(null);
        properties.setApiKey(null);
        VietQrCheckoutAdapter quickLinkAdapter = new VietQrCheckoutAdapter(
                properties, RestClient.builder().build()
        );

        var result = quickLinkAdapter.createCheckout(command());

        assertThat(result.qrCodeContent()).isEqualTo(
                "https://img.vietqr.io/image/970415-123456789-compact2.png"
                        + "?amount=100000&addInfo=GS27BBAD9F70C8448294D1&accountName=GOAT%20SPORTS"
        );
    }

    private ProviderCheckoutCommand command() {
        return ProviderCheckoutCommand.builder()
                .attemptId(ATTEMPT_ID)
                .amount(100_000D)
                .currency("VND")
                .method(PaymentMethod.BANK_TRANSFER)
                .build();
    }

    private VietQrProperties properties() {
        VietQrProperties value = new VietQrProperties();
        value.setEnabled(true);
        value.setApiUrl(API_URL);
        value.setClientId("client-id");
        value.setApiKey("api-key");
        value.setAccountNo("123456789");
        value.setAccountName("GOAT SPORTS");
        value.setAcqId("970415");
        value.setTemplate("compact2");
        value.setTimeoutSeconds(30);
        return value;
    }
}
