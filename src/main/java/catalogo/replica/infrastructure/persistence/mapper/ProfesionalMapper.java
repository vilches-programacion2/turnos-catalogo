package catalogo.replica.infrastructure.persistence.mapper;

import catalogo.replica.domain.model.Profesional;
import catalogo.replica.infrastructure.persistence.entity.ProfesionalEntity;
import org.springframework.stereotype.Component;

@Component
public class ProfesionalMapper {

    public ProfesionalEntity toEntity(Profesional profesional) {
        if (profesional == null) {
            return null;
        }
        return ProfesionalEntity.builder()
                .id(profesional.getId())
                .categoryId(profesional.getCategoryId())
                .firstName(profesional.getFirstName())
                .lastName(profesional.getLastName())
                .enabled(profesional.getEnabled())
                .createdAt(profesional.getCreatedAt())
                .updatedAt(profesional.getUpdatedAt())
                .build();
    }

    public Profesional toDomainModel(ProfesionalEntity entity) {
        if (entity == null) {
            return null;
        }
        return Profesional.builder()
                .id(entity.getId())
                .categoryId(entity.getCategoryId())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
