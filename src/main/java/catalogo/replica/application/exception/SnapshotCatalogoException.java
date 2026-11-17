package catalogo.replica.application.exception;

import lombok.Getter;

@Getter
public class SnapshotCatalogoException extends RuntimeException {
    public enum Error {
        CONFIGURACION_INVALIDA, NO_AUTENTICADO, ACCESO_DENEGADO,
        NO_DISPONIBLE, RESPUESTA_INVALIDA, ERROR_HTTP, VERSION_OBSOLETA
    }

    private final Error error;
    private final Integer httpStatus;

    public SnapshotCatalogoException(Error error) {
        this(error, null);
    }

    public SnapshotCatalogoException(Error error, Integer httpStatus) {
        super("No se pudo aplicar el snapshot de catálogo: " + error);
        this.error = error;
        this.httpStatus = httpStatus;
    }
}
