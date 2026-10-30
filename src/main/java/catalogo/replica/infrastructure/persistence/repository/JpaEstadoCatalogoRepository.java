package catalogo.replica.infrastructure.persistence.repository;

import catalogo.replica.infrastructure.persistence.entity.EstadoCatalogoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaEstadoCatalogoRepository extends JpaRepository<EstadoCatalogoEntity, Long> {
}
