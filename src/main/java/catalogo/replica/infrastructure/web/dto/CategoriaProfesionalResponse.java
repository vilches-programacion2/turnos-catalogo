package catalogo.replica.infrastructure.web.dto;

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
public class CategoriaProfesionalResponse {
    private Long id;
    private String name;
    private Boolean enabled;
}
