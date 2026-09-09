package iuh.fit.paymentservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.paymentservice.domain.entity.Refund;
import iuh.fit.paymentservice.domain.enums.RefundStatus;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import iuh.fit.paymentservice.infrastructure.persistence.entity.RefundEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:refund_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
@ActiveProfiles("test")
class RefundIntegrationTests {
    private static final String GATEWAY_SECRET = "test-gateway-secret";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void refundDomainAndSchemaMatchClassDiagramExactly() {
        assertThat(declaredFields(Refund.class)).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("refundId", UUID.class),
                Map.entry("paymentId", UUID.class),
                Map.entry("requestedBy", UUID.class),
                Map.entry("reason", String.class),
                Map.entry("amount", Double.class),
                Map.entry("providerRefundId", String.class),
                Map.entry("idempotencyKey", String.class),
                Map.entry("autoDisburse", Boolean.class),
                Map.entry("status", RefundStatus.class),
                Map.entry("failureReason", String.class),
                Map.entry("refundedAt", LocalDateTime.class)
        ));
        Map<String, Class<?>> refundEntityFields = declaredFields(RefundEntity.class);
        assertThat(refundEntityFields).containsAllEntriesOf(declaredFields(Refund.class));
        assertThat(refundEntityFields)
                .containsEntry("payment", PaymentEntity.class)
                .hasSize(declaredFields(Refund.class).size() + 1);
        assertThat(Arrays.stream(RefundStatus.values()).map(Enum::name)).containsExactly(
                "CREATED", "AWAITING_CLAIM", "AWAITING_BANK_ACCOUNT", "PENDING", "PROCESSING",
                "SUCCEEDED", "FAILED", "MANUAL_REVIEW"
        );
        assertThat(jdbcTemplate.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'refunds'",
                String.class
        )).containsExactlyInAnyOrder(
                "refund_id", "payment_id", "requested_by", "reason", "amount",
                "provider_refund_id", "idempotency_key", "auto_disburse", "status", "failure_reason",
                "refunded_at", "created_at", "updated_at", "created_by", "updated_by"
        );
    }

    @Test
    void partialThenFullRefundRequestsRemainManualAndIdempotentReplayDoesNotDuplicate() throws Exception {
        UUID paymentId = createSucceededPayment("PAYOS", "BANK_TRANSFER", 100_000D);
        UUID requesterId = UUID.randomUUID();
        String firstKey = "refund-" + UUID.randomUUID();

        TestHttpResponse first = refund(paymentId, requesterId, firstKey, 40_000, "Hủy một phần sân");
        assertThat(first.status()).isEqualTo(201);
        JsonNode firstRefund = responseData(first.body());
        assertThat(firstRefund.path("status").asText()).isEqualTo("AWAITING_CLAIM");
        assertThat(firstRefund.path("amount").asDouble()).isEqualTo(40_000D);
        assertThat(paymentStatus(paymentId)).isEqualTo("SUCCEEDED");

        TestHttpResponse replay = refund(paymentId, requesterId, firstKey, 40_000, "Hủy một phần sân");
        assertThat(replay.status()).isEqualTo(200);
        assertThat(responseData(replay.body()).path("refundId").asText())
                .isEqualTo(firstRefund.path("refundId").asText());
        assertThat(refundCount(paymentId)).isEqualTo(1);

        TestHttpResponse second = refund(
                paymentId, requesterId, "refund-" + UUID.randomUUID(), 60_000, "Hủy phần còn lại"
        );
        assertThat(second.status()).isEqualTo(201);
        assertThat(responseData(second.body()).path("status").asText()).isEqualTo("AWAITING_CLAIM");
        assertThat(paymentStatus(paymentId)).isEqualTo("SUCCEEDED");
        assertThat(refundCount(paymentId)).isEqualTo(2);
    }

    @Test
    void refundCannotExceedUnreservedPaymentAmount() throws Exception {
        UUID paymentId = createSucceededPayment("MANUAL", "CASH", 100_000D);
        UUID requesterId = UUID.randomUUID();
        assertThat(refund(paymentId, requesterId, "refund-" + UUID.randomUUID(),
                40_000, "Refund lần một").status()).isEqualTo(201);

        TestHttpResponse exceeded = refund(paymentId, requesterId, "refund-" + UUID.randomUUID(),
                60_001, "Refund vượt số dư");
        assertThat(exceeded.status()).isEqualTo(409);
        assertThat(exceeded.body()).contains("\"code\":\"REFUND_AMOUNT_EXCEEDS_AVAILABLE\"");
        assertThat(refundCount(paymentId)).isEqualTo(1);
    }

    @Test
    void idempotencyKeyCannotBeReusedWithDifferentRefundPayload() throws Exception {
        UUID paymentId = createSucceededPayment("PAYOS", "BANK_TRANSFER", 100_000D);
        UUID requesterId = UUID.randomUUID();
        String key = "refund-" + UUID.randomUUID();
        assertThat(refund(paymentId, requesterId, key, 40_000, "Lý do A").status()).isEqualTo(201);

        TestHttpResponse conflict = refund(paymentId, requesterId, key, 30_000, "Lý do B");
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.body()).contains("\"code\":\"IDEMPOTENCY_KEY_REUSED\"");
        assertThat(refundCount(paymentId)).isEqualTo(1);
    }

    @Test
    void manualCashRefundRequiresManualReviewAndReservesAmount() throws Exception {
        UUID paymentId = createSucceededPayment("MANUAL", "CASH", 80_000D);
        UUID requesterId = UUID.randomUUID();
        TestHttpResponse response = refund(paymentId, requesterId, "refund-" + UUID.randomUUID(),
                30_000, "Hoàn tiền mặt");

        assertThat(response.status()).isEqualTo(201);
        assertThat(responseData(response.body()).path("status").asText()).isEqualTo("MANUAL_REVIEW");
        assertThat(paymentStatus(paymentId)).isEqualTo("SUCCEEDED");
        TestHttpResponse exceeded = refund(paymentId, requesterId, "refund-" + UUID.randomUUID(),
                50_001, "Vượt khoản đang chờ xử lý");
        assertThat(exceeded.status()).isEqualTo(409);
    }

    @Test
    void ownerCanConfirmManualRefundAndPaymentBecomesRefunded() throws Exception {
        UUID paymentId = createSucceededPayment("MANUAL", "CASH", 100_000D);
        UUID requesterId = UUID.randomUUID();
        TestHttpResponse created = refund(
                paymentId, requesterId, "refund-" + UUID.randomUUID(), 100_000, "Hủy sân"
        );
        UUID refundId = UUID.fromString(responseData(created.body()).path("refundId").asText());

        TestHttpResponse completed = request(
                "POST", "/internal/payments/refunds/" + refundId + "/manual-completion",
                Map.of(
                        "X-Gateway-Secret", GATEWAY_SECRET,
                        "X-User-Id", requesterId.toString()
                ),
                objectMapper.writeValueAsString(Map.of("providerRefundId", "FT-REFUND-100000"))
        );

        assertThat(completed.status()).isEqualTo(200);
        assertThat(responseData(completed.body()).path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(responseData(completed.body()).path("providerRefundId").asText())
                .isEqualTo("FT-REFUND-100000");
        assertThat(paymentStatus(paymentId)).isEqualTo("REFUNDED");
    }

    @Test
    void concurrentSameIdempotencyKeyCreatesOneManualRefund() throws Exception {
        UUID paymentId = createSucceededPayment("PAYOS", "BANK_TRANSFER", 100_000D);
        UUID requesterId = UUID.randomUUID();
        String key = "refund-race-" + UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<TestHttpResponse> caller = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Refund callers did not start in time");
                }
                return refund(paymentId, requesterId, key, 50_000, "Concurrent refund");
            };
            Future<TestHttpResponse> first = executor.submit(caller);
            Future<TestHttpResponse> second = executor.submit(caller);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            TestHttpResponse firstResponse = first.get(30, TimeUnit.SECONDS);
            TestHttpResponse secondResponse = second.get(30, TimeUnit.SECONDS);
            assertThat(List.of(firstResponse.status(), secondResponse.status()))
                    .containsExactlyInAnyOrder(201, 200);
            assertThat(responseData(firstResponse.body()).path("refundId").asText())
                    .isEqualTo(responseData(secondResponse.body()).path("refundId").asText());
            assertThat(refundCount(paymentId)).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private UUID createSucceededPayment(String provider, String method, double amount) {
        UUID paymentId = UUID.randomUUID();
        UUID payerId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                insert into payments (
                    payment_id, payer_id, purpose, reference_type, reference_id, amount,
                    platform_fee_amount, payee_amount, currency,
                    description, idempotency_key, status, paid_at, expires_at,
                    created_at, updated_at, created_by, updated_by
                ) values (?, ?, 'BOOKING_DEPOSIT', 'BOOKING', ?, ?, 0, ?, 'VND', ?, ?, 'SUCCEEDED', ?, ?, ?, ?, ?, ?)
                """, paymentId, payerId, referenceId, amount, amount, "Test refund", "pay-" + paymentId,
                now, now.plusHours(1), now, now, payerId.toString(), payerId.toString());
        jdbcTemplate.update("""
                insert into payment_attempts (
                    attempt_id, payment_id, provider, method, provider_transaction_id, checkout_url,
                    status, failure_code, failure_reason, initiated_at, completed_at,
                    created_at, updated_at, created_by, updated_by
                ) values (?, ?, ?, ?, ?, null, 'SUCCEEDED', null, null, ?, ?, ?, ?, ?, ?)
                """, attemptId, paymentId, provider, method,
                provider + "-" + attemptId.toString().replace("-", ""),
                now, now, now, now, payerId.toString(), payerId.toString());
        return paymentId;
    }

    private TestHttpResponse refund(
            UUID paymentId, UUID requesterId, String idempotencyKey, long amount, String reason
    ) throws Exception {
        return request("POST", "/internal/payments/" + paymentId + "/refunds",
                Map.of(
                        "X-Gateway-Secret", GATEWAY_SECRET,
                        "X-User-Id", requesterId.toString(),
                        "Idempotency-Key", idempotencyKey
                ), objectMapper.writeValueAsString(Map.of(
                        "amount", amount, "reason", reason, "autoDisburse", false
                )));
    }

    private String paymentStatus(UUID paymentId) {
        return jdbcTemplate.queryForObject("select status from payments where payment_id = ?",
                String.class, paymentId);
    }

    private int refundCount(UUID paymentId) {
        return jdbcTemplate.queryForObject("select count(*) from refunds where payment_id = ?",
                Integer.class, paymentId);
    }

    private JsonNode responseData(String body) throws Exception {
        return objectMapper.readTree(body).path("data");
    }

    private Map<String, Class<?>> declaredFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .collect(Collectors.toMap(java.lang.reflect.Field::getName,
                        java.lang.reflect.Field::getType));
    }

    private TestHttpResponse request(String method, String path, Map<String, String> headers, String body)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI
                .create("http://localhost:" + port + path).toURL().openConnection();
        connection.setRequestMethod(method);
        headers.forEach(connection::setRequestProperty);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }
        int status = connection.getResponseCode();
        var stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String responseBody = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        connection.disconnect();
        return new TestHttpResponse(status, responseBody);
    }

    private record TestHttpResponse(int status, String body) {
    }

}
