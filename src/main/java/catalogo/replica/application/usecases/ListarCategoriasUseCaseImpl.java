package catalogo.replica.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import catalogo.replica.application.exception.CatalogoNoInicializadoException;
import catalogo.replica.domain.model.LecturaCategorias;
import catalogo.replica.domain.ports.in.ListarCategoriasUseCase;
import catalogo.replica.domain.ports.out.CatalogoRepository;

@Component
@RequiredArgsConstructor
public class ListarCategoriasUseCaseImpl implements ListarCategoriasUseCase {
    private final CatalogoRepository catalogo;

    @Override
    public LecturaCategorias listar() {
        var lectura = catalogo.listarCategorias();
        if (lectura.getEstado() == null) throw new CatalogoNoInicializadoException();
        return lectura;
    }
}
