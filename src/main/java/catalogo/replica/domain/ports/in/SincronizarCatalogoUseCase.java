package catalogo.replica.domain.ports.in;

import catalogo.replica.domain.model.EstadoCatalogo;

public interface SincronizarCatalogoUseCase {
    EstadoCatalogo sincronizar();
}
