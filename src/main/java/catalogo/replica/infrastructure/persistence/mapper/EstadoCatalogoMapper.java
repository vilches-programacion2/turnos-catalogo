package catalogo.replica.infrastructure.persistence.mapper;

import catalogo.replica.domain.model.EstadoCatalogo;
import catalogo.replica.infrastructure.persistence.entity.EstadoCatalogoEntity;
import org.springframework.stereotype.Component;

@Component
public class EstadoCatalogoMapper {

    public EstadoCatalogoEntity toEntity(EstadoCatalogo estadoCatalogo) {
        if (estadoCatalogo == null) {
            return null;
        }
        return EstadoCatalogoEntity.builder()
                .id(1L)
                .versionAplicada(estadoCatalogo.getVersionAplicada())
                .actualizadoEn(estadoCatalogo.getActualizadoEn())
                .build();
    }

    public EstadoCatalogo toDomainModel(EstadoCatalogoEntity entity) {
        if (entity == null) {
            return null;
        }
        return EstadoCatalogo.builder()
                .versionAplicada(entity.getVersionAplicada())
                .actualizadoEn(entity.getActualizadoEn())
                .build();
    }
}
