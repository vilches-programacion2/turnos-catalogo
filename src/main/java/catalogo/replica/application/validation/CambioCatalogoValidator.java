package catalogo.replica.application.validation;

import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import catalogo.replica.application.exception.IncrementalCatalogoException;
import catalogo.replica.application.exception.IncrementalCatalogoException.Error;
import catalogo.replica.domain.model.CambioCatalogo;
import catalogo.replica.domain.model.CategoriaProfesional;
import catalogo.replica.domain.model.Profesional;
import catalogo.replica.domain.model.HorarioSemanal;

@Component
public class CambioCatalogoValidator {
    public void validar(CambioCatalogo cambio, long versionEsperada) {
        comprobar(cambio != null && Long.valueOf(versionEsperada).equals(cambio.getVersion()));
        ids(cambio.getCategorias(), CategoriaProfesional::getId);
        ids(cambio.getProfesionales(), Profesional::getId);
        ids(cambio.getHorarios(), HorarioSemanal::getId);
        for (var c : cambio.getCategorias()) {
            comprobar(c.getName() != null && c.getEnabled() != null && c.getCreatedAt() != null && c.getUpdatedAt() != null);
        }
        for (var p : cambio.getProfesionales()) {
            comprobar(positivo(p.getCategoryId()) && p.getFirstName() != null && p.getLastName() != null
                    && p.getEnabled() != null && p.getCreatedAt() != null && p.getUpdatedAt() != null);
        }
        for (var h : cambio.getHorarios()) {
            comprobar(positivo(h.getProfessionalId()) && h.getDayOfWeek() != null && h.getStartTime() != null
                    && h.getEndTime() != null && h.getEndTime().isAfter(h.getStartTime())
                    && h.getSlotDurationMinutes() != null && h.getSlotDurationMinutes() > 0
                    && h.getEnabled() != null && h.getCreatedAt() != null && h.getUpdatedAt() != null);
        }
    }

    private <T> void ids(List<T> elementos, Function<T, Long> id) {
        comprobar(elementos != null);
        var vistos = new HashSet<Long>();
        for (var elemento : elementos) {
            comprobar(elemento != null);
            comprobar(positivo(id.apply(elemento)) && vistos.add(id.apply(elemento)));
        }
    }

    private boolean positivo(Long id) { return id != null && id > 0; }
    private void comprobar(boolean valido) {
        if (!valido) throw new IncrementalCatalogoException(Error.RESPUESTA_INVALIDA);
    }
}
