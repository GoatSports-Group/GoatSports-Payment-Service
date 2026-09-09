package iuh.fit.paymentservice.infrastructure.external.adapter;

import iuh.fit.paymentservice.application.dto.response.bankaccount.BankDirectoryEntry;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.application.port.client.BankDirectoryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class VietQrBankDirectoryAdapter implements BankDirectoryPort {
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final RestClient restClient;
    private volatile List<BankDirectoryEntry> cached = List.of();
    private volatile Instant cachedAt = Instant.EPOCH;

    public VietQrBankDirectoryAdapter(
            @Value("${bank.directory.url}") String bankDirectoryUrl
    ) {
        this.restClient = RestClient.builder().baseUrl(bankDirectoryUrl).build();
    }

    @Override
    public List<BankDirectoryEntry> getBanks() {
        List<BankDirectoryEntry> current = cached;
        if (!current.isEmpty() && cachedAt.plus(CACHE_TTL).isAfter(Instant.now())) {
            return current;
        }
        synchronized (this) {
            current = cached;
            if (!current.isEmpty() && cachedAt.plus(CACHE_TTL).isAfter(Instant.now())) {
                return current;
            }
            try {
                VietQrResponse response = restClient.get().retrieve()
                        .body(new ParameterizedTypeReference<>() {
                        });
                if (response == null || response.data() == null || response.data().isEmpty()) {
                    throw new PaymentProviderException(
                            "BANK_DIRECTORY_INVALID_RESPONSE",
                            "Danh sách ngân hàng VietQR không hợp lệ"
                    );
                }
                cached = response.data().stream()
                        .filter(bank -> Boolean.TRUE.equals(bank.transferSupported()))
                        .toList();
                cachedAt = Instant.now();
                return cached;
            } catch (PaymentProviderException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new PaymentProviderException(
                        "BANK_DIRECTORY_UNAVAILABLE",
                        "Không thể tải danh sách ngân hàng VietQR",
                        exception
                );
            }
        }
    }

    private record VietQrResponse(String code, String desc, List<BankDirectoryEntry> data) {
    }
}
