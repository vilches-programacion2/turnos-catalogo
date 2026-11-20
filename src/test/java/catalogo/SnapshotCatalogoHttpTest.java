package catalogo;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import catalogo.replica.application.exception.SnapshotCatalogoException;
import catalogo.replica.application.exception.SnapshotCatalogoException.Error;
import catalogo.replica.application.validation.SnapshotCatalogoValidator;
import catalogo.replica.infrastructure.client.adapter.CatedraSnapshotHttpAdapter;
import catalogo.replica.infrastructure.client.mapper.SnapshotCatalogoMapper;
import catalogo.shared.infrastructure.catedra.CatedraProperties;
import catalogo.shared.infrastructure.catedra.CatedraTokenFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class SnapshotCatalogoHttpTest {
    private static final String TOKEN = "token-tecnico-ficticio-snapshot";
    @TempDir Path temp;
    private MockRestServiceServer server;
    private CatedraSnapshotHttpAdapter adapter;
    private final SnapshotCatalogoValidator validator = new SnapshotCatalogoValidator();

    @BeforeEach
    void preparar() throws IOException {
        var properties = new CatedraProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(URI.create("https://catedra.test"));
        properties.setTokenFile(temp.resolve("catedra.jwt"));
        Files.writeString(properties.getTokenFile(), TOKEN);
        var builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new CatedraSnapshotHttpAdapter(builder.build(), new CatedraTokenFile(properties), new SnapshotCatalogoMapper());
    }

    @Test
    void descargaLasTresColeccionesConElJwtTecnicoYConservaDatosYVersion(CapturedOutput output) {
        esperarGet().andRespond(withSuccess(SnapshotTestData.JSON, MediaType.APPLICATION_JSON));
        var snapshot = adapter.descargar();
        validator.validar(snapshot);
        assertThat(snapshot.getSnapshotVersion()).isEqualTo(7L);
        assertThat(snapshot.getGeneratedAt()).isEqualTo(SnapshotTestData.FECHA);
        assertThat(snapshot.getProfessionalCategories()).singleElement().satisfies(categoria -> {
            assertThat(categoria.getId()).isEqualTo(1L);
            assertThat(categoria.getDescription()).isNull();
        });
        assertThat(snapshot.getProfessionals()).singleElement().satisfies(profesional -> {
            assertThat(profesional.getCategoryId()).isEqualTo(1L);
            assertThat(profesional.getLastName()).isEqualTo("Gómez");
        });
        assertThat(snapshot.getWeeklySchedules()).singleElement().satisfies(horario -> {
            assertThat(horario.getProfessionalId()).isEqualTo(15L);
            assertThat(horario.getStartTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(horario.getEndTime()).isEqualTo(LocalTime.of(12, 0));
        });
        assertThat(output).doesNotContain(TOKEN);
        server.verify();
    }

    @Test
    void conservaEntidadesDeshabilitadasDelSnapshot() {
        esperarGet().andRespond(withSuccess(SnapshotTestData.JSON.replace("true", "false"), MediaType.APPLICATION_JSON));
        var snapshot = adapter.descargar();
        validator.validar(snapshot);
        assertThat(snapshot.getProfessionalCategories().getFirst().getEnabled()).isFalse();
        assertThat(snapshot.getProfessionals().getFirst().getEnabled()).isFalse();
        assertThat(snapshot.getWeeklySchedules().getFirst().getEnabled()).isFalse();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{", "{\"snapshotVersion\":7}",
            "{\"snapshotVersion\":7,\"generatedAt\":\"2026-09-10T13:40:00Z\",\"professionalCategories\":[],\"professionals\":[]}"})
    void distingueRespuestaAusenteDeColeccionesVacias(String body) {
        esperarGet().andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertError(() -> validator.validar(adapter.descargar()), Error.RESPUESTA_INVALIDA);
        server.verify();
    }

    @Test
    void admiteColeccionesVaciasYVersionCero() {
        esperarGet().andRespond(withSuccess("""
                {"snapshotVersion":0,"generatedAt":"2026-09-10T13:40:00Z",
                 "professionalCategories":[],"professionals":[],"weeklySchedules":[]}
                """, MediaType.APPLICATION_JSON));
        var snapshot = adapter.descargar();
        validator.validar(snapshot);
        assertThat(snapshot.getSnapshotVersion()).isZero();
        assertThat(snapshot.getProfessionals()).isEmpty();
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("erroresHttp")
    void rechazaErroresSinFiltrarCuerposNiReintentar(int status, Error error, CapturedOutput output) {
        esperarGet().andRespond(withStatus(HttpStatusCode.valueOf(status))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body("{\"detail\":\"" + TOKEN + "\"}"));
        assertThatThrownBy(adapter::descargar).isInstanceOfSatisfying(SnapshotCatalogoException.class, e -> {
            assertThat(e.getError()).isEqualTo(error);
            assertThat(e.getHttpStatus()).isEqualTo(status);
            assertThat(e.getCause()).isNull();
            assertThat(e.getMessage()).doesNotContain(TOKEN);
        });
        assertThat(output).doesNotContain(TOKEN);
        server.verify();
    }

    static Stream<Arguments> erroresHttp() {
        return Stream.of(Arguments.of(401, Error.NO_AUTENTICADO), Arguments.of(403, Error.ACCESO_DENEGADO),
                Arguments.of(503, Error.NO_DISPONIBLE), Arguments.of(429, Error.NO_DISPONIBLE),
                Arguments.of(404, Error.ERROR_HTTP));
    }

    @Test
    void tolera401SinCuerpo() {
        esperarGet().andRespond(withStatus(HttpStatusCode.valueOf(401)));
        assertError(adapter::descargar, Error.NO_AUTENTICADO);
        server.verify();
    }

    @Test
    void sanitizaLaDesconexion() {
        esperarGet().andRespond(withException(new IOException("fallo " + TOKEN)));
        assertThatThrownBy(adapter::descargar).isInstanceOfSatisfying(SnapshotCatalogoException.class, e -> {
            assertThat(e.getError()).isEqualTo(Error.NO_DISPONIBLE);
            assertThat(e.getCause()).isNull();
            assertThat(e.getMessage()).doesNotContain(TOKEN);
        });
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"referencia", "duplicado", "habilitado", "fecha", "duracion", "version", "elemento-null"})
    void validaElGrafoCompletoAntesDePersistir(String caso) {
        var snapshot = SnapshotTestData.snapshot(7, 0);
        switch (caso) {
            case "referencia" -> snapshot.getProfessionals().getFirst().setCategoryId(999L);
            case "duplicado" -> snapshot.setProfessionals(List.of(snapshot.getProfessionals().getFirst(), snapshot.getProfessionals().getFirst()));
            case "habilitado" -> snapshot.getWeeklySchedules().getFirst().setEnabled(null);
            case "fecha" -> snapshot.getProfessionalCategories().getFirst().setCreatedAt(null);
            case "duracion" -> snapshot.getWeeklySchedules().getFirst().setSlotDurationMinutes(0);
            case "version" -> snapshot.setSnapshotVersion(-1L);
            default -> snapshot.setWeeklySchedules(java.util.Arrays.asList((catalogo.replica.domain.model.HorarioSemanal) null));
        }
        assertError(() -> validator.validar(snapshot), Error.RESPUESTA_INVALIDA);
    }

    private org.springframework.test.web.client.ResponseActions esperarGet() {
        return server.expect(requestTo("https://catedra.test/api/synchronization/snapshot"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer " + TOKEN));
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, Error error) {
        assertThatThrownBy(action).isInstanceOfSatisfying(SnapshotCatalogoException.class,
                e -> assertThat(e.getError()).isEqualTo(error));
    }
}
