package iuh.fit.paymentservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:booking_cash_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
@ActiveProfiles("test")
@Import(PaymentServiceApplicationTests.StubPaymentProviderConfiguration.class)
class BookingCashCollectionIntegrationTests {
    private static final String GATEWAY_SECRET = "test-gateway-secret";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsAndCollectsOnceAcrossRetriesAndRejectsUntrustedCallers() throws Exception {
        UUID payerUserId = UUID.randomUUID();
        UUID collectorUserId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String body = """
                {
                  "payerId": "%s",
                  "bookingId": "%s",
                  "amount": 140000,
                  "currency": "VND"
                }
                """.formatted(payerUserId, bookingId);

        TestHttpResponse first = request(gatewayHeaders(collectorUserId), body);
        assertThat(first.status()).isEqualTo(201);
        JsonNode payment = responseData(first.body());
        assertThat(payment.path("referenceId").asText()).isEqualTo(bookingId.toString());
        assertThat(payment.path("purpose").asText()).isEqualTo("BOOKING_REMAINING");
        assertThat(payment.path("status").asText()).isEqualTo("SUCCEEDED");

        TestHttpResponse retried = request(gatewayHeaders(collectorUserId), body);
        assertThat(retried.status()).isEqualTo(201);
        assertThat(responseData(retried.body()).path("paymentId").asText())
                .isEqualTo(payment.path("paymentId").asText());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from payments where reference_id = ? and purpose = 'BOOKING_REMAINING'",
                Integer.class, bookingId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from payment_attempts where payment_id = ? and method = 'CASH'",
                Integer.class, UUID.fromString(payment.path("paymentId").asText())
        )).isEqualTo(1);

        TestHttpResponse untrusted = request(
                Map.of("X-User-Id", collectorUserId.toString()), body
        );
        assertThat(untrusted.status()).isEqualTo(403);
    }

    private Map<String, String> gatewayHeaders(UUID userId) {
        return Map.of(
                "X-Gateway-Secret", GATEWAY_SECRET,
                "X-User-Id", userId.toString()
        );
    }

    private JsonNode responseData(String body) throws Exception {
        return objectMapper.readTree(body).path("data");
    }

    private TestHttpResponse request(Map<String, String> headers, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI
                .create("http://localhost:" + port + "/internal/payments/booking-cash-collections")
                .toURL()
                .openConnection();
        connection.setRequestMethod("POST");
        headers.forEach(connection::setRequestProperty);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String responseBody = stream == null
                ? ""
                : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        connection.disconnect();
        return new TestHttpResponse(status, responseBody);
    }

    private record TestHttpResponse(int status, String body) {
    }
}
