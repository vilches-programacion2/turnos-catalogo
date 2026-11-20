package catalogo.replica.infrastructure.redis;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CambiosIdsResponse {
    private Long version;
    private Ids changes;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Ids {
        private List<Long> professionalCategories;
        private List<Long> professionals;
        private List<Long> weeklySchedules;
    }
}
