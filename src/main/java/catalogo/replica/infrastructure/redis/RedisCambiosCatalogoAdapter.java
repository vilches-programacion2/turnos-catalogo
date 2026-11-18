package catalogo.replica.infrastructure.redis;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import catalogo.integracion.domain.model.EstadoProvisionamiento;
import catalogo.integracion.domain.model.IntegracionCatedra;
import catalogo.replica.application.exception.IncrementalCatalogoException;
import catalogo.replica.application.exception.IncrementalCatalogoException.Error;
import catalogo.replica.domain.model.CambioCatalogo;
import catalogo.replica.domain.model.MetadataCatalogo;
import catalogo.replica.domain.ports.out.CambiosCatalogoClient;
import catalogo.replica.infrastructure.client.dto.CategoriaProfesionalSnapshotResponse;
import catalogo.replica.infrastructure.client.dto.ProfesionalSnapshotResponse;
import catalogo.replica.infrastructure.client.dto.HorarioSemanalSnapshotResponse;
import catalogo.replica.infrastructure.client.mapper.SnapshotCatalogoMapper;
import catalogo.replica.infrastructure.config.SincronizacionProperties;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SincronizacionProperties.class)
public class RedisCambiosCatalogoAdapter implements CambiosCatalogoClient {
    private static final String PREFIX = "catedra:sync:";
    private final SincronizacionProperties properties;
    private final JsonMapper json;
    private final SnapshotCatalogoMapper mapper;
    private RedisClient client;
    private StatefulRedisConnection<String, String> connection;

    public synchronized void preparar(IntegracionCatedra integracion) {
        cerrar();
        if (integracion.getProvisioningStatus() != EstadoProvisionamiento.PROVISIONED) return;
        try {
            var uri = RedisURI.builder().withHost(integracion.getRedisHost()).withPort(integracion.getRedisPort())
                    .withAuthentication(integracion.getRedisUsername(), integracion.getRedisPassword())
                    .withTimeout(properties.commandTimeout()).build();
            client = RedisClient.create(uri);
            client.setOptions(ClientOptions.builder().autoReconnect(false)
                    .socketOptions(SocketOptions.builder().connectTimeout(properties.connectTimeout()).build()).build());
            connection = client.connect();
        } catch (RuntimeException e) {
            cerrar();
            throw new IncrementalCatalogoException(Error.REDIS_NO_DISPONIBLE);
        }
    }

    @Override
    public synchronized MetadataCatalogo obtenerMetadata() {
        try {
            var values = conexion().sync().hmget(PREFIX + "metadata", "currentVersion", "oldestAvailableVersion", "publishedAt");
            if (values.size() != 3 || values.stream().anyMatch(v -> !v.hasValue())) throw invalido();
            long current = Long.parseLong(values.get(0).getValue());
            long oldest = Long.parseLong(values.get(1).getValue());
            Instant.parse(values.get(2).getValue());
            if (oldest < 0 || current < oldest) throw invalido();
            return MetadataCatalogo.builder().currentVersion(current).oldestAvailableVersion(oldest).build();
        } catch (RedisException e) {
            throw new IncrementalCatalogoException(Error.REDIS_NO_DISPONIBLE);
        } catch (IncrementalCatalogoException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalido();
        }
    }

    @Override
    public synchronized CambioCatalogo obtenerCambio(long version) {
        try {
            String raw = conexion().sync().get(PREFIX + "changes:" + version);
            if (raw == null) throw new IncrementalCatalogoException(Error.HISTORIAL_NO_DISPONIBLE);
            var response = json.readValue(raw, CambiosIdsResponse.class);
            if (response == null || !Long.valueOf(version).equals(response.getVersion()) || response.getChanges() == null) throw invalido();
            var ids = response.getChanges();
            return CambioCatalogo.builder().version(version)
                    .categorias(entidades("professional-categories", ids.getProfessionalCategories(), CategoriaProfesionalSnapshotResponse.class, mapper::toDomain))
                    .profesionales(entidades("professionals", ids.getProfessionals(), ProfesionalSnapshotResponse.class, mapper::toDomain))
                    .horarios(entidades("weekly-schedules", ids.getWeeklySchedules(), HorarioSemanalSnapshotResponse.class, mapper::toDomain)).build();
        } catch (RedisException e) {
            throw new IncrementalCatalogoException(Error.REDIS_NO_DISPONIBLE);
        } catch (IncrementalCatalogoException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalido();
        }
    }

    private <T, R> List<R> entidades(String hash, List<Long> ids, Class<T> type, Function<T, R> mapping) {
        if (ids == null || ids.stream().anyMatch(id -> id == null || id <= 0) || new HashSet<>(ids).size() != ids.size()) throw invalido();
        if (ids.isEmpty()) return List.of();
        var values = conexion().sync().hmget(PREFIX + hash, ids.stream().map(String::valueOf).toArray(String[]::new));
        if (values.size() != ids.size() || values.stream().anyMatch(v -> !v.hasValue())) throw invalido();
        var result = new java.util.ArrayList<R>();
        for (int i = 0; i < values.size(); i++) {
            // El field de Redis debe coincidir con el ID contenido en el JSON.
            var node = json.readTree(values.get(i).getValue());
            if (!node.isObject() || !node.path("id").isIntegralNumber() || node.path("id").asLong() != ids.get(i)) throw invalido();
            result.add(mapping.apply(json.treeToValue(node, type)));
        }
        return List.copyOf(result);
    }

    private StatefulRedisConnection<String, String> conexion() {
        if (connection == null || !connection.isOpen()) throw new IncrementalCatalogoException(Error.REDIS_NO_DISPONIBLE);
        return connection;
    }

    private IncrementalCatalogoException invalido() { return new IncrementalCatalogoException(Error.RESPUESTA_INVALIDA); }

    @PreDestroy
    public synchronized void cerrar() {
        if (connection != null) {
            try { connection.close(); } catch (RedisException ignored) { }
            connection = null;
        }
        if (client != null) {
            try { client.shutdown(Duration.ZERO, Duration.ofSeconds(1)); } catch (RedisException ignored) { }
            client = null;
        }
    }
}
