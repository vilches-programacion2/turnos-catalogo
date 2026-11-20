package catalogo.replica.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.catedra.sync")
public record SincronizacionProperties(boolean enabled, Duration connectTimeout, Duration commandTimeout,
        String kafkaSecurityProtocol, Duration kafkaRequestTimeout, Duration kafkaOperationTimeout) {
    public SincronizacionProperties {
        if (!valido(connectTimeout) || !valido(commandTimeout) || !valido(kafkaRequestTimeout)
                || !valido(kafkaOperationTimeout) || kafkaOperationTimeout.compareTo(kafkaRequestTimeout) < 0
                || kafkaSecurityProtocol == null || !java.util.Set.of("PLAINTEXT", "SSL", "SASL_PLAINTEXT", "SASL_SSL").contains(kafkaSecurityProtocol)) {
            throw new IllegalArgumentException("Configuración de sincronización inválida.");
        }
    }

    private static boolean valido(Duration value) {
        return value != null && value.toMillis() > 0 && value.toMillis() <= Integer.MAX_VALUE;
    }
}
