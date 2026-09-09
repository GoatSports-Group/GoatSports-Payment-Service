package iuh.fit.paymentservice.infrastructure.external.config;

import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayOsPropertiesTests {

    @Test
    void acceptsPayoutChannelWhenPaymentChannelIsDisabled() {
        PayOsProperties properties = payoutProperties();
        properties.setEnabled(false);

        assertThatCode(properties::validateWhenEnabled).doesNotThrowAnyException();
        assertThatCode(properties::requirePayoutEnabled).doesNotThrowAnyException();
    }

    @Test
    void rejectsIncompletePayoutCredentials() {
        PayOsProperties properties = payoutProperties();
        properties.setPayoutApiKey(null);

        assertThatThrownBy(properties::validateWhenEnabled)
                .isInstanceOf(PaymentProviderException.class)
                .hasMessageContaining("PAYOS_PAYOUT_API_KEY");
    }

    private PayOsProperties payoutProperties() {
        PayOsProperties properties = new PayOsProperties();
        properties.setPayoutEnabled(true);
        properties.setApiUrl("https://api-merchant.payos.vn");
        properties.setPayoutClientId("payout-client-id");
        properties.setPayoutApiKey("payout-api-key");
        properties.setPayoutChecksumKey("payout-checksum-key");
        return properties;
    }
}
