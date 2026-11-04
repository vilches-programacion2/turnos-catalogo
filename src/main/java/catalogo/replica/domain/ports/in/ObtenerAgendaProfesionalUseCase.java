package catalogo.replica.domain.ports.in;

import java.util.Optional;
import catalogo.replica.domain.model.AgendaProfesional;

public interface ObtenerAgendaProfesionalUseCase {
    Optional<AgendaProfesional> obtener(Long id);
}
