package catalogo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
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
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogoConsultasIntegrationTest {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");
    private static final AtomicLong IDS = new AtomicLong(20000);
    private static final Instant ORIGEN = Instant.parse("2026-09-10T13:40:00Z");

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
    @Autowired private ApplicationContext context;

    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void limpiarCheckpoint() {
        jdbc.update("DELETE FROM catalog_state");
    }

    @Test
    void devuelveDetalleYAgendaSemanalLocalConVersionYOrdenIso() throws Exception {
        var profesional = profesional(true);
        var otro = profesional(true);
        var viernes = horario(profesional, DayOfWeek.FRIDAY, 9, 0, true);
        var lunesTarde = horario(profesional, DayOfWeek.MONDAY, 10, 30, true);
        var lunes = horario(profesional, DayOfWeek.MONDAY, 9, 0, true);
        var lunesMismaHora = horario(profesional, DayOfWeek.MONDAY, 9, 0, true);
        var martes = horario(profesional, DayOfWeek.TUESDAY, 8, 0, true);
        horario(profesional, DayOfWeek.MONDAY, 7, 0, false);
        horario(otro, DayOfWeek.MONDAY, 6, 0, true);
        inicializar(7L);

        var detalle = get(path(profesional), JwtTestKeys.token());
        assertThat(detalle.statusCode()).isEqualTo(200);
        var datos = json.readTree(detalle.body());
        assertThat(datos.get("id").asLong()).isEqualTo(profesional.getId());
        assertThat(datos.get("categoryId").asLong()).isEqualTo(profesional.getCategoryId());
        assertThat(datos.get("firstName").asString()).isEqualTo("Ana");
        assertThat(datos.get("lastName").asString()).isEqualTo("Gómez");
        assertThat(datos.get("enabled").asBoolean()).isTrue();

        var response = get(path(profesional) + "/schedule", JwtTestKeys.token());
        assertThat(response.statusCode()).isEqualTo(200);
        var body = json.readTree(response.body());
        assertThat(body.get("versionAplicada").asLong()).isEqualTo(7L);
        assertThat(body.get("professional").get("id").asLong()).isEqualTo(profesional.getId());
        var horarios = body.get("weeklySchedules");
        List<Long> ids = new ArrayList<>();
        horarios.forEach(horario -> ids.add(horario.get("id").asLong()));
        assertThat(ids).containsExactly(lunes.getId(), lunesMismaHora.getId(), lunesTarde.getId(), martes.getId(), viernes.getId());
        assertThat(horarios.get(0).get("dayOfWeek").asString()).isEqualTo("MONDAY");
        assertThat(LocalTime.parse(horarios.get(0).get("startTime").asString())).isEqualTo(LocalTime.of(9, 0));
        assertThat(LocalTime.parse(horarios.get(0).get("endTime").asString())).isEqualTo(LocalTime.of(12, 0));
        assertThat(horarios.get(0).get("slotDurationMinutes").asInt()).isEqualTo(30);
        assertThat(body.has("availableSlots")).isFalse();
        assertThat(context.getBeansOfType(JwtEncoder.class)).isEmpty();
    }

    @Test
    void sinVersionNoInventaUnCatalogoVacioNiUnaVersionCero() throws Exception {
        var profesional = profesional(true);
        for (String endpoint : List.of(path(profesional), path(profesional) + "/schedule",
                "/api/professionals/9223372036854775807")) {
            assertError(get(endpoint, JwtTestKeys.token()), 503, "CATALOG_NOT_READY");
        }
    }

    @Test
    void versionCeroInicializadaDistingueAusenciaYAgendaVacia() throws Exception {
        var profesional = profesional(true);
        horario(profesional, DayOfWeek.MONDAY, 9, 0, false);
        inicializar(0L);
        for (String suffix : List.of("", "/schedule")) {
            assertError(get("/api/professionals/9223372036854775807" + suffix, JwtTestKeys.token()),
                    404, "PROFESSIONAL_NOT_FOUND");
        }
        var response = get(path(profesional) + "/schedule", JwtTestKeys.token());
        assertThat(response.statusCode()).isEqualTo(200);
        var body = json.readTree(response.body());
        assertThat(body.get("versionAplicada").asLong()).isZero();
        assertThat(body.get("weeklySchedules").size()).isZero();
    }

    @Test
    void informaDeshabilitacionSinConfundirlaConInexistencia() throws Exception {
        var profesional = profesional(false);
        inicializar(1L);
        var response = get(path(profesional), JwtTestKeys.token());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).get("enabled").asBoolean()).isFalse();
        assertError(get(path(profesional) + "/schedule", JwtTestKeys.token()), 409, "PROFESSIONAL_DISABLED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void rechazaIdsInvalidos(String id) throws Exception {
        for (String suffix : List.of("", "/schedule")) {
            assertError(get("/api/professionals/" + id + suffix, JwtTestKeys.token()), 400, "INVALID_REQUEST");
        }
    }

    @Test
    void noHabilitaEscriturasNiListadosTodavia() throws Exception {
        var profesional = profesional(true);
        inicializar(1L);
        String token = JwtTestKeys.token();
        var post = HttpRequest.newBuilder(uri(path(profesional))).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}"));
        assertThat(client.send(post.build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        assertThat(get("/api/professionals", token).statusCode()).isEqualTo(403);
        assertThat(get("/actuator/env", token).statusCode()).isEqualTo(403);
        assertThat(get("/api/authenticate", token).statusCode()).isEqualTo(403);
        assertThat(get("/api/student/integration", token).statusCode()).isEqualTo(403);
    }

    @Test
    void requiereBearerAntesDeConsultarLaBase() throws Exception {
        for (String header : List.of("", "Bearer no-es-un-jwt", "Basic ZGVtbzpjbGF2ZQ==")) {
            var request = HttpRequest.newBuilder(uri("/api/professionals/1"));
            if (!header.isEmpty()) {
                request.header("Authorization", header);
            }
            assertThat(client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        }
    }

    @ParameterizedTest
    @MethodSource("tokensRechazados")
    void validaFirmaEmisorAudienciaVigenciaIdentidadYPermisos(String caso, int status) throws Exception {
        var profesional = profesional(true);
        inicializar(7L);
        var claims = JwtTestKeys.claims();
        Instant ahora = Instant.now();
        SignatureAlgorithm algoritmo = SignatureAlgorithm.RS256;
        switch (caso) {
            case "vencido" -> claims.issuedAt(ahora.minusSeconds(600)).expiresAt(ahora.minusSeconds(120));
            case "futuro" -> claims.notBefore(ahora.plusSeconds(300));
            case "iat-futuro" -> claims.issuedAt(ahora.plusSeconds(300));
            case "emisor" -> claims.issuer("https://otro-emisor.test");
            case "solo-reservas" -> claims.audience(List.of("turnos-reservas"));
            case "sin-audiencia" -> claims.claims(values -> values.remove("aud"));
            case "sin-exp" -> claims.claims(values -> values.remove("exp"));
            case "sin-iat" -> claims.claims(values -> values.remove("iat"));
            case "sin-sub" -> claims.claims(values -> values.remove("sub"));
            case "sub-invalido" -> claims.subject("usuario");
            case "sub-cero" -> claims.subject("0");
            case "sin-roles" -> claims.claims(values -> values.remove("roles"));
            case "otro-rol" -> claims.claim("roles", List.of("ROLE_STUDENT_CLIENT"));
            case "roles-invalidos" -> claims.claim("roles", List.of(123));
            case "algoritmo" -> algoritmo = SignatureAlgorithm.RS512;
            case "otra-clave" -> { }
            default -> throw new IllegalArgumentException(caso);
        }
        String token = "otra-clave".equals(caso) ? JwtTestKeys.firmaAjena() : JwtTestKeys.firmar(claims, algoritmo);
        assertThat(get(path(profesional), token).statusCode()).isEqualTo(status);
        assertThat(get(path(profesional) + "/schedule", token).statusCode()).isEqualTo(status);
    }

    static Stream<Arguments> tokensRechazados() {
        return Stream.of("vencido", "futuro", "iat-futuro", "emisor", "solo-reservas", "sin-audiencia", "sin-exp",
                        "sin-iat", "sin-sub", "sub-invalido", "sub-cero", "sin-roles", "otro-rol", "roles-invalidos",
                        "algoritmo", "otra-clave")
                .map(caso -> Arguments.of(caso, List.of("sin-roles", "otro-rol").contains(caso) ? 403 : 401));
    }

    @Test
    void aceptaMultiplesAudienciasSiCatalogoEstaIncluidoYRechazaManipulaciones() throws Exception {
        var profesional = profesional(true);
        inicializar(1L);
        String token = JwtTestKeys.firmar(JwtTestKeys.claims().audience(List.of("turnos-reservas", JwtTestKeys.AUDIENCE)),
                SignatureAlgorithm.RS256);
        assertThat(get(path(profesional), token).statusCode()).isEqualTo(200);
        String[] parts = token.split("\\.");
        String firmaAlterada = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        assertThat(get(path(profesional), parts[0] + "." + parts[1] + "." + firmaAlterada).statusCode()).isEqualTo(401);
    }

    private Profesional profesional(boolean enabled) {
        var categoria = catalogo.guardarCategoria(CategoriaProfesional.builder().id(IDS.incrementAndGet())
                .name("Clínica médica").enabled(true).createdAt(ORIGEN).updatedAt(ORIGEN).build());
        return catalogo.guardarProfesional(Profesional.builder().id(IDS.incrementAndGet()).categoryId(categoria.getId())
                .firstName("Ana").lastName("Gómez").enabled(enabled).createdAt(ORIGEN).updatedAt(ORIGEN).build());
    }

    private HorarioSemanal horario(Profesional profesional, DayOfWeek dia, int hora, int minuto, boolean enabled) {
        return catalogo.guardarHorario(HorarioSemanal.builder().id(IDS.incrementAndGet()).professionalId(profesional.getId())
                .dayOfWeek(dia).startTime(LocalTime.of(hora, minuto)).endTime(LocalTime.of(12, 0))
                .slotDurationMinutes(30).enabled(enabled).createdAt(ORIGEN).updatedAt(ORIGEN).build());
    }

    private void inicializar(Long version) {
        catalogo.guardarEstado(EstadoCatalogo.builder().versionAplicada(version).actualizadoEn(ORIGEN).build());
    }

    private String path(Profesional profesional) {
        return "/api/professionals/" + profesional.getId();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(json.readTree(response.body()).get("code").asString()).isEqualTo(code);
        assertThat(response.body()).doesNotContain("jdbc:", "password", "stackTrace");
    }
}
