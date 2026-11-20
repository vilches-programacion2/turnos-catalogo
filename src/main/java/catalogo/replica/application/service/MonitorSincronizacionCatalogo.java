package catalogo.replica.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Component;
import catalogo.replica.domain.model.DiagnosticoCatalogo;
import catalogo.replica.domain.model.DiagnosticoCatalogo.Codigo;
import catalogo.replica.domain.model.DiagnosticoCatalogo.Estado;

@Component
public class MonitorSincronizacionCatalogo {
    public enum Fuente { SNAPSHOT, REVISION, KAFKA_CONEXION, KAFKA_EVENTO }

    private final Duration vigencia;
    private final EnumMap<Fuente, Incidencia> errores = new EnumMap<>(Fuente.class);
    private long secuencia;
    private Instant ultimaRevision;
    private Long versionVerificada;
    private boolean kafkaAsignado;
    private Posicion eventoPendiente;

    public MonitorSincronizacionCatalogo(@Value("${app.catedra.sync.recovery-interval}") String intervalo) {
        vigencia = DurationStyle.detectAndParse(intervalo).multipliedBy(2);
    }

    public synchronized void registrarError(Fuente fuente, Codigo codigo) {
        errores.put(fuente, new Incidencia(++secuencia, codigo));
    }

    public synchronized void revisionCorrecta() {
        errores.remove(Fuente.SNAPSHOT);
        errores.remove(Fuente.REVISION);
    }

    public synchronized void versionVerificada(long version) {
        versionVerificada = version;
        ultimaRevision = Instant.now();
    }

    public synchronized void asignacionKafka(boolean asignado) {
        kafkaAsignado = asignado;
        if (asignado) errores.remove(Fuente.KAFKA_CONEXION);
    }

    public synchronized void errorEvento(int particion, long offset, Codigo codigo) {
        eventoPendiente = new Posicion(particion, offset);
        registrarError(Fuente.KAFKA_EVENTO, codigo);
    }

    public synchronized void eventoConfirmado(int particion, long offset) {
        if (eventoPendiente != null && eventoPendiente.particion() == particion && offset >= eventoPendiente.offset()) {
            eventoPendiente = null;
            errores.remove(Fuente.KAFKA_EVENTO);
        }
    }

    public synchronized DiagnosticoCatalogo consultar(Long version) {
        var codigo = errores.values().stream().max(java.util.Comparator.comparingLong(Incidencia::orden))
                .map(Incidencia::codigo).orElse(null);
        Estado estado;
        if (version == null) {
            estado = Estado.SIN_SNAPSHOT;
        } else if (codigo != null) {
            estado = Estado.DEGRADADO;
        } else if (ultimaRevision == null || !ultimaRevision.plus(vigencia).isAfter(Instant.now())) {
            estado = Estado.PENDIENTE;
        } else if (version < versionVerificada) {
            estado = Estado.DEGRADADO;
        } else {
            estado = kafkaAsignado && version.equals(versionVerificada) ? Estado.AL_DIA : Estado.PENDIENTE;
        }
        return new DiagnosticoCatalogo(version, estado, codigo);
    }

    private record Incidencia(long orden, Codigo codigo) {}
    private record Posicion(int particion, long offset) {}
}
