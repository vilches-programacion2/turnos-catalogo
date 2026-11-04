package catalogo.replica.infrastructure.web.dto;

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
public class AgendaProfesionalResponse {
    private Long versionAplicada;
    private ProfesionalResponse professional;
    private List<HorarioSemanalResponse> weeklySchedules;
}
