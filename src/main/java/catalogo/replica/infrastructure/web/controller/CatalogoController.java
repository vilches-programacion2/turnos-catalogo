package catalogo.replica.infrastructure.web.controller;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import catalogo.replica.application.exception.CatalogoNoInicializadoException;
import catalogo.replica.application.exception.ProfesionalDeshabilitadoException;
import catalogo.replica.application.exception.ProfesionalNoEncontradoException;
import catalogo.replica.application.service.CatalogoService;
import catalogo.replica.infrastructure.web.dto.AgendaProfesionalResponse;
import catalogo.replica.infrastructure.web.dto.ProfesionalResponse;
import catalogo.replica.infrastructure.web.mapper.CatalogoDtoMapper;

@Slf4j
@RestController
@RequestMapping("/api/professionals")
@RequiredArgsConstructor
public class CatalogoController {
    private final CatalogoService catalogo;
    private final CatalogoDtoMapper mapper;

    @GetMapping("/{id}")
    public ProfesionalResponse obtener(@PathVariable @Positive Long id) {
        return mapper.toResponse(catalogo.obtenerProfesional(id));
    }

    @GetMapping("/{id}/schedule")
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

    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail idInvalido() {
        return problema(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Se requiere un ID positivo válido.");
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
