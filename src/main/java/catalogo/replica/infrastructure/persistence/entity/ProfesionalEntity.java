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
@Table(name = "professional")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfesionalEntity {
    @Id
    private Long id;
    @Column(name = "category_id", nullable = false)
    private Long categoryId;
    @Column(name = "first_name", nullable = false, columnDefinition = "text")
    private String firstName;
    @Column(name = "last_name", nullable = false, columnDefinition = "text")
    private String lastName;
    @Column(nullable = false)
    private Boolean enabled;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
