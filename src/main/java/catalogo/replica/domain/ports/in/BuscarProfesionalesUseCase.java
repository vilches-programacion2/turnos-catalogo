package catalogo.replica.domain.ports.in;

import catalogo.replica.domain.model.LecturaBusquedaProfesionales;
import catalogo.replica.domain.model.FiltroProfesionales;

public interface BuscarProfesionalesUseCase {
    LecturaBusquedaProfesionales buscar(FiltroProfesionales filtro);
}
