package catalogo.replica.application.exception;

public class CatalogoNoInicializadoException extends RuntimeException {
    public CatalogoNoInicializadoException() {
        super("El catálogo todavía no tiene una versión aplicada.");
    }
}
