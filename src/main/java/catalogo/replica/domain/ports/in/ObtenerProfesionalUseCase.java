package catalogo.replica.domain.ports.in;

import java.util.Optional;
import catalogo.replica.domain.model.Profesional;

public interface ObtenerProfesionalUseCase {
    Optional<Profesional> obtener(Long id);
}
