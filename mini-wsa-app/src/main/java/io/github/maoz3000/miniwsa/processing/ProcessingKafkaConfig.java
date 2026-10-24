package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRole(Role.PROCESSING)
public class ProcessingKafkaConfig {

    /**
     * Dead letters carry the original record bytes unchanged, so they use a byte-array value serializer.
     */
    @Bean
    public KafkaTemplate<String, byte[]> deadLetterTemplate(ProducerFactory<?, ?> producerFactory) {
        Map<String, Object> config = new HashMap<>(producerFactory.getConfigurationProperties());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }
}
