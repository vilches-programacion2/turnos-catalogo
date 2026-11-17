package catalogo.replica.application.exception;

public class FiltroInvalidoException extends RuntimeException {
    public FiltroInvalidoException() {
        super("Los filtros o la paginación no son válidos; la hora requiere un día semanal.");
    }
}
