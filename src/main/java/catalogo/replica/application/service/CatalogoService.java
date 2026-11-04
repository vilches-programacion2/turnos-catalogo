package catalogo.replica.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import catalogo.replica.application.exception.ProfesionalNoEncontradoException;
import catalogo.replica.domain.model.AgendaProfesional;
import catalogo.replica.domain.model.Profesional;
import catalogo.replica.domain.ports.in.ObtenerAgendaProfesionalUseCase;
import catalogo.replica.domain.ports.in.ObtenerProfesionalUseCase;

@Service
@RequiredArgsConstructor
public class CatalogoService {
    private final ObtenerProfesionalUseCase obtenerProfesional;
    private final ObtenerAgendaProfesionalUseCase obtenerAgenda;

    public Profesional obtenerProfesional(Long id) {
        return obtenerProfesional.obtener(id).orElseThrow(ProfesionalNoEncontradoException::new);
    }

    public AgendaProfesional obtenerAgenda(Long id) {
        return obtenerAgenda.obtener(id).orElseThrow(ProfesionalNoEncontradoException::new);
    }
}
