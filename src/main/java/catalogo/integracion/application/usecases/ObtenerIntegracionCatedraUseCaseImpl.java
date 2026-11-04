package catalogo.integracion.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import catalogo.integracion.domain.model.IntegracionCatedra;
import catalogo.integracion.domain.ports.in.ObtenerIntegracionCatedraUseCase;
import catalogo.integracion.domain.ports.out.CatedraClient;

@Component
@RequiredArgsConstructor
public class ObtenerIntegracionCatedraUseCaseImpl implements ObtenerIntegracionCatedraUseCase {
    private final CatedraClient catedra;

    @Override
    public IntegracionCatedra obtener() {
        return catedra.obtenerIntegracion();
    }
}
