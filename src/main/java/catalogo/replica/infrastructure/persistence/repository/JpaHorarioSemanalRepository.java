package catalogo.replica.infrastructure.persistence.repository;

import catalogo.replica.infrastructure.persistence.entity.HorarioSemanalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaHorarioSemanalRepository extends JpaRepository<HorarioSemanalEntity, Long> {
}
