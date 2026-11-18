package catalogo.replica.infrastructure.kafka;

import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.stereotype.Component;
import catalogo.integracion.domain.model.EstadoProvisionamiento;
import catalogo.integracion.domain.model.IntegracionCatedra;
import catalogo.replica.infrastructure.config.SincronizacionProperties;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SincronizacionProperties.class)
public class KafkaCatalogoConsumer {
    private final SincronizacionProperties properties;
    private final CatalogUpdatedListener listener;
    private KafkaMessageListenerContainer<String, String> container;

    public synchronized void arrancar(IntegracionCatedra integracion) {
        cerrar();
        if (integracion.getProvisioningStatus() != EstadoProvisionamiento.PROVISIONED) return;
        var config = new HashMap<String, Object>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, integracion.getKafkaBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, integracion.getKafkaConsumerGroupId());
        config.put(ConsumerConfig.CLIENT_ID_CONFIG, "turnos-catalogo-incrementales");
        config.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, properties.kafkaSecurityProtocol());
        config.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, Math.toIntExact(properties.kafkaRequestTimeout().toMillis()));
        config.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, Math.toIntExact(properties.kafkaOperationTimeout().toMillis()));
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        config.put(ConsumerConfig.GROUP_PROTOCOL_CONFIG, "classic");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        container = crearContenedor(config, integracion.getKafkaCatalogTopic());
        container.start();
    }

    protected KafkaMessageListenerContainer<String, String> crearContenedor(Map<String, Object> config, String topic) {
        var props = new ContainerProperties(topic);
        props.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        props.setSyncCommits(true);
        props.setSyncCommitTimeout(properties.kafkaOperationTimeout());
        props.setMissingTopicsFatal(true);
        props.setShutdownTimeout(properties.kafkaOperationTimeout().toMillis());
        props.setMessageListener(listener);
        var result = new KafkaMessageListenerContainer<>(new DefaultKafkaConsumerFactory<String, String>(config), props);
        var errores = new CommonContainerStoppingErrorHandler() {
            @Override public boolean isAckAfterHandle() { return false; }
        };
        errores.setLogLevel(KafkaException.Level.DEBUG);
        result.setCommonErrorHandler(errores);
        return result;
    }

    public synchronized boolean activo() { return container != null && container.isRunning(); }

    @PreDestroy
    public synchronized void cerrar() {
        if (container != null) {
            container.stop();
            container = null;
        }
    }
}
