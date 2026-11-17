package catalogo.replica.infrastructure.web.mapper;

import org.springframework.stereotype.Component;
import catalogo.replica.domain.model.AgendaProfesional;
import catalogo.replica.domain.model.HorarioSemanal;
import catalogo.replica.domain.model.Profesional;
import catalogo.replica.infrastructure.web.dto.AgendaProfesionalResponse;
import catalogo.replica.infrastructure.web.dto.HorarioSemanalResponse;
import catalogo.replica.infrastructure.web.dto.ProfesionalResponse;
import catalogo.replica.domain.model.LecturaBusquedaProfesionales;
import catalogo.replica.domain.model.LecturaCategorias;
import catalogo.replica.infrastructure.web.dto.BusquedaProfesionalesResponse;
import catalogo.replica.infrastructure.web.dto.CategoriasResponse;
import catalogo.replica.infrastructure.web.dto.CategoriaProfesionalResponse;

@Component
public class CatalogoDtoMapper {
    public BusquedaProfesionalesResponse toResponse(LecturaBusquedaProfesionales lectura) {
        if (lectura == null) return null;
        return BusquedaProfesionalesResponse.builder().versionAplicada(lectura.getEstado().getVersionAplicada())
                .content(lectura.getProfesionales().stream().map(this::toResponse).toList())
                .page(lectura.getPage()).size(lectura.getSize()).totalElements(lectura.getTotalElements()).build();
    }

    public CategoriasResponse toResponse(LecturaCategorias lectura) {
        if (lectura == null) return null;
        return CategoriasResponse.builder().versionAplicada(lectura.getEstado().getVersionAplicada())
                .content(lectura.getCategorias().stream().map(categoria -> CategoriaProfesionalResponse.builder()
                        .id(categoria.getId()).name(categoria.getName()).enabled(categoria.getEnabled()).build()).toList())
                .build();
    }

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
