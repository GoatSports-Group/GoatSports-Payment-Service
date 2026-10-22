package iuh.fit.paymentservice.infrastructure.config;

import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Xử lý sự kiện lỗi (vd. DB tạm lỗi khi xóa tài khoản ngân hàng của tài khoản đã bị xóa): thử lại 6 lần giãn cách
 * 1s → 2s → 4s … tối đa 30s. Hết lượt thì chuyển nguyên văn message sang topic {{topic}}-dlt để giữ lại và
 * gửi lại sau, thay vì bỏ qua như mặc định.
 */
@Configuration
public class KafkaConsumerErrorConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler(ProducerFactory<?, ?> producerFactory) {
        // Listener đọc message dạng chuỗi: DLT cũng ghi chuỗi (StringSerializer) để giữ đúng nội dung gốc.
        KafkaTemplate<String, String> deadLetters = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                producerFactory.getConfigurationProperties(), new StringSerializer(), new StringSerializer()));
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(6);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(30_000L);
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(deadLetters), backOff);
    }
}
