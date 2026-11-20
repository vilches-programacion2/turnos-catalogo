package catalogo.replica.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import catalogo.replica.application.service.MonitorSincronizacionCatalogo;
import catalogo.replica.domain.model.DiagnosticoCatalogo;
import catalogo.replica.domain.model.EstadoCatalogo;
import catalogo.replica.domain.ports.in.ConsultarEstadoSincronizacionUseCase;
import catalogo.replica.domain.ports.out.CatalogoRepository;

@Component
@RequiredArgsConstructor
public class ConsultarEstadoSincronizacionUseCaseImpl implements ConsultarEstadoSincronizacionUseCase {
    private final CatalogoRepository catalogo;
    private final MonitorSincronizacionCatalogo monitor;

    @Override
    public DiagnosticoCatalogo consultar() {
        var version = catalogo.obtenerEstado().map(EstadoCatalogo::getVersionAplicada).orElse(null);
        return monitor.consultar(version);
    }
}
