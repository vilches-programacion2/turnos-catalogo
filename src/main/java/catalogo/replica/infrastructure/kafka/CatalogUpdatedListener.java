package catalogo.replica.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import catalogo.replica.application.exception.IncrementalCatalogoException;
import catalogo.replica.application.exception.IncrementalCatalogoException.Error;
import catalogo.replica.application.service.CatalogoService;
import catalogo.replica.application.service.MonitorSincronizacionCatalogo;
import catalogo.replica.domain.model.DiagnosticoCatalogo.Codigo;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogUpdatedListener implements AcknowledgingMessageListener<String, String> {
    private final CatalogoService catalogo;
    private final JsonMapper json;
    private final MonitorSincronizacionCatalogo monitor;

    @Override
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            var evento = parsear(record);
            catalogo.actualizar(evento.getEventId(), evento.getNewVersion());
            // El caso de uso termina después del commit de cambios, checkpoint y deduplicación.
            acknowledgment.acknowledge();
            monitor.eventoConfirmado(record.partition(), record.offset());
        } catch (IncrementalCatalogoException e) {
            monitor.errorEvento(record.partition(), record.offset(), Codigo.valueOf(e.getError().name()));
            log.warn("Consumo de catálogo detenido: particion={} offset={} codigo={}", record.partition(), record.offset(), e.getError());
            throw e;
        } catch (DataAccessException e) {
            monitor.errorEvento(record.partition(), record.offset(), Codigo.PERSISTENCIA_NO_DISPONIBLE);
            log.warn("Consumo de catálogo detenido: particion={} offset={} codigo=PERSISTENCIA_NO_DISPONIBLE", record.partition(), record.offset());
            throw new IncrementalCatalogoException(Error.PERSISTENCIA_NO_DISPONIBLE);
        } catch (RuntimeException e) {
            monitor.errorEvento(record.partition(), record.offset(), Codigo.KAFKA_NO_DISPONIBLE);
            throw e;
        }
    }

    private CatalogUpdatedResponse parsear(ConsumerRecord<String, String> record) {
        try {
            if (record.value() == null) throw new IllegalArgumentException();
            var e = json.readValue(record.value(), CatalogUpdatedResponse.class);
            if (e == null || e.getEventId() == null || e.getEventId().isBlank()
                    || !"CatalogUpdated".equals(e.getEventType()) || !Integer.valueOf(1).equals(e.getSchemaVersion())
                    || e.getNewVersion() == null || e.getNewVersion() < 0 || e.getOccurredAt() == null
                    || e.getSource() == null || e.getSource().isBlank()
                    || !e.getNewVersion().toString().equals(record.key())) throw new IllegalArgumentException();
            return e;
        } catch (RuntimeException e) {
            throw new IncrementalCatalogoException(Error.EVENTO_INVALIDO);
        }
    }
}
