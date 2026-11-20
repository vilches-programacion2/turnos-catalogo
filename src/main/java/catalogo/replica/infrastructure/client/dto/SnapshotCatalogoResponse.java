package catalogo.replica.infrastructure.client.dto;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
public class SnapshotCatalogoResponse {
    private Long snapshotVersion;
    private Instant generatedAt;
    private List<CategoriaProfesionalSnapshotResponse> professionalCategories;
    private List<ProfesionalSnapshotResponse> professionals;
    private List<HorarioSemanalSnapshotResponse> weeklySchedules;
}
