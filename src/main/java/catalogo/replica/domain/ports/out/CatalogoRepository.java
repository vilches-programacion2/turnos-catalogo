package catalogo.replica.domain.ports.out;

import java.util.Optional;
import catalogo.replica.domain.model.CategoriaProfesional;
import catalogo.replica.domain.model.EstadoCatalogo;
import catalogo.replica.domain.model.HorarioSemanal;
import catalogo.replica.domain.model.Profesional;

public interface CatalogoRepository {
    CategoriaProfesional guardarCategoria(CategoriaProfesional categoria);
    Optional<CategoriaProfesional> buscarCategoria(Long id);
    Profesional guardarProfesional(Profesional profesional);
    Optional<Profesional> buscarProfesional(Long id);
    HorarioSemanal guardarHorario(HorarioSemanal horario);
    Optional<HorarioSemanal> buscarHorario(Long id);
    Optional<EstadoCatalogo> obtenerEstado();
    EstadoCatalogo guardarEstado(EstadoCatalogo estado);
}
