package catalogo.replica.domain.ports.out;

import java.util.Optional;
import catalogo.replica.domain.model.CategoriaProfesional;
import catalogo.replica.domain.model.EstadoCatalogo;
import catalogo.replica.domain.model.HorarioSemanal;
import catalogo.replica.domain.model.LecturaProfesional;
import catalogo.replica.domain.model.Profesional;
import catalogo.replica.domain.model.SnapshotCatalogo;
import catalogo.replica.domain.model.FiltroProfesionales;
import catalogo.replica.domain.model.LecturaBusquedaProfesionales;
import catalogo.replica.domain.model.LecturaCategorias;

public interface CatalogoRepository {
    CategoriaProfesional guardarCategoria(CategoriaProfesional categoria);
    Optional<CategoriaProfesional> buscarCategoria(Long id);
    Profesional guardarProfesional(Profesional profesional);
    Optional<Profesional> buscarProfesional(Long id);
    HorarioSemanal guardarHorario(HorarioSemanal horario);
    Optional<HorarioSemanal> buscarHorario(Long id);
    Optional<EstadoCatalogo> obtenerEstado();
    EstadoCatalogo guardarEstado(EstadoCatalogo estado);
    LecturaProfesional leerProfesional(Long id);
    EstadoCatalogo reemplazarSnapshot(SnapshotCatalogo snapshot);
    LecturaBusquedaProfesionales buscarProfesionales(FiltroProfesionales filtro);
    LecturaCategorias listarCategorias();
}
