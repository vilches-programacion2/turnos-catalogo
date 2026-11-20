package catalogo.replica.domain.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Lectura consistente; estado o profesional ausentes se distinguen en aplicación. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LecturaProfesional {
    private EstadoCatalogo estado;
    private Profesional profesional;
    private List<HorarioSemanal> horarios;
}
