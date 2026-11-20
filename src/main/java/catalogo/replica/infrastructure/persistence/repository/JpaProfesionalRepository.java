package catalogo.replica.infrastructure.persistence.repository;

import catalogo.replica.infrastructure.persistence.entity.ProfesionalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaProfesionalRepository extends JpaRepository<ProfesionalEntity, Long> {
}
