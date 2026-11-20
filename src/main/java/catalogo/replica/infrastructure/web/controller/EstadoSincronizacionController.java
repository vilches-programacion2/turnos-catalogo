package catalogo.replica.infrastructure.web.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import catalogo.replica.application.service.CatalogoService;
import catalogo.replica.domain.model.DiagnosticoCatalogo;

@RestController
@RequiredArgsConstructor
public class EstadoSincronizacionController {
    private final CatalogoService catalogo;

    @GetMapping("/api/catalog/sync/status")
    public ResponseEntity<EstadoSincronizacionResponse> consultar() {
        var estado = catalogo.consultarEstadoSincronizacion();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new EstadoSincronizacionResponse(estado.versionAplicada(), estado.estado(), estado.ultimoError()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail persistenciaNoDisponible() {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo consultar el estado de sincronización.");
        problema.setProperty("code", "CATALOG_STATUS_UNAVAILABLE");
        return problema;
    }

    public record EstadoSincronizacionResponse(Long versionAplicada, DiagnosticoCatalogo.Estado estado,
            DiagnosticoCatalogo.Codigo ultimoError) {}
}
