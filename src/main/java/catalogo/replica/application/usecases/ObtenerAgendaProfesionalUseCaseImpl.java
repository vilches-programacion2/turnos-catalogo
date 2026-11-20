package catalogo.replica.application.usecases;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import catalogo.replica.application.exception.CatalogoNoInicializadoException;
import catalogo.replica.application.exception.ProfesionalDeshabilitadoException;
import catalogo.replica.domain.model.AgendaProfesional;
import catalogo.replica.domain.ports.in.ObtenerAgendaProfesionalUseCase;
import catalogo.replica.domain.ports.out.CatalogoRepository;

@Component
@RequiredArgsConstructor
public class ObtenerAgendaProfesionalUseCaseImpl implements ObtenerAgendaProfesionalUseCase {
    private final CatalogoRepository catalogo;

    @Override
    public Optional<AgendaProfesional> obtener(Long id) {
        var lectura = catalogo.leerProfesional(id);
        if (lectura.getEstado() == null) {
            throw new CatalogoNoInicializadoException();
        }
        if (lectura.getProfesional() == null) {
            return Optional.empty();
        }
        if (!Boolean.TRUE.equals(lectura.getProfesional().getEnabled())) {
            throw new ProfesionalDeshabilitadoException();
        }
        return Optional.of(AgendaProfesional.builder().versionAplicada(lectura.getEstado().getVersionAplicada())
                .profesional(lectura.getProfesional()).horarios(lectura.getHorarios()).build());
    }
}
