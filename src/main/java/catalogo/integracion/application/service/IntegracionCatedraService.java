package catalogo.integracion.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import catalogo.integracion.domain.model.IntegracionCatedra;
import catalogo.integracion.domain.ports.in.ObtenerIntegracionCatedraUseCase;

@Service
@RequiredArgsConstructor
public class IntegracionCatedraService {
    private final ObtenerIntegracionCatedraUseCase obtenerIntegracion;

    public IntegracionCatedra obtener() {
        return obtenerIntegracion.obtener();
    }
}
