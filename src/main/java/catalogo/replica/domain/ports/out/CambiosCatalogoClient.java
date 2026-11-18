package catalogo.replica.domain.ports.out;

import catalogo.replica.domain.model.CambioCatalogo;
import catalogo.replica.domain.model.MetadataCatalogo;

public interface CambiosCatalogoClient {
    MetadataCatalogo obtenerMetadata();
    CambioCatalogo obtenerCambio(long version);
}
