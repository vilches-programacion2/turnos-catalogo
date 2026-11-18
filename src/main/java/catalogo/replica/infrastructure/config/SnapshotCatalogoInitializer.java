package catalogo.replica.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import catalogo.replica.application.exception.SnapshotCatalogoException;
import catalogo.replica.application.service.CatalogoService;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.catedra", name = "enabled", havingValue = "true")
public class SnapshotCatalogoInitializer implements ApplicationRunner {
    private final CatalogoService catalogo;

    @Override
    public void run(ApplicationArguments args) {
        try {
            catalogo.inicializar();
        } catch (SnapshotCatalogoException e) {
            log.warn("Inicialización de catálogo pendiente: error={} httpStatus={}", e.getError(), e.getHttpStatus());
        } catch (DataAccessException e) {
            log.warn("Inicialización de catálogo pendiente: error=PERSISTENCIA_NO_DISPONIBLE");
        }
    }
}
