package catalogo.replica.application.usecases;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import catalogo.replica.application.exception.CatalogoNoInicializadoException;
import catalogo.replica.domain.model.Profesional;
import catalogo.replica.domain.ports.in.ObtenerProfesionalUseCase;
import catalogo.replica.domain.ports.out.CatalogoRepository;

@Component
@RequiredArgsConstructor
public class ObtenerProfesionalUseCaseImpl implements ObtenerProfesionalUseCase {
    private final CatalogoRepository catalogo;

    @Override
    public Optional<Profesional> obtener(Long id) {
        var lectura = catalogo.leerProfesional(id);
        if (lectura.getEstado() == null) {
            throw new CatalogoNoInicializadoException();
        }
        return Optional.ofNullable(lectura.getProfesional());
    }
}
