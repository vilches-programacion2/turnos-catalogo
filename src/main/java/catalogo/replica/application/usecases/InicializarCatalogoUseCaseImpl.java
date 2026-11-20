package catalogo.replica.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import catalogo.replica.domain.model.EstadoCatalogo;
import catalogo.replica.domain.ports.in.InicializarCatalogoUseCase;
import catalogo.replica.domain.ports.in.SincronizarCatalogoUseCase;
import catalogo.replica.domain.ports.out.CatalogoRepository;

@Component
@RequiredArgsConstructor
public class InicializarCatalogoUseCaseImpl implements InicializarCatalogoUseCase {
    private final CatalogoRepository catalogo;
    private final SincronizarCatalogoUseCase sincronizar;

    @Override
    public EstadoCatalogo inicializar() {
        return catalogo.obtenerEstado().orElseGet(sincronizar::sincronizar);
    }
}
