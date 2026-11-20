package catalogo.replica.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import catalogo.replica.application.exception.CatalogoNoInicializadoException;
import catalogo.replica.application.exception.CategoriaInvalidaException;
import catalogo.replica.application.exception.FiltroInvalidoException;
import catalogo.replica.domain.model.FiltroProfesionales;
import catalogo.replica.domain.model.LecturaBusquedaProfesionales;
import catalogo.replica.domain.ports.in.BuscarProfesionalesUseCase;
import catalogo.replica.domain.ports.out.CatalogoRepository;

@Component
@RequiredArgsConstructor
public class BuscarProfesionalesUseCaseImpl implements BuscarProfesionalesUseCase {
    private final CatalogoRepository catalogo;

    @Override
    public LecturaBusquedaProfesionales buscar(FiltroProfesionales filtro) {
        if (filtro == null || filtro.getPage() < 0 || filtro.getSize() < 1 || filtro.getSize() > 100
                || (long) filtro.getPage() * filtro.getSize() > Integer.MAX_VALUE
                || (filtro.getCategoryId() != null && filtro.getCategoryId() < 1)
                || (filtro.getName() != null && filtro.getName().length() > 100)
                || (filtro.getTime() != null && filtro.getDayOfWeek() == null)) {
            throw new FiltroInvalidoException();
        }
        var lectura = catalogo.buscarProfesionales(filtro);
        if (lectura.getEstado() == null) throw new CatalogoNoInicializadoException();
        if (!lectura.isCategoriaValida()) throw new CategoriaInvalidaException();
        return lectura;
    }
}
