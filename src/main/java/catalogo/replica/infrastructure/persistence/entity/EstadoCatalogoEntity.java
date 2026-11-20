package catalogo.replica.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "catalog_state")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstadoCatalogoEntity {
    @Id
    private Long id;
    @Column(name = "applied_version", nullable = false)
    private Long versionAplicada;
    @Column(name = "updated_at", nullable = false)
    private Instant actualizadoEn;
}
