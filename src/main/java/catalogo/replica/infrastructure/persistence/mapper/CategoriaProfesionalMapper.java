package catalogo.replica.infrastructure.persistence.mapper;

import catalogo.replica.domain.model.CategoriaProfesional;
import catalogo.replica.infrastructure.persistence.entity.CategoriaProfesionalEntity;
import org.springframework.stereotype.Component;

@Component
public class CategoriaProfesionalMapper {

    public CategoriaProfesionalEntity toEntity(CategoriaProfesional categoriaProfesional) {
        if (categoriaProfesional == null) {
            return null;
        }
        return CategoriaProfesionalEntity.builder()
                .id(categoriaProfesional.getId())
                .name(categoriaProfesional.getName())
                .description(categoriaProfesional.getDescription())
                .enabled(categoriaProfesional.getEnabled())
                .createdAt(categoriaProfesional.getCreatedAt())
                .updatedAt(categoriaProfesional.getUpdatedAt())
                .build();
    }

    public CategoriaProfesional toDomainModel(CategoriaProfesionalEntity entity) {
        if (entity == null) {
            return null;
        }
        return CategoriaProfesional.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
