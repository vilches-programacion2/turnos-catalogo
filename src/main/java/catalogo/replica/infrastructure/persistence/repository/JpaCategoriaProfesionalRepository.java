package catalogo.replica.infrastructure.persistence.repository;

import catalogo.replica.infrastructure.persistence.entity.CategoriaProfesionalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaCategoriaProfesionalRepository extends JpaRepository<CategoriaProfesionalEntity, Long> {
}
