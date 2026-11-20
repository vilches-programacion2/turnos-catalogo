package catalogo.replica.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import catalogo.replica.domain.model.EstadoCatalogo;
import catalogo.replica.domain.ports.in.RecuperarCatalogoUseCase;

@Component
@RequiredArgsConstructor
public class RecuperarCatalogoUseCaseImpl implements RecuperarCatalogoUseCase {
    private final IncrementosCatalogo incrementos;

    @Override
    public EstadoCatalogo recuperar() {
        return incrementos.recuperar();
    }
}
