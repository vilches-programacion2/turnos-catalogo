package catalogo.replica.domain.ports.out;

import catalogo.replica.domain.model.SnapshotCatalogo;

public interface SnapshotCatalogoClient {
    SnapshotCatalogo descargar();
}
