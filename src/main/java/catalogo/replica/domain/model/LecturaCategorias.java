package catalogo.replica.domain.model;

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
public class LecturaCategorias {
    private EstadoCatalogo estado;
    private List<CategoriaProfesional> categorias;
}
