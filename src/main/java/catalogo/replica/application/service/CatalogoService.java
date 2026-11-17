package catalogo.replica.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import catalogo.replica.application.exception.ProfesionalNoEncontradoException;
import catalogo.replica.domain.model.AgendaProfesional;
import catalogo.replica.domain.model.Profesional;
import catalogo.replica.domain.ports.in.ObtenerAgendaProfesionalUseCase;
import catalogo.replica.domain.ports.in.ObtenerProfesionalUseCase;
import catalogo.replica.domain.model.EstadoCatalogo;
import catalogo.replica.domain.ports.in.InicializarCatalogoUseCase;
import catalogo.replica.domain.ports.in.SincronizarCatalogoUseCase;
import catalogo.replica.domain.ports.in.BuscarProfesionalesUseCase;
import catalogo.replica.domain.ports.in.ListarCategoriasUseCase;
import catalogo.replica.domain.model.FiltroProfesionales;
import catalogo.replica.domain.model.LecturaBusquedaProfesionales;
import catalogo.replica.domain.model.LecturaCategorias;

@Service
@RequiredArgsConstructor
public class CatalogoService {
    private final ObtenerProfesionalUseCase obtenerProfesional;
    private final ObtenerAgendaProfesionalUseCase obtenerAgenda;
    private final InicializarCatalogoUseCase inicializarCatalogo;
    private final SincronizarCatalogoUseCase sincronizarCatalogo;
    private final BuscarProfesionalesUseCase buscarProfesionales;
    private final ListarCategoriasUseCase listarCategorias;

    public LecturaBusquedaProfesionales buscar(FiltroProfesionales filtro) {
        return buscarProfesionales.buscar(filtro);
    }

    public LecturaCategorias listarCategorias() {
        return listarCategorias.listar();
    }

    public Profesional obtenerProfesional(Long id) {
        return obtenerProfesional.obtener(id).orElseThrow(ProfesionalNoEncontradoException::new);
    }

    public AgendaProfesional obtenerAgenda(Long id) {
        return obtenerAgenda.obtener(id).orElseThrow(ProfesionalNoEncontradoException::new);
    }

    public EstadoCatalogo inicializar() {
        return inicializarCatalogo.inicializar();
    }

    public EstadoCatalogo sincronizar() {
        return sincronizarCatalogo.sincronizar();
    }
}
