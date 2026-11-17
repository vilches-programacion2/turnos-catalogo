package catalogo.replica.domain.model;

import java.time.Instant;
import java.util.List;
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
public class SnapshotCatalogo {
    private Long snapshotVersion;
    private Instant generatedAt;
    private List<CategoriaProfesional> professionalCategories;
    private List<Profesional> professionals;
    private List<HorarioSemanal> weeklySchedules;
}
