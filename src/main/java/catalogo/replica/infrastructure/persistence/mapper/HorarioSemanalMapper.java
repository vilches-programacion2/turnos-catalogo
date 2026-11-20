package catalogo.replica.infrastructure.persistence.mapper;

import catalogo.replica.domain.model.HorarioSemanal;
import catalogo.replica.infrastructure.persistence.entity.HorarioSemanalEntity;
import org.springframework.stereotype.Component;

@Component
public class HorarioSemanalMapper {

    public HorarioSemanalEntity toEntity(HorarioSemanal horarioSemanal) {
        if (horarioSemanal == null) {
            return null;
        }
        return HorarioSemanalEntity.builder()
                .id(horarioSemanal.getId())
                .professionalId(horarioSemanal.getProfessionalId())
                .dayOfWeek(horarioSemanal.getDayOfWeek())
                .startTime(horarioSemanal.getStartTime())
                .endTime(horarioSemanal.getEndTime())
                .slotDurationMinutes(horarioSemanal.getSlotDurationMinutes())
                .enabled(horarioSemanal.getEnabled())
                .createdAt(horarioSemanal.getCreatedAt())
                .updatedAt(horarioSemanal.getUpdatedAt())
                .build();
    }

    public HorarioSemanal toDomainModel(HorarioSemanalEntity entity) {
        if (entity == null) {
            return null;
        }
        return HorarioSemanal.builder()
                .id(entity.getId())
                .professionalId(entity.getProfessionalId())
                .dayOfWeek(entity.getDayOfWeek())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .slotDurationMinutes(entity.getSlotDurationMinutes())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
