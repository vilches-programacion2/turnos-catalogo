package catalogo.replica.application.usecases;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import catalogo.replica.application.exception.CatalogoNoInicializadoException;
import catalogo.replica.application.exception.IncrementalCatalogoException;
import catalogo.replica.application.exception.IncrementalCatalogoException.Error;
import catalogo.replica.domain.model.EstadoCatalogo;
import catalogo.replica.domain.ports.in.ActualizarCatalogoUseCase;
import catalogo.replica.domain.ports.out.CatalogoRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActualizarCatalogoUseCaseImpl implements ActualizarCatalogoUseCase {
    private final CatalogoRepository catalogo;
    private final IncrementosCatalogo incrementos;

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
            estado = incrementos.aplicar(estado, nuevaVersion);
        }
        estado = catalogo.registrarEventoProcesado(eventId, nuevaVersion);
        log.debug("Evento de catálogo persistido: versionNotificada={} versionAplicada={}",
                nuevaVersion, estado.getVersionAplicada());
        return estado;
    }
}
