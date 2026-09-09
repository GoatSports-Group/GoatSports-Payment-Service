package iuh.fit.paymentservice.infrastructure.security;

import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesBankAccountProtectorTests {
    private static final String SECRET = "goatsports-test-bank-account-secret-key";

    @Test
    void encryptsWithRandomIvAndDecryptsOriginalAccountNumber() {
        AesBankAccountProtector protector = new AesBankAccountProtector(SECRET);

        String first = protector.encrypt("0123456789");
        String second = protector.encrypt("0123456789");

        assertThat(first).doesNotContain("0123456789").isNotEqualTo(second);
        assertThat(protector.decrypt(first)).isEqualTo("0123456789");
        assertThat(protector.decrypt(second)).isEqualTo("0123456789");
    }

    @Test
    void hashesDestinationDeterministicallyWithoutExposingAccountNumber() {
        AesBankAccountProtector protector = new AesBankAccountProtector(SECRET);

        String hash = protector.hash("970436", "0123456789");

        assertThat(hash)
                .isEqualTo(protector.hash("970436", "0123456789"))
                .isNotEqualTo(protector.hash("970436", "9876543210"))
                .doesNotContain("0123456789");
    }

    @Test
    void rejectsMissingProductionSecret() {
        AesBankAccountProtector protector = new AesBankAccountProtector("too-short");

        assertThatThrownBy(() -> protector.encrypt("0123456789"))
                .isInstanceOf(PaymentProviderException.class)
                .hasMessageContaining("32");
    }
}
