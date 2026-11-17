package catalogo.replica.application.exception;

public class CategoriaInvalidaException extends RuntimeException {
    public CategoriaInvalidaException() {
        super("La categoría indicada no existe en el catálogo local.");
    }
}
