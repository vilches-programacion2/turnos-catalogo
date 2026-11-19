package catalogo.replica.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import catalogo.integracion.application.exception.IntegracionCatedraException;
import catalogo.integracion.domain.model.EstadoProvisionamiento;
import catalogo.integracion.domain.ports.in.ObtenerIntegracionCatedraUseCase;
import catalogo.replica.application.exception.IncrementalCatalogoException;
import catalogo.replica.application.exception.SnapshotCatalogoException;
import catalogo.replica.application.service.MonitorSincronizacionCatalogo;
import catalogo.replica.application.service.MonitorSincronizacionCatalogo.Fuente;
import catalogo.replica.domain.model.DiagnosticoCatalogo.Codigo;
import catalogo.replica.domain.ports.in.RecuperarCatalogoUseCase;
import catalogo.replica.infrastructure.kafka.KafkaCatalogoConsumer;
import catalogo.replica.infrastructure.redis.RedisCambiosCatalogoAdapter;

@Slf4j
@Component
@EnableScheduling
@Order(10)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.catedra", name = "enabled", havingValue = "true")
public class IncrementalCatalogoInitializer implements ApplicationRunner {
    private final SincronizacionProperties properties;
    private final ObtenerIntegracionCatedraUseCase integracion;
    private final RecuperarCatalogoUseCase recuperacion;
    private final RedisCambiosCatalogoAdapter redis;
    private final KafkaCatalogoConsumer kafka;
    private final MonitorSincronizacionCatalogo monitor;
    private volatile boolean inicioCompleto;

    @Override
    public void run(ApplicationArguments args) {
        revisar();
        inicioCompleto = true;
    }

    @Scheduled(fixedDelayString = "${app.catedra.sync.recovery-interval}",
            initialDelayString = "${app.catedra.sync.recovery-interval}")
    public void revisarPeriodicamente() {
        if (inicioCompleto) revisar();
    }

    public void revisar() {
        if (!properties.enabled()) return;
        var fuente = Fuente.REVISION;
        try {
            if (kafka.activo()) {
                recuperacion.recuperar();
                monitor.revisionCorrecta();
                return;
            }

            var config = integracion.obtener();
            if (config.getProvisioningStatus() != EstadoProvisionamiento.PROVISIONED) {
                monitor.registrarError(Fuente.REVISION, Codigo.INTEGRACION_NO_PROVISIONADA);
                log.info("Consumo incremental no habilitado: estado={}", config.getProvisioningStatus());
                return;
            }
            redis.preparar(config);
            recuperacion.recuperar();
            monitor.revisionCorrecta();
            fuente = Fuente.KAFKA_CONEXION;
            kafka.arrancar(config);
            log.info("Consumo incremental iniciado: resultado=correcto");
        } catch (IntegracionCatedraException e) {
            monitor.registrarError(fuente, Codigo.INTEGRACION_NO_DISPONIBLE);
            cerrar();
            log.warn("Consumo incremental pendiente: codigo={}", e.getError());
        } catch (IncrementalCatalogoException e) {
            monitor.registrarError(fuente, Codigo.valueOf(e.getError().name()));
            cerrar();
            log.warn("Consumo incremental pendiente: codigo={}", e.getError());
        } catch (SnapshotCatalogoException e) {
            monitor.registrarError(fuente, Codigo.SNAPSHOT_NO_DISPONIBLE);
            cerrar();
            log.warn("Recuperación de catálogo pendiente: codigo={}", e.getError());
        } catch (DataAccessException e) {
            monitor.registrarError(fuente, Codigo.PERSISTENCIA_NO_DISPONIBLE);
            cerrar();
            log.warn("Consumo incremental pendiente: codigo=PERSISTENCIA_NO_DISPONIBLE");
        } catch (RuntimeException e) {
            monitor.registrarError(fuente, fuente == Fuente.KAFKA_CONEXION
                    ? Codigo.KAFKA_NO_DISPONIBLE : Codigo.CONEXION_NO_DISPONIBLE);
            cerrar();
            log.warn("Consumo incremental pendiente: codigo=CONEXION_NO_DISPONIBLE");
        }
    }

    private void cerrar() { kafka.cerrar(); redis.cerrar(); }
}
