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
public class BusquedaProfesionalesResponse {
    private Long versionAplicada;
    private List<ProfesionalResponse> content;
    private int page;
    private int size;
    private long totalElements;
}
