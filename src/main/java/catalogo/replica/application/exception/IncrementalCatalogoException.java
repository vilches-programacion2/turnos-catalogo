package catalogo.replica.application.exception;

import lombok.Getter;

@Getter
public class IncrementalCatalogoException extends RuntimeException {
    public enum Error {
        RESPUESTA_INVALIDA, EVENTO_INVALIDO, EVENTO_CONFLICTIVO, HISTORIAL_NO_DISPONIBLE,
        VERSION_NO_PUBLICADA, VERSION_CONFLICTIVA, REDIS_NO_DISPONIBLE, CONFIGURACION_INVALIDA,
        PERSISTENCIA_NO_DISPONIBLE
    }
    private final Error error;

    public IncrementalCatalogoException(Error error) {
        super("Sincronización incremental fallida: " + error);
        this.error = error;
    }
}
