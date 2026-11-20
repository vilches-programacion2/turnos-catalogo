package catalogo.replica.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CategoriaProfesionalSnapshotResponse {
    private Long id;
    private String name;
    private String description;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}
