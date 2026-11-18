package catalogo.replica.infrastructure.kafka;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CatalogUpdatedResponse {
    private String eventId;
    private String eventType;
    private Long newVersion;
    private Instant occurredAt;
    private String source;
    private Integer schemaVersion;
}
