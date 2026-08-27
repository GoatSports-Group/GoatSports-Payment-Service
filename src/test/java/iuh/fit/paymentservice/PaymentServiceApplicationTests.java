package iuh.fit.paymentservice;

import iuh.fit.paymentservice.domain.entity.BaseEntity;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PaymentServiceApplicationTests {
    private static final String GATEWAY_SECRET = "test-gateway-secret";
    private static final Pattern PAYMENT_ID_PATTERN =
            Pattern.compile("\\\"paymentId\\\":\\\"([0-9a-f-]+)\\\"");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void liquibaseBaselineIsApplied() {
        Integer changeCount = jdbcTemplate.queryForObject(
                "select count(*) from databasechangelog",
                Integer.class
        );

        assertThat(changeCount).isEqualTo(1);

        List<String> paymentColumns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns " +
                        "where table_schema = 'public' and table_name = 'payments'",
                String.class
        );
        assertThat(paymentColumns).containsExactlyInAnyOrder(
                "payment_id",
                "payer_id",
                "purpose",
                "reference_type",
                "reference_id",
                "amount",
                "currency",
                "description",
                "idempotency_key",
                "status",
                "paid_at",
                "expires_at",
                "created_at",
                "updated_at",
                "created_by",
                "updated_by"
        );
    }

    @Test
    void paymentDomainMatchesClassDiagramExactly() {
        assertThat(declaredFields(Payment.class)).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("paymentId", UUID.class),
                Map.entry("payerId", UUID.class),
                Map.entry("purpose", PaymentPurpose.class),
                Map.entry("referenceType", PaymentReferenceType.class),
                Map.entry("referenceId", UUID.class),
                Map.entry("amount", Double.class),
                Map.entry("currency", String.class),
                Map.entry("description", String.class),
                Map.entry("idempotencyKey", String.class),
                Map.entry("status", PaymentStatus.class),
                Map.entry("paidAt", LocalDateTime.class),
                Map.entry("expiresAt", LocalDateTime.class)
        ));
        assertThat(declaredFields(BaseEntity.class)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "createdAt", LocalDateTime.class,
                "updatedAt", LocalDateTime.class,
                "createdBy", String.class,
                "updatedBy", String.class
        ));
        assertThat(declaredFields(PaymentEntity.class)).containsExactlyInAnyOrderEntriesOf(
                declaredFields(Payment.class)
        );
        assertThat(declaredFields(
                iuh.fit.paymentservice.infrastructure.persistence.entity.BaseEntity.class
        )).containsExactlyInAnyOrderEntriesOf(declaredFields(BaseEntity.class));
        assertThat(Arrays.stream(PaymentPurpose.values()).map(Enum::name)).containsExactly(
                "BOOKING_DEPOSIT",
                "BOOKING_REMAINING",
                "CLUB_FEE",
                "TOURNAMENT_FEE"
        );
    }

    @Test
    void healthEndpointIsPublicAndUp() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI
                .create("http://localhost:" + port + "/actuator/health")
                .toURL()
                .openConnection();
        connection.setRequestMethod("GET");

        assertThat(connection.getResponseCode()).isEqualTo(200);
    }

    @Test
    void directBusinessApiAccessIsRejected() throws Exception {
        TestHttpResponse response = request("GET", "/api/v1/payments", Map.of(), null);

        assertThat(response.status()).isEqualTo(403);
    }

    @Test
    void createPaymentIsIdempotentAndOwnedByAuthenticatedUser() throws Exception {
        UUID payerUserId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();
        String idempotencyKey = "pay-test-" + UUID.randomUUID();
        String body = paymentBody("BOOKING", referenceId, "BOOKING_DEPOSIT", "150000.00", "VND");
        Map<String, String> headers = gatewayHeaders(payerUserId, idempotencyKey);

        TestHttpResponse created = request("POST", "/api/v1/payments", headers, body);
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body()).contains(
                "\"payerId\":\"" + payerUserId + "\"",
                "\"status\":\"CREATED\"",
                "\"amount\":150000.0",
                "\"currency\":\"VND\""
        );

        UUID paymentId = extractPaymentId(created.body());
        TestHttpResponse replayed = request("POST", "/api/v1/payments", headers, body);
        assertThat(replayed.status()).isEqualTo(200);
        assertThat(extractPaymentId(replayed.body())).isEqualTo(paymentId);

        TestHttpResponse owned = request(
                "GET",
                "/api/v1/payments/" + paymentId,
                gatewayHeaders(payerUserId, null),
                null
        );
        assertThat(owned.status()).isEqualTo(200);

        TestHttpResponse anotherUser = request(
                "GET",
                "/api/v1/payments/" + paymentId,
                gatewayHeaders(UUID.randomUUID(), null),
                null
        );
        assertThat(anotherUser.status()).isEqualTo(403);
        assertThat(anotherUser.body()).contains("\"code\":\"PAYMENT_ACCESS_DENIED\"");
    }

    @Test
    void idempotencyKeyCannotBeReusedWithAnotherPayload() throws Exception {
        UUID payerUserId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();
        String idempotencyKey = "pay-test-" + UUID.randomUUID();
        Map<String, String> headers = gatewayHeaders(payerUserId, idempotencyKey);

        TestHttpResponse created = request(
                "POST",
                "/api/v1/payments",
                headers,
                paymentBody("BOOKING", referenceId, "BOOKING_DEPOSIT", "100000", "VND")
        );
        assertThat(created.status()).isEqualTo(201);

        TestHttpResponse conflict = request(
                "POST",
                "/api/v1/payments",
                headers,
                paymentBody("BOOKING", referenceId, "BOOKING_DEPOSIT", "200000", "VND")
        );
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.body()).contains("\"code\":\"IDEMPOTENCY_KEY_REUSED\"");
    }

    @Test
    void referenceAndPurposeCannotCreateDuplicatePayment() throws Exception {
        UUID payerUserId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();
        String body = paymentBody("CLUB_FEE", referenceId, "CLUB_FEE", "50000", "vnd");

        TestHttpResponse created = request(
                "POST",
                "/api/v1/payments",
                gatewayHeaders(payerUserId, "pay-test-" + UUID.randomUUID()),
                body
        );
        assertThat(created.status()).isEqualTo(201);

        TestHttpResponse conflict = request(
                "POST",
                "/api/v1/payments",
                gatewayHeaders(payerUserId, "pay-test-" + UUID.randomUUID()),
                body
        );
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.body()).contains("\"code\":\"PAYMENT_ALREADY_EXISTS\"");
    }

    @Test
    void purposeMustMatchReferenceType() throws Exception {
        TestHttpResponse response = request(
                "POST",
                "/api/v1/payments",
                gatewayHeaders(UUID.randomUUID(), "pay-test-" + UUID.randomUUID()),
                paymentBody(
                        "TOURNAMENT_REGISTRATION",
                        UUID.randomUUID(),
                        "CLUB_FEE",
                        "75000",
                        "VND"
                )
        );

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"INVALID_PAYMENT_PURPOSE\"");
    }

    private Map<String, String> gatewayHeaders(UUID userId, String idempotencyKey) {
        var headers = new java.util.LinkedHashMap<String, String>();
        headers.put("X-Gateway-Secret", GATEWAY_SECRET);
        headers.put("X-User-Id", userId.toString());
        if (idempotencyKey != null) {
            headers.put("Idempotency-Key", idempotencyKey);
        }
        return headers;
    }

    private String paymentBody(
            String referenceType,
            UUID referenceId,
            String purpose,
            String amount,
            String currency
    ) {
        return """
                {
                  "referenceType": "%s",
                  "referenceId": "%s",
                  "purpose": "%s",
                  "amount": %s,
                  "currency": "%s"
                }
                """.formatted(referenceType, referenceId, purpose, amount, currency);
    }

    private UUID extractPaymentId(String body) {
        var matcher = PAYMENT_ID_PATTERN.matcher(body);
        assertThat(matcher.find()).as("payment id is present in response").isTrue();
        return UUID.fromString(matcher.group(1));
    }

    private Map<String, Class<?>> declaredFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .collect(Collectors.toMap(
                        java.lang.reflect.Field::getName,
                        java.lang.reflect.Field::getType
                ));
    }

    private TestHttpResponse request(
            String method,
            String path,
            Map<String, String> headers,
            String body
    ) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI
                .create("http://localhost:" + port + path)
                .toURL()
                .openConnection();
        connection.setRequestMethod(method);
        headers.forEach(connection::setRequestProperty);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = connection.getResponseCode();
        var stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String responseBody = stream == null
                ? ""
                : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        connection.disconnect();
        return new TestHttpResponse(status, responseBody);
    }

    private record TestHttpResponse(int status, String body) {
    }
}
