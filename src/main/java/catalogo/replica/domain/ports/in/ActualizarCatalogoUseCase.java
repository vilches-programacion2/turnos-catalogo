package catalogo.replica.domain.ports.in;

import catalogo.replica.domain.model.EstadoCatalogo;

public interface ActualizarCatalogoUseCase {
    EstadoCatalogo actualizar(String eventId, Long nuevaVersion);
}
