package catalogo.replica.application.exception;

public class ProfesionalDeshabilitadoException extends RuntimeException {
    public ProfesionalDeshabilitadoException() {
        super("El profesional está deshabilitado.");
    }
}
