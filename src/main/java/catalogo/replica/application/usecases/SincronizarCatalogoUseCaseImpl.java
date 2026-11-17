package catalogo.replica.application.usecases;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import catalogo.replica.application.validation.SnapshotCatalogoValidator;
import catalogo.replica.domain.model.EstadoCatalogo;
import catalogo.replica.domain.ports.in.SincronizarCatalogoUseCase;
import catalogo.replica.domain.ports.out.CatalogoRepository;
import catalogo.replica.domain.ports.out.SnapshotCatalogoClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class SincronizarCatalogoUseCaseImpl implements SincronizarCatalogoUseCase {
    private final SnapshotCatalogoClient cliente;
    private final SnapshotCatalogoValidator validator;
    private final CatalogoRepository catalogo;

    @Override
    public EstadoCatalogo sincronizar() {
        // La espera HTTP y la validación ocurren antes de abrir la transacción de reemplazo.
        var snapshot = cliente.descargar();
        validator.validar(snapshot);
        var estado = catalogo.reemplazarSnapshot(snapshot);
        log.info("Snapshot aplicado: version={} categorias={} profesionales={} horarios={}",
                estado.getVersionAplicada(), snapshot.getProfessionalCategories().size(),
                snapshot.getProfessionals().size(), snapshot.getWeeklySchedules().size());
        return estado;
    }
}
