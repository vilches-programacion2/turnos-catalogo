package catalogo.integracion.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import catalogo.integracion.application.exception.IntegracionCatedraException;
import catalogo.integracion.application.service.IntegracionCatedraService;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.catedra", name = "enabled", havingValue = "true")
public class CatedraStartupCheck implements ApplicationRunner {
    private final IntegracionCatedraService integracion;

    @Override
    public void run(ApplicationArguments args) {
        try {
            var configuracion = integracion.obtener();
            log.info("Integración de cátedra consultada: estado={}", configuracion.getProvisioningStatus());
        } catch (IntegracionCatedraException e) {
            // La copia local puede seguir atendiendo consultas aunque cátedra no esté disponible.
            log.warn("No se pudo consultar integración de cátedra: error={} httpStatus={}",
                    e.getError(), e.getHttpStatus());
        }
    }
}
