package catalogo.replica.domain.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
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
public class FiltroProfesionales {
    private Long categoryId;
    private String name;
    private Boolean enabled;
    private DayOfWeek dayOfWeek;
    private LocalTime time;
    private int page;
    private int size;
}
