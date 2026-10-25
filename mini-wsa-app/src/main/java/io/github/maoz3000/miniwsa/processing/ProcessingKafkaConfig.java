package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.KafkaTopicsProperties;
import io.github.maoz3000.miniwsa.config.Role;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRole(Role.PROCESSING)
public class ProcessingKafkaConfig {

    /**
     * Dead letters carry the original record bytes unchanged, so they use a byte-array value serializer.
     * The template is private to the sink: exposing a second KafkaTemplate bean would make Boot's
     * default template back off.
     */
    @Bean
    public DeadLetterSink deadLetterSink(ProducerFactory<?, ?> producerFactory, KafkaTopicsProperties topics) {
        Map<String, Object> config = new HashMap<>(producerFactory.getConfigurationProperties());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new KafkaDeadLetterSink(new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config)), topics);
    }

    /**
     * Retries a failed batch forever with exponential backoff: failures reaching the container are transient
     * by construction (bad data is dead-lettered by the consumer), and Kafka holds the data meanwhile.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(ProcessingProperties properties) {
        ProcessingProperties.Retry retry = properties.retry();
        ExponentialBackOff backOff = new ExponentialBackOff(retry.initialInterval().toMillis(), retry.multiplier());
        backOff.setMaxInterval(retry.maxInterval().toMillis());
        return new DefaultErrorHandler(backOff);
    }
}
