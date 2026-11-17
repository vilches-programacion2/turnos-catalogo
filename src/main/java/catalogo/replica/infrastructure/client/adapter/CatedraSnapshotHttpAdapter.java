package catalogo.replica.infrastructure.client.adapter;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import catalogo.replica.application.exception.SnapshotCatalogoException;
import catalogo.replica.application.exception.SnapshotCatalogoException.Error;
import catalogo.replica.domain.model.SnapshotCatalogo;
import catalogo.replica.domain.ports.out.SnapshotCatalogoClient;
import catalogo.replica.infrastructure.client.dto.SnapshotCatalogoResponse;
import catalogo.replica.infrastructure.client.mapper.SnapshotCatalogoMapper;
import catalogo.shared.infrastructure.catedra.CatedraTokenFile;

@Component
@RequiredArgsConstructor
public class CatedraSnapshotHttpAdapter implements SnapshotCatalogoClient {
    private final RestClient catedraRestClient;
    private final CatedraTokenFile tokenFile;
    private final SnapshotCatalogoMapper mapper;

    @Override
    public SnapshotCatalogo descargar() {
        String token;
        try {
            token = tokenFile.leer();
        } catch (IOException e) {
            throw new SnapshotCatalogoException(Error.CONFIGURACION_INVALIDA);
        }
        try {
            return catedraRestClient.get().uri("/api/synchronization/snapshot")
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(token))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status != 200) {
                            Error error = switch (status) {
                                case 401 -> Error.NO_AUTENTICADO;
                                case 403 -> Error.ACCESO_DENEGADO;
                                case 429, 500, 502, 503, 504 -> Error.NO_DISPONIBLE;
                                default -> Error.ERROR_HTTP;
                            };
                            throw new SnapshotCatalogoException(error, status);
                        }
                        try {
                            return mapper.toDomain(response.bodyTo(SnapshotCatalogoResponse.class));
                        } catch (RestClientException e) {
                            throw new SnapshotCatalogoException(Error.RESPUESTA_INVALIDA);
                        }
                    });
        } catch (RestClientException e) {
            throw new SnapshotCatalogoException(Error.NO_DISPONIBLE);
        }
    }
}
