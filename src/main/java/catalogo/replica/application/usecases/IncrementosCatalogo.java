package catalogo.replica.application.usecases;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import catalogo.replica.application.exception.IncrementalCatalogoException;
import catalogo.replica.application.exception.IncrementalCatalogoException.Error;
import catalogo.replica.application.validation.CambioCatalogoValidator;
import catalogo.replica.application.validation.SnapshotCatalogoValidator;
import catalogo.replica.domain.model.EstadoCatalogo;
import catalogo.replica.domain.model.MetadataCatalogo;
import catalogo.replica.domain.ports.out.CambiosCatalogoClient;
import catalogo.replica.domain.ports.out.CatalogoRepository;
import catalogo.replica.domain.ports.out.SnapshotCatalogoClient;

@Slf4j
@Component
@RequiredArgsConstructor
class IncrementosCatalogo {
    private final CatalogoRepository catalogo;
    private final CambiosCatalogoClient cambios;
    private final CambioCatalogoValidator validator;
    private final SnapshotCatalogoClient snapshots;
    private final SnapshotCatalogoValidator snapshotValidator;

    // Kafka y la revisión periódica comparten la misma secuencia de lecturas y escrituras.
    synchronized EstadoCatalogo aplicar(EstadoCatalogo estado, long notificada) {
        var vigente = catalogo.obtenerEstado().orElse(estado);
        if (vigente.getVersionAplicada() >= notificada) return vigente;
        var metadata = metadata();
        if (metadata.getCurrentVersion() < notificada) {
            throw new IncrementalCatalogoException(Error.VERSION_NO_PUBLICADA);
        }
        return aplicarDesde(vigente, metadata);
    }

    synchronized EstadoCatalogo recuperar() {
        var estado = catalogo.obtenerEstado();
        if (estado.isEmpty()) {
            return reemplazar(0);
        }
        var metadata = metadata();
        try {
            return aplicarDesde(estado.get(), metadata);
        } catch (IncrementalCatalogoException e) {
            if (e.getError() != Error.HISTORIAL_NO_DISPONIBLE && e.getError() != Error.RESPUESTA_INVALIDA) {
                throw e;
            }
            log.warn("Reconstrucción de catálogo requerida: codigo={}", e.getError());
        } catch (DataIntegrityViolationException e) {
            log.warn("Reconstrucción de catálogo requerida: codigo=REFERENCIAS_INCONSISTENTES");
        }
        return reemplazar(metadata.getCurrentVersion());
    }

    private EstadoCatalogo reemplazar(long versionMinima) {
        var snapshot = snapshots.descargar();
        snapshotValidator.validar(snapshot);
        if (snapshot.getSnapshotVersion() < versionMinima) {
            throw new IncrementalCatalogoException(Error.VERSION_NO_PUBLICADA);
        }
        var estado = catalogo.reemplazarSnapshot(snapshot);
        log.info("Catálogo recuperado por snapshot: version={}", estado.getVersionAplicada());
        return estado;
    }

    private MetadataCatalogo metadata() {
        var metadata = cambios.obtenerMetadata();
        if (metadata == null || metadata.getCurrentVersion() == null || metadata.getOldestAvailableVersion() == null
                || metadata.getOldestAvailableVersion() < 0
                || metadata.getCurrentVersion() < metadata.getOldestAvailableVersion()) {
            throw new IncrementalCatalogoException(Error.RESPUESTA_INVALIDA);
        }
        return metadata;
    }

    private EstadoCatalogo aplicarDesde(EstadoCatalogo estado, MetadataCatalogo metadata) {
        long anterior = estado.getVersionAplicada();
        if (anterior < metadata.getOldestAvailableVersion()) {
            throw new IncrementalCatalogoException(Error.HISTORIAL_NO_DISPONIBLE);
        }
        while (estado.getVersionAplicada() < metadata.getCurrentVersion()) {
            long version = estado.getVersionAplicada() + 1;
            var cambio = cambios.obtenerCambio(version);
            validator.validar(cambio, version);
            estado = catalogo.aplicarCambio(version - 1, cambio);
        }
        if (estado.getVersionAplicada() > anterior) {
            log.info("Incrementos de catálogo aplicados: versionAnterior={} versionAplicada={}", anterior, estado.getVersionAplicada());
        }
        return estado;
    }
}
