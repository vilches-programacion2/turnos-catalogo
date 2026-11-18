package catalogo.replica.application.usecases;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import catalogo.replica.application.exception.CatalogoNoInicializadoException;
import catalogo.replica.application.exception.IncrementalCatalogoException;
import catalogo.replica.application.exception.IncrementalCatalogoException.Error;
import catalogo.replica.application.validation.CambioCatalogoValidator;
import catalogo.replica.domain.model.EstadoCatalogo;
import catalogo.replica.domain.ports.in.ActualizarCatalogoUseCase;
import catalogo.replica.domain.ports.out.CambiosCatalogoClient;
import catalogo.replica.domain.ports.out.CatalogoRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActualizarCatalogoUseCaseImpl implements ActualizarCatalogoUseCase {
    private final CatalogoRepository catalogo;
    private final CambiosCatalogoClient cambios;
    private final CambioCatalogoValidator validator;

    @Override
    public EstadoCatalogo actualizar(String eventId, Long nuevaVersion) {
        if (eventId == null || eventId.isBlank() || nuevaVersion == null || nuevaVersion < 0) {
            throw new IncrementalCatalogoException(Error.EVENTO_INVALIDO);
        }
        var estado = catalogo.obtenerEstado().orElseThrow(CatalogoNoInicializadoException::new);
        var procesado = catalogo.buscarEventoProcesado(eventId);
        if (procesado.isPresent()) {
            if (!procesado.get().equals(nuevaVersion)) throw new IncrementalCatalogoException(Error.EVENTO_CONFLICTIVO);
            return estado;
        }
        long anterior = estado.getVersionAplicada();
        if (anterior < nuevaVersion) {
            var metadata = cambios.obtenerMetadata();
            if (metadata == null || metadata.getCurrentVersion() == null || metadata.getOldestAvailableVersion() == null
                    || metadata.getOldestAvailableVersion() < 0 || metadata.getCurrentVersion() < metadata.getOldestAvailableVersion()) {
                throw new IncrementalCatalogoException(Error.RESPUESTA_INVALIDA);
            }
            if (anterior < metadata.getOldestAvailableVersion()) {
                throw new IncrementalCatalogoException(Error.HISTORIAL_NO_DISPONIBLE);
            }
            if (metadata.getCurrentVersion() < nuevaVersion) throw new IncrementalCatalogoException(Error.VERSION_NO_PUBLICADA);
            long objetivo = metadata.getCurrentVersion();
            while (estado.getVersionAplicada() < objetivo) {
                long version = estado.getVersionAplicada() + 1;
                var cambio = cambios.obtenerCambio(version);
                validator.validar(cambio, version);
                estado = catalogo.aplicarCambio(version - 1, cambio);
            }
        }
        estado = catalogo.registrarEventoProcesado(eventId, nuevaVersion);
        log.info("Incrementos de catálogo aplicados: versionAnterior={} versionAplicada={} resultado=correcto",
                anterior, estado.getVersionAplicada());
        return estado;
    }
}
