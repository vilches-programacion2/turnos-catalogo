package catalogo.replica.infrastructure.web.controller;

import jakarta.validation.constraints.Positive;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import catalogo.replica.application.exception.CatalogoNoInicializadoException;
import catalogo.replica.application.exception.ProfesionalDeshabilitadoException;
import catalogo.replica.application.exception.ProfesionalNoEncontradoException;
import catalogo.replica.application.exception.CategoriaInvalidaException;
import catalogo.replica.application.exception.FiltroInvalidoException;
import catalogo.replica.domain.model.FiltroProfesionales;
import catalogo.replica.application.service.CatalogoService;
import catalogo.replica.infrastructure.web.dto.AgendaProfesionalResponse;
import catalogo.replica.infrastructure.web.dto.ProfesionalResponse;
import catalogo.replica.infrastructure.web.dto.BusquedaProfesionalesResponse;
import catalogo.replica.infrastructure.web.dto.CategoriasResponse;
import catalogo.replica.infrastructure.web.mapper.CatalogoDtoMapper;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogoController {
    private final CatalogoService catalogo;
    private final CatalogoDtoMapper mapper;

    @GetMapping("/professionals")
    public BusquedaProfesionalesResponse buscar(@RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String name, @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) DayOfWeek dayOfWeek,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return mapper.toResponse(catalogo.buscar(FiltroProfesionales.builder().categoryId(categoryId).name(name)
                .enabled(enabled).dayOfWeek(dayOfWeek).time(time).page(page).size(size).build()));
    }

    @GetMapping("/professional-categories")
    public CategoriasResponse categorias() {
        return mapper.toResponse(catalogo.listarCategorias());
    }

    @GetMapping("/professionals/{id}")
    public ProfesionalResponse obtener(@PathVariable @Positive Long id) {
        return mapper.toResponse(catalogo.obtenerProfesional(id));
    }

    @GetMapping("/professionals/{id}/schedule")
    public AgendaProfesionalResponse agenda(@PathVariable @Positive Long id) {
        return mapper.toResponse(catalogo.obtenerAgenda(id));
    }

    @ExceptionHandler(CatalogoNoInicializadoException.class)
    public ProblemDetail noInicializado(CatalogoNoInicializadoException exception) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_NOT_READY", exception.getMessage());
    }

    @ExceptionHandler(ProfesionalNoEncontradoException.class)
    public ProblemDetail noEncontrado(ProfesionalNoEncontradoException exception) {
        return problema(HttpStatus.NOT_FOUND, "PROFESSIONAL_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ProfesionalDeshabilitadoException.class)
    public ProblemDetail deshabilitado(ProfesionalDeshabilitadoException exception) {
        return problema(HttpStatus.CONFLICT, "PROFESSIONAL_DISABLED", exception.getMessage());
    }

    @ExceptionHandler(CategoriaInvalidaException.class)
    public ProblemDetail categoriaInvalida(CategoriaInvalidaException exception) {
        return problema(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY", exception.getMessage());
    }

    @ExceptionHandler(FiltroInvalidoException.class)
    public ProblemDetail filtroInvalido(FiltroInvalidoException exception) {
        return problema(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
    }

    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail idInvalido() {
        return problema(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Los parámetros de consulta no son válidos.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail persistenciaNoDisponible() {
        log.warn("Consulta de catálogo fallida: resultado=error codigo=CATALOG_UNAVAILABLE");
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE", "El catálogo no está disponible.");
    }

    private ProblemDetail problema(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
