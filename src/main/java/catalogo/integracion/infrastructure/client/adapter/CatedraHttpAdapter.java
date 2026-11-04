package catalogo.integracion.infrastructure.client.adapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import catalogo.integracion.application.exception.IntegracionCatedraException;
import catalogo.integracion.application.exception.IntegracionCatedraException.Error;
import catalogo.integracion.domain.model.IntegracionCatedra;
import catalogo.integracion.domain.ports.out.CatedraClient;
import catalogo.integracion.infrastructure.client.dto.IntegracionCatedraResponse;
import catalogo.integracion.infrastructure.client.mapper.IntegracionCatedraMapper;
import catalogo.integracion.infrastructure.config.CatedraProperties;

@Component
@RequiredArgsConstructor
public class CatedraHttpAdapter implements CatedraClient {
    private static final int MAX_TOKEN_BYTES = 16384;

    private final RestClient catedraRestClient;
    private final CatedraProperties properties;
    private final IntegracionCatedraMapper mapper;

    @Override
    public IntegracionCatedra obtenerIntegracion() {
        String token = leerToken();
        try {
            return catedraRestClient.get().uri("/api/student/integration")
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(token))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status != 200) {
                            throw new IntegracionCatedraException(errorHttp(status), status);
                        }
                        try {
                            return mapper.toDomain(response.bodyTo(IntegracionCatedraResponse.class));
                        } catch (RestClientException e) {
                            throw new IntegracionCatedraException(Error.RESPUESTA_INVALIDA);
                        }
                    });
        } catch (RestClientException e) {
            throw new IntegracionCatedraException(Error.NO_DISPONIBLE);
        }
    }

    private String leerToken() {
        if (!properties.isEnabled() || properties.getTokenFile() == null
                || !Files.isRegularFile(properties.getTokenFile())) {
            throw new IntegracionCatedraException(Error.CONFIGURACION_INVALIDA);
        }
        try (var input = Files.newInputStream(properties.getTokenFile())) {
            byte[] bytes = input.readNBytes(MAX_TOKEN_BYTES + 1);
            String token = new String(bytes, StandardCharsets.UTF_8).strip();
            if (bytes.length > MAX_TOKEN_BYTES || !token.matches("[A-Za-z0-9._~+/=-]+")) {
                throw new IntegracionCatedraException(Error.CONFIGURACION_INVALIDA);
            }
            return token;
        } catch (IOException e) {
            throw new IntegracionCatedraException(Error.CONFIGURACION_INVALIDA);
        }
    }

    private Error errorHttp(int status) {
        return switch (status) {
            case 401 -> Error.NO_AUTENTICADO;
            case 403 -> Error.ACCESO_DENEGADO;
            case 404 -> Error.INTEGRACION_NO_ENCONTRADA;
            case 429, 500, 502, 503, 504 -> Error.NO_DISPONIBLE;
            default -> Error.ERROR_HTTP;
        };
    }
}
