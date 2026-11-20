package catalogo.replica.application.validation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import catalogo.replica.application.exception.SnapshotCatalogoException;
import catalogo.replica.application.exception.SnapshotCatalogoException.Error;
import catalogo.replica.domain.model.CategoriaProfesional;
import catalogo.replica.domain.model.HorarioSemanal;
import catalogo.replica.domain.model.Profesional;
import catalogo.replica.domain.model.SnapshotCatalogo;

@Component
public class SnapshotCatalogoValidator {
    public void validar(SnapshotCatalogo snapshot) {
        comprobar(snapshot != null && snapshot.getSnapshotVersion() != null
                && snapshot.getSnapshotVersion() >= 0 && snapshot.getGeneratedAt() != null);
        Set<Long> categorias = ids(snapshot.getProfessionalCategories(), CategoriaProfesional::getId);
        Set<Long> profesionales = ids(snapshot.getProfessionals(), Profesional::getId);
        ids(snapshot.getWeeklySchedules(), HorarioSemanal::getId);
        for (var categoria : snapshot.getProfessionalCategories()) {
            comprobar(categoria.getName() != null && categoria.getEnabled() != null
                    && categoria.getCreatedAt() != null && categoria.getUpdatedAt() != null);
        }
        for (var profesional : snapshot.getProfessionals()) {
            comprobar(categorias.contains(profesional.getCategoryId()) && profesional.getFirstName() != null
                    && profesional.getLastName() != null && profesional.getEnabled() != null
                    && profesional.getCreatedAt() != null && profesional.getUpdatedAt() != null);
        }
        for (var horario : snapshot.getWeeklySchedules()) {
            comprobar(profesionales.contains(horario.getProfessionalId()) && horario.getDayOfWeek() != null
                    && horario.getStartTime() != null && horario.getEndTime() != null
                    && horario.getSlotDurationMinutes() != null && horario.getSlotDurationMinutes() > 0
                    && horario.getEnabled() != null && horario.getCreatedAt() != null && horario.getUpdatedAt() != null);
        }
    }

    private <T> Set<Long> ids(List<T> elementos, Function<T, Long> extraerId) {
        comprobar(elementos != null);
        Set<Long> ids = new HashSet<>();
        for (var elemento : elementos) {
            comprobar(elemento != null);
            Long id = extraerId.apply(elemento);
            comprobar(id != null && id > 0 && ids.add(id));
        }
        return ids;
    }

    private void comprobar(boolean valido) {
        if (!valido) throw new SnapshotCatalogoException(Error.RESPUESTA_INVALIDA);
    }
}
