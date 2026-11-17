package catalogo;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import catalogo.replica.domain.model.CategoriaProfesional;
import catalogo.replica.domain.model.EstadoCatalogo;
import catalogo.replica.domain.model.HorarioSemanal;
import catalogo.replica.domain.model.Profesional;
import catalogo.replica.domain.ports.out.CatalogoRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BusquedaCatalogoIntegrationTest {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");
    private static final Instant ORIGEN = Instant.parse("2026-09-10T13:40:00Z");
    private static final List<String> RUTAS = List.of("/api/professionals", "/api/professional-categories");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestKeys.configurar(registry);
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @LocalServerPort private int port;
    @Autowired private CatalogoRepository catalogo;
    @Autowired private JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private long siguienteId;

    @BeforeEach
    void prepararReplica() {
        jdbc.update("DELETE FROM weekly_schedule");
        jdbc.update("DELETE FROM professional");
        jdbc.update("DELETE FROM professional_category");
        jdbc.update("DELETE FROM catalog_state");
        siguienteId = 100;
        categoria(1, "Clínica", true);
        categoria(2, "Cardiología", false);
        profesional(10, 1, "Ana", "Gómez", true);
        profesional(11, 1, "Bruno", "Gómez", true);
        profesional(12, 2, "Ana", "Pérez", true);
        profesional(13, 1, "Ana", "Gómez", false);
        horario(10, DayOfWeek.MONDAY, "09:00", "12:00", 30, true);
        horario(10, DayOfWeek.MONDAY, "09:00", "10:00", 30, true);
        horario(11, DayOfWeek.MONDAY, "10:00", "11:00", 30, true);
        horario(12, DayOfWeek.MONDAY, "09:00", "12:00", 30, false);
        horario(13, DayOfWeek.MONDAY, "09:00", "12:00", 30, true);
        inicializar(7);
    }

    @Test
    void combinaTodosLosFiltrosSinDuplicarProfesionalesPorHorariosCoincidentes() throws Exception {
        var body = buscar("categoryId=1&name=aNA&enabled=true&dayOfWeek=MONDAY&time=09:30");
        assertThat(ids(body)).containsExactly(10L);
        assertThat(body.get("totalElements").asLong()).isEqualTo(1);
        assertThat(body.get("versionAplicada").asLong()).isEqualTo(7);
        assertThat(body.get("content").get(0).get("categoryId").asLong()).isEqualTo(1);
        assertThat(body.toString()).doesNotContain("availableSlots", "password", "token");
    }

    @Test
    void sinFiltrosIncluyeDeshabilitadosYSinAgendaConOrdenYPaginacionEstables() throws Exception {
        assertThat(ids(buscar(""))).containsExactly(10L, 13L, 11L, 12L);
        var first = buscar("page=0&size=2");
        var second = buscar("page=1&size=2");
        assertThat(ids(first)).containsExactly(10L, 13L);
        assertThat(ids(second)).containsExactly(11L, 12L);
        assertThat(first.get("totalElements").asLong()).isEqualTo(4);
        assertThat(second.get("page").asInt()).isEqualTo(1);
        assertThat(second.get("size").asInt()).isEqualTo(2);
        var beyond = buscar("page=9&size=2");
        assertThat(ids(beyond)).isEmpty();
        assertThat(beyond.get("totalElements").asLong()).isEqualTo(4);
    }

    @ParameterizedTest
    @CsvSource({"09:00,1", "09:30,1", "10:00,2", "11:30,1", "08:30,0", "11:50,0", "12:00,0", "09:30:01,0"})
    void soloOfreceIniciosAlineadosDeTurnosCompletos(String time, int total) throws Exception {
        assertThat(buscar("dayOfWeek=MONDAY&time=" + time).get("totalElements").asInt()).isEqualTo(total);
    }

    @Test
    void diaSinHoraExigeHorarioHabilitadoConDuracionSuficienteYProfesionalHabilitado() throws Exception {
        horario(12, DayOfWeek.TUESDAY, "09:00", "09:20", 30, true);
        assertThat(ids(buscar("dayOfWeek=MONDAY"))).containsExactly(10L, 11L);
        assertThat(ids(buscar("dayOfWeek=TUESDAY"))).isEmpty();
        assertThat(ids(buscar("enabled=false"))).containsExactly(13L);
        assertThat(ids(buscar("enabled=false&dayOfWeek=MONDAY"))).isEmpty();
        assertThat(ids(buscar("enabled=true"))).containsExactly(10L, 11L, 12L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"  ANA GÓMEZ  ", "gÓmEz", "Ana", "%", "_", "!", "' OR 1=1 --"})
    void nombreParcialCompletoYLiteralesSinConvertirTextoEnSQL(String name) throws Exception {
        profesional(20, 1, "Literal%_!", "Especial", true);
        var body = buscar("name=" + URLEncoder.encode(name, StandardCharsets.UTF_8));
        if (List.of("%", "_", "!").contains(name)) {
            assertThat(ids(body)).containsExactly(20L);
        } else if (name.contains("OR 1=1")) {
            assertThat(ids(body)).isEmpty();
        } else if (name.strip().equalsIgnoreCase("ANA GÓMEZ")) {
            assertThat(ids(body)).containsExactly(10L, 13L);
        } else {
            assertThat(body.get("totalElements").asInt()).isEqualTo(3);
        }
    }

    @Test
    void referenciaInexistenteEsDistintaDeBusquedaSinResultados() throws Exception {
        error(get("/api/professionals?categoryId=99", JwtTestKeys.token()), 400, "INVALID_CATEGORY");
        categoria(3, "Sin profesionales", true);
        assertThat(ids(buscar("categoryId=3"))).isEmpty();
        assertThat(ids(buscar("categoryId=2"))).containsExactly(12L);
    }

    @Test
    void listaCategoriasIncluyeSusIdsYEstadoSinExponerCamposInternos() throws Exception {
        var response = get("/api/professional-categories", JwtTestKeys.token());
        assertThat(response.statusCode()).isEqualTo(200);
        var body = json.readTree(response.body());
        assertThat(body.get("versionAplicada").asLong()).isEqualTo(7);
        assertThat(ids(body)).containsExactly(2L, 1L);
        assertThat(body.get("content").get(0).get("enabled").asBoolean()).isFalse();
        assertThat(body.get("content").get(0).properties()).hasSize(3);
    }

    @Test
    void replicaSinCheckpointResponde503YReplicaVaciaVersionCeroEsValida() throws Exception {
        jdbc.update("DELETE FROM catalog_state");
        for (var path : RUTAS) error(get(path, JwtTestKeys.token()), 503, "CATALOG_NOT_READY");
        error(get("/api/professionals?categoryId=99", JwtTestKeys.token()), 503, "CATALOG_NOT_READY");
        jdbc.update("DELETE FROM weekly_schedule");
        jdbc.update("DELETE FROM professional");
        jdbc.update("DELETE FROM professional_category");
        inicializar(0);
        for (var path : RUTAS) {
            var response = get(path, JwtTestKeys.token());
            assertThat(response.statusCode()).isEqualTo(200);
            var body = json.readTree(response.body());
            assertThat(body.get("versionAplicada").asLong()).isZero();
            assertThat(ids(body)).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"categoryId=0", "categoryId=-1", "categoryId=abc", "categoryId=9223372036854775808",
            "page=-1", "page=abc", "size=0", "size=101", "page=2147483647&size=100",
            "enabled=quizas", "dayOfWeek=LUNES", "time=09:00", "dayOfWeek=MONDAY&time=25:00"})
    void parametrosInvalidosTienenErrorSeguro(String query) throws Exception {
        error(get("/api/professionals?" + query, JwtTestKeys.token()), 400, "INVALID_REQUEST");
    }

    @Test
    void limitaLongitudDelNombre() throws Exception {
        error(get("/api/professionals?name=" + "a".repeat(101), JwtTestKeys.token()), 400, "INVALID_REQUEST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ausente", "invalido", "vencido", "solo-reservas", "otro-rol"})
    void protegeAmbasRutasConElMismoJwtDeUsuario(String caso) throws Exception {
        String token = switch (caso) {
            case "ausente" -> null;
            case "invalido" -> "no-es-un-jwt";
            case "vencido" -> JwtTestKeys.firmar(JwtTestKeys.claims().issuedAt(Instant.now().minusSeconds(500))
                    .expiresAt(Instant.now().minusSeconds(200)), SignatureAlgorithm.RS256);
            case "solo-reservas" -> JwtTestKeys.firmar(JwtTestKeys.claims().audience(List.of("turnos-reservas")), SignatureAlgorithm.RS256);
            default -> JwtTestKeys.firmar(JwtTestKeys.claims().claim("roles", List.of("ROLE_STUDENT_CLIENT")), SignatureAlgorithm.RS256);
        };
        for (var path : RUTAS) assertThat(get(path, token).statusCode()).isEqualTo(caso.equals("otro-rol") ? 403 : 401);
    }

    @Test
    void falloDeBaseNoSePresentaComoListaVaciaNiExponeSQL() throws Exception {
        jdbc.execute("ALTER TABLE professional RENAME TO professional_no_disponible");
        try {
            error(get("/api/professionals", JwtTestKeys.token()), 503, "CATALOG_UNAVAILABLE");
        } finally {
            jdbc.execute("ALTER TABLE professional_no_disponible RENAME TO professional");
        }
    }

    private void categoria(long id, String name, boolean enabled) {
        catalogo.guardarCategoria(CategoriaProfesional.builder().id(id).name(name).enabled(enabled)
                .createdAt(ORIGEN).updatedAt(ORIGEN).build());
    }

    private void profesional(long id, long category, String first, String last, boolean enabled) {
        catalogo.guardarProfesional(Profesional.builder().id(id).categoryId(category).firstName(first).lastName(last)
                .enabled(enabled).createdAt(ORIGEN).updatedAt(ORIGEN).build());
    }

    private void horario(long professional, DayOfWeek day, String start, String end, int minutes, boolean enabled) {
        catalogo.guardarHorario(HorarioSemanal.builder().id(siguienteId++).professionalId(professional).dayOfWeek(day)
                .startTime(LocalTime.parse(start)).endTime(LocalTime.parse(end)).slotDurationMinutes(minutes)
                .enabled(enabled).createdAt(ORIGEN).updatedAt(ORIGEN).build());
    }

    private void inicializar(long version) {
        catalogo.guardarEstado(EstadoCatalogo.builder().versionAplicada(version).actualizadoEn(ORIGEN).build());
    }

    private JsonNode buscar(String query) throws Exception {
        var response = get("/api/professionals?" + query, JwtTestKeys.token());
        assertThat(response.statusCode()).withFailMessage("Respuesta de búsqueda: %s", response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(10));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private List<Long> ids(JsonNode body) {
        var ids = new ArrayList<Long>();
        body.get("content").forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }

    private void error(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(json.readTree(response.body()).get("code").asString()).isEqualTo(code);
        assertThat(response.body()).doesNotContain("jdbc:", "stackTrace", "SELECT", "password");
    }
}
