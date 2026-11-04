package catalogo.replica.infrastructure.web.mapper;

import org.springframework.stereotype.Component;
import catalogo.replica.domain.model.AgendaProfesional;
import catalogo.replica.domain.model.HorarioSemanal;
import catalogo.replica.domain.model.Profesional;
import catalogo.replica.infrastructure.web.dto.AgendaProfesionalResponse;
import catalogo.replica.infrastructure.web.dto.HorarioSemanalResponse;
import catalogo.replica.infrastructure.web.dto.ProfesionalResponse;

@Component
public class CatalogoDtoMapper {
    public ProfesionalResponse toResponse(Profesional profesional) {
        if (profesional == null) {
            return null;
        }
        return ProfesionalResponse.builder().id(profesional.getId()).categoryId(profesional.getCategoryId())
                .firstName(profesional.getFirstName()).lastName(profesional.getLastName())
                .enabled(profesional.getEnabled()).build();
    }

    public HorarioSemanalResponse toResponse(HorarioSemanal horario) {
        if (horario == null) {
            return null;
        }
        return HorarioSemanalResponse.builder().id(horario.getId()).dayOfWeek(horario.getDayOfWeek())
                .startTime(horario.getStartTime()).endTime(horario.getEndTime())
                .slotDurationMinutes(horario.getSlotDurationMinutes()).build();
    }

    public AgendaProfesionalResponse toResponse(AgendaProfesional agenda) {
        if (agenda == null) {
            return null;
        }
        return AgendaProfesionalResponse.builder().versionAplicada(agenda.getVersionAplicada())
                .professional(toResponse(agenda.getProfesional()))
                .weeklySchedules(agenda.getHorarios().stream().map(this::toResponse).toList()).build();
    }
}
