package catalogo.replica.infrastructure.client.mapper;

import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import catalogo.replica.domain.model.CategoriaProfesional;
import catalogo.replica.domain.model.HorarioSemanal;
import catalogo.replica.domain.model.Profesional;
import catalogo.replica.domain.model.SnapshotCatalogo;
import catalogo.replica.infrastructure.client.dto.CategoriaProfesionalSnapshotResponse;
import catalogo.replica.infrastructure.client.dto.HorarioSemanalSnapshotResponse;
import catalogo.replica.infrastructure.client.dto.ProfesionalSnapshotResponse;
import catalogo.replica.infrastructure.client.dto.SnapshotCatalogoResponse;

@Component
public class SnapshotCatalogoMapper {
    public SnapshotCatalogo toDomain(SnapshotCatalogoResponse response) {
        if (response == null) return null;
        return SnapshotCatalogo.builder().snapshotVersion(response.getSnapshotVersion())
                .generatedAt(response.getGeneratedAt())
                .professionalCategories(mapear(response.getProfessionalCategories(), this::toDomain))
                .professionals(mapear(response.getProfessionals(), this::toDomain))
                .weeklySchedules(mapear(response.getWeeklySchedules(), this::toDomain)).build();
    }

    public CategoriaProfesional toDomain(CategoriaProfesionalSnapshotResponse response) {
        if (response == null) return null;
        return CategoriaProfesional.builder().id(response.getId()).name(response.getName())
                .description(response.getDescription()).enabled(response.getEnabled())
                .createdAt(response.getCreatedAt()).updatedAt(response.getUpdatedAt()).build();
    }

    public Profesional toDomain(ProfesionalSnapshotResponse response) {
        if (response == null) return null;
        return Profesional.builder().id(response.getId()).categoryId(response.getCategoryId())
                .firstName(response.getFirstName()).lastName(response.getLastName()).enabled(response.getEnabled())
                .createdAt(response.getCreatedAt()).updatedAt(response.getUpdatedAt()).build();
    }

    public HorarioSemanal toDomain(HorarioSemanalSnapshotResponse response) {
        if (response == null) return null;
        return HorarioSemanal.builder().id(response.getId()).professionalId(response.getProfessionalId())
                .dayOfWeek(response.getDayOfWeek()).startTime(response.getStartTime()).endTime(response.getEndTime())
                .slotDurationMinutes(response.getSlotDurationMinutes()).enabled(response.getEnabled())
                .createdAt(response.getCreatedAt()).updatedAt(response.getUpdatedAt()).build();
    }

    private <T, R> List<R> mapear(List<T> source, Function<T, R> mapper) {
        return source == null ? null : source.stream().map(mapper).toList();
    }
}
