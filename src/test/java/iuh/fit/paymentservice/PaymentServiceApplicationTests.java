package iuh.fit.paymentservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.paymentservice.domain.entity.BaseEntity;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PaymentAttemptEntity;
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
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void liquibaseBaselineIsApplied() {
        Integer changeCount = jdbcTemplate.queryForObject(
                "select count(*) from databasechangelog",
                Integer.class
        );

        assertThat(changeCount).isEqualTo(2);

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

        List<String> attemptColumns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns " +
                        "where table_schema = 'public' and table_name = 'payment_attempts'",
                String.class
        );
        assertThat(attemptColumns).containsExactlyInAnyOrder(
                "attempt_id",
                "payment_id",
                "provider",
                "method",
                "provider_transaction_id",
                "checkout_url",
                "status",
                "failure_code",
                "failure_reason",
                "initiated_at",
                "completed_at",
                "created_at",
                "updated_at",
                "created_by",
                "updated_by"
        );
    }

    @Test
    void paymentAttemptDomainMatchesClassDiagramExactly() {
        assertThat(declaredFields(PaymentAttempt.class)).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("attemptId", UUID.class),
                Map.entry("paymentId", UUID.class),
                Map.entry("provider", PaymentProvider.class),
                Map.entry("method", PaymentMethod.class),
                Map.entry("providerTransactionId", String.class),
                Map.entry("checkoutUrl", String.class),
                Map.entry("status", PaymentAttemptStatus.class),
                Map.entry("failureCode", String.class),
                Map.entry("failureReason", String.class),
                Map.entry("initiatedAt", LocalDateTime.class),
                Map.entry("completedAt", LocalDateTime.class)
        ));
        assertThat(declaredFields(PaymentAttemptEntity.class)).containsExactlyInAnyOrderEntriesOf(
                declaredFields(PaymentAttempt.class)
        );
        assertThat(Arrays.stream(PaymentMethod.values()).map(Enum::name)).containsExactly(
                "BANK_TRANSFER",
                "E_WALLET",
                "CASH"
        );
        assertThat(Arrays.stream(PaymentProvider.values()).map(Enum::name)).containsExactly(
                "VNPAY",
                "MANUAL"
        );
        assertThat(Arrays.stream(PaymentAttemptStatus.values()).map(Enum::name)).containsExactly(
                "CREATED",
                "REDIRECT_READY",
                "PENDING",
                "SUCCEEDED",
                "FAILED",
                "EXPIRED"
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

    @Test
    void createVnPayCheckoutIsSignedPersistedAndReplaySafe() throws Exception {
        UUID payerUserId = UUID.randomUUID();
        TestHttpResponse paymentResponse = request(
                "POST",
                "/api/v1/payments",
                gatewayHeaders(payerUserId, "pay-test-" + UUID.randomUUID()),
                paymentBody("BOOKING", UUID.randomUUID(), "BOOKING_DEPOSIT", "150000", "VND")
        );
        assertThat(paymentResponse.status()).isEqualTo(201);
        UUID paymentId = extractPaymentId(paymentResponse.body());

        Map<String, String> headers = gatewayHeaders(payerUserId, null);
        headers = new java.util.LinkedHashMap<>(headers);
        headers.put("X-Forwarded-For", "203.0.113.10");
        String attemptBody = """
                {
                  "method": "BANK_TRANSFER",
                  "locale": "vn"
                }
                """;

        TestHttpResponse created = request(
                "POST",
                "/api/v1/payments/" + paymentId + "/attempts",
                headers,
                attemptBody
        );
        assertThat(created.status()).isEqualTo(201);
        JsonNode createdAttempt = responseData(created.body());
        assertThat(createdAttempt.path("provider").asText()).isEqualTo("VNPAY");
        assertThat(createdAttempt.path("method").asText()).isEqualTo("BANK_TRANSFER");
        assertThat(createdAttempt.path("status").asText()).isEqualTo("REDIRECT_READY");
        assertThat(createdAttempt.path("providerTransactionId").isNull()).isTrue();

        String checkoutUrl = createdAttempt.path("checkoutUrl").asText();
        assertValidVnPayCheckoutUrl(checkoutUrl, createdAttempt.path("attemptId").asText());

        Map<String, Object> persisted = jdbcTemplate.queryForMap(
                "select provider, method, status, checkout_url, failure_code, failure_reason " +
                        "from payment_attempts where attempt_id = ?",
                UUID.fromString(createdAttempt.path("attemptId").asText())
        );
        assertThat(persisted).containsEntry("PROVIDER", "VNPAY");
        assertThat(persisted).containsEntry("METHOD", "BANK_TRANSFER");
        assertThat(persisted).containsEntry("STATUS", "REDIRECT_READY");
        assertThat(persisted.get("CHECKOUT_URL")).isEqualTo(checkoutUrl);
        assertThat(persisted.get("FAILURE_CODE")).isNull();
        assertThat(persisted.get("FAILURE_REASON")).isNull();

        TestHttpResponse replayed = request(
                "POST",
                "/api/v1/payments/" + paymentId + "/attempts",
                headers,
                attemptBody
        );
        assertThat(replayed.status()).isEqualTo(200);
        assertThat(responseData(replayed.body()).path("attemptId").asText())
                .isEqualTo(createdAttempt.path("attemptId").asText());

        String paymentStatus = jdbcTemplate.queryForObject(
                "select status from payments where payment_id = ?",
                String.class,
                paymentId
        );
        assertThat(paymentStatus).isEqualTo("PENDING");
    }

    @Test
    void checkoutEnforcesOwnershipAndRejectsCash() throws Exception {
        UUID payerUserId = UUID.randomUUID();
        TestHttpResponse paymentResponse = request(
                "POST",
                "/api/v1/payments",
                gatewayHeaders(payerUserId, "pay-test-" + UUID.randomUUID()),
                paymentBody("CLUB_FEE", UUID.randomUUID(), "CLUB_FEE", "50000", "VND")
        );
        UUID paymentId = extractPaymentId(paymentResponse.body());

        TestHttpResponse anotherUser = request(
                "POST",
                "/api/v1/payments/" + paymentId + "/attempts",
                gatewayHeaders(UUID.randomUUID(), null),
                "{\"method\":\"E_WALLET\",\"locale\":\"en\"}"
        );
        assertThat(anotherUser.status()).isEqualTo(403);
        assertThat(anotherUser.body()).contains("\"code\":\"PAYMENT_ACCESS_DENIED\"");

        TestHttpResponse cash = request(
                "POST",
                "/api/v1/payments/" + paymentId + "/attempts",
                gatewayHeaders(payerUserId, null),
                "{\"method\":\"CASH\",\"locale\":\"vn\"}"
        );
        assertThat(cash.status()).isEqualTo(400);
        assertThat(cash.body()).contains("\"code\":\"UNSUPPORTED_PAYMENT_METHOD\"");
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

    private JsonNode responseData(String body) throws Exception {
        return objectMapper.readTree(body).path("data");
    }

    private void assertValidVnPayCheckoutUrl(String checkoutUrl, String attemptId) throws Exception {
        URI uri = URI.create(checkoutUrl);
        assertThat(uri.getScheme()).isEqualTo("https");
        assertThat(uri.getHost()).isEqualTo("sandbox.vnpayment.vn");

        String rawQuery = uri.getRawQuery();
        int signatureIndex = rawQuery.lastIndexOf("&vnp_SecureHash=");
        assertThat(signatureIndex).isPositive();
        String canonicalQuery = rawQuery.substring(0, signatureIndex);
        String signature = rawQuery.substring(signatureIndex + "&vnp_SecureHash=".length());

        assertThat(canonicalQuery).contains(
                "vnp_Amount=15000000",
                "vnp_BankCode=VNBANK",
                "vnp_CurrCode=VND",
                "vnp_IpAddr=203.0.113.10",
                "vnp_Locale=vn",
                "vnp_TmnCode=TESTCODE",
                "vnp_TxnRef=" + attemptId.replace("-", ""),
                "vnp_Version=2.1.0"
        );
        assertThat(signature).isEqualTo(hmacSha512("test-vnpay-hash-secret", canonicalQuery));
    }

    private String hmacSha512(String secret, String data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
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
