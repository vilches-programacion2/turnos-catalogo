package catalogo.replica.application.exception;

public class ProfesionalNoEncontradoException extends RuntimeException {
    public ProfesionalNoEncontradoException() {
        super("Profesional no encontrado.");
    }
}
