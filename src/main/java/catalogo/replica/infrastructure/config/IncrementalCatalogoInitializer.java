package catalogo.replica.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import catalogo.integracion.application.exception.IntegracionCatedraException;
import catalogo.integracion.domain.model.EstadoProvisionamiento;
import catalogo.integracion.domain.ports.in.ObtenerIntegracionCatedraUseCase;
import catalogo.replica.application.exception.IncrementalCatalogoException;
import catalogo.replica.domain.ports.out.CatalogoRepository;
import catalogo.replica.infrastructure.kafka.KafkaCatalogoConsumer;
import catalogo.replica.infrastructure.redis.RedisCambiosCatalogoAdapter;

@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.catedra", name = "enabled", havingValue = "true")
public class IncrementalCatalogoInitializer implements ApplicationRunner {
    private final SincronizacionProperties properties;
    private final ObtenerIntegracionCatedraUseCase integracion;
    private final CatalogoRepository catalogo;
    private final RedisCambiosCatalogoAdapter redis;
    private final KafkaCatalogoConsumer kafka;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) return;
        try {
            if (catalogo.obtenerEstado().isEmpty()) {
                log.warn("Consumo incremental pendiente: codigo=CATALOG_NOT_READY");
                return;
            }
            var config = integracion.obtener();
            if (config.getProvisioningStatus() != EstadoProvisionamiento.PROVISIONED) {
                log.info("Consumo incremental no habilitado: estado={}", config.getProvisioningStatus());
                return;
            }
            redis.preparar(config);
            kafka.arrancar(config);
            log.info("Consumo incremental iniciado: resultado=correcto");
        } catch (IntegracionCatedraException e) {
            cerrar();
            log.warn("Consumo incremental pendiente: codigo={}", e.getError());
        } catch (IncrementalCatalogoException e) {
            cerrar();
            log.warn("Consumo incremental pendiente: codigo={}", e.getError());
        } catch (DataAccessException e) {
            cerrar();
            log.warn("Consumo incremental pendiente: codigo=PERSISTENCIA_NO_DISPONIBLE");
        } catch (RuntimeException e) {
            cerrar();
            log.warn("Consumo incremental pendiente: codigo=CONEXION_NO_DISPONIBLE");
        }
    }

    private void cerrar() { kafka.cerrar(); redis.cerrar(); }
}
