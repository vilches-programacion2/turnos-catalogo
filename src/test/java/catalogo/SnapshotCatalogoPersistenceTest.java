package catalogo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import catalogo.replica.application.exception.SnapshotCatalogoException;
import catalogo.replica.application.exception.SnapshotCatalogoException.Error;
import catalogo.replica.application.service.CatalogoService;
import catalogo.replica.domain.model.SnapshotCatalogo;
import catalogo.replica.domain.ports.out.CatalogoRepository;
import catalogo.replica.domain.ports.out.SnapshotCatalogoClient;
import catalogo.replica.infrastructure.config.SnapshotCatalogoInitializer;
import catalogo.replica.application.service.MonitorSincronizacionCatalogo;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SnapshotCatalogoPersistenceTest {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestKeys.configurar(registry);
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired private CatalogoService service;
    @Autowired private CatalogoRepository catalogo;
    @Autowired private DescargaControlada descarga;
    @Autowired private JdbcTemplate jdbc;
    @LocalServerPort private int port;

    @BeforeEach
    void limpiar() {
        for (String tabla : List.of("weekly_schedule", "professional", "professional_category", "catalog_state")) {
            jdbc.update("DELETE FROM " + tabla);
        }
        descarga.llamadas.set(0);
        descarga.respuesta = () -> SnapshotTestData.snapshot(7, 0);
    }

    @Test
    void inicializaLaBaseVaciaYLasConsultasPublicanLaVersionCorrespondiente(CapturedOutput output) throws Exception {
        assertThat(catalogo.obtenerEstado()).isEmpty();
        var estado = service.inicializar();
        assertThat(estado.getVersionAplicada()).isEqualTo(7L);
        assertThat(descarga.llamadas).hasValue(1);
        verificarContenido(7, 0);
        assertThat(catalogo.buscarProfesional(15L).orElseThrow().getUpdatedAt()).isEqualTo(SnapshotTestData.FECHA);
        assertThat(catalogo.buscarCategoria(1L).orElseThrow().getDescription()).isNull();
        assertThat(output).contains("Snapshot aplicado: version=7 categorias=1 profesionales=1 horarios=1");

        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/professionals/15/schedule"))
                .header("Authorization", "Bearer " + JwtTestKeys.token()).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var body = JsonMapper.builder().build().readTree(response.body());
        assertThat(body.path("versionAplicada").asLong()).isEqualTo(7L);
        assertThat(body.path("professional").path("id").asLong()).isEqualTo(15L);
        assertThat(body.path("weeklySchedules").size()).isEqualTo(1);
    }

    @Test
    void reiniciarConCheckpointNoVuelveADescargarNiReemplazar() {
        var inicial = service.inicializar();
        descarga.respuesta = () -> { throw new AssertionError("No debe consultar la red con checkpoint existente"); };
        assertThat(service.inicializar()).usingRecursiveComparison().isEqualTo(inicial);
        assertThat(descarga.llamadas).hasValue(1);
        verificarContenido(7, 0);
    }

    @Test
    void reaplicarLaMismaVersionNoDuplicaDatosYPuedeRepararLaCopia() {
        service.sincronizar();
        jdbc.update("UPDATE professional SET last_name = 'dato local incorrecto' WHERE id = 15");
        service.sincronizar();
        verificarContenido(7, 0);
        assertThat(catalogo.buscarProfesional(15L).orElseThrow().getLastName()).isEqualTo("Gómez");
        assertThat(descarga.llamadas).hasValue(2);
    }

    @Test
    void reemplazaTodasLasColeccionesYRetiraLosIdsAusentes() {
        service.sincronizar();
        descarga.respuesta = () -> SnapshotTestData.snapshot(8, 100);
        service.sincronizar();
        verificarContenido(8, 100);
        assertThat(catalogo.buscarCategoria(1L)).isEmpty();
        assertThat(catalogo.buscarProfesional(15L)).isEmpty();
        assertThat(catalogo.buscarHorario(21L)).isEmpty();
    }

    @Test
    void persisteLasBajasLogicasQueElServidorIncluye() {
        var snapshot = SnapshotTestData.snapshot(7, 0);
        snapshot.getProfessionalCategories().getFirst().setEnabled(false);
        snapshot.getProfessionals().getFirst().setEnabled(false);
        snapshot.getWeeklySchedules().getFirst().setEnabled(false);
        descarga.respuesta = () -> snapshot;
        service.sincronizar();
        verificarContenido(7, 0);
        assertThat(catalogo.buscarCategoria(1L).orElseThrow().getEnabled()).isFalse();
        assertThat(catalogo.buscarProfesional(15L).orElseThrow().getEnabled()).isFalse();
        assertThat(catalogo.buscarHorario(21L).orElseThrow().getEnabled()).isFalse();
    }

    @Test
    void snapshotVacioConVersionCeroEsUnaInicializacionValida() {
        descarga.respuesta = () -> SnapshotTestData.vacio(0);
        assertThat(service.inicializar().getVersionAplicada()).isZero();
        assertThat(service.inicializar().getVersionAplicada()).isZero();
        assertThat(descarga.llamadas).hasValue(1);
        for (String tabla : List.of("professional_category", "professional", "weekly_schedule")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class)).isZero();
        }
    }

    @Test
    void falloDeDescargaOValidacionConservaElEstadoAnterior() {
        service.sincronizar();
        descarga.respuesta = () -> { throw new SnapshotCatalogoException(Error.NO_DISPONIBLE); };
        assertThatThrownBy(service::sincronizar).isInstanceOf(SnapshotCatalogoException.class);
        verificarContenido(7, 0);
        var invalido = SnapshotTestData.snapshot(8, 100);
        invalido.getProfessionals().getFirst().setCategoryId(999L);
        descarga.respuesta = () -> invalido;
        assertThatThrownBy(service::sincronizar).isInstanceOfSatisfying(SnapshotCatalogoException.class,
                e -> assertThat(e.getError()).isEqualTo(Error.RESPUESTA_INVALIDA));
        verificarContenido(7, 0);
    }

    @Test
    void falloDuranteLaPersistenciaRevierteBorradoInsercionesYCheckpoint() {
        service.sincronizar();
        var invalido = SnapshotTestData.snapshot(8, 100);
        invalido.getProfessionals().getFirst().setCategoryId(999L);
        // Se fuerza un error de FK en el adaptador para verificar rollback después del borrado.
        assertThatThrownBy(() -> catalogo.reemplazarSnapshot(invalido)).isInstanceOf(DataIntegrityViolationException.class);
        verificarContenido(7, 0);
        assertThat(catalogo.buscarCategoria(101L)).isEmpty();
        assertThat(catalogo.buscarProfesional(115L)).isEmpty();
    }

    @Test
    void snapshotAnteriorNoHaceRetrocederLaVersion() {
        service.sincronizar();
        descarga.respuesta = () -> SnapshotTestData.snapshot(6, 100);
        assertThatThrownBy(service::sincronizar).isInstanceOfSatisfying(SnapshotCatalogoException.class,
                e -> assertThat(e.getError()).isEqualTo(Error.VERSION_OBSOLETA));
        verificarContenido(7, 0);
    }

    @Test
    void dosReemplazosConcurrentesNoMezclanDatosNiRetrocedenVersion() throws Exception {
        var listos = new CountDownLatch(2);
        var empezar = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var anterior = executor.submit(() -> {
                listos.countDown();
                empezar.await();
                return catalogo.reemplazarSnapshot(SnapshotTestData.snapshot(8, 100));
            });
            var posterior = executor.submit(() -> {
                listos.countDown();
                empezar.await();
                return catalogo.reemplazarSnapshot(SnapshotTestData.snapshot(9, 200));
            });
            boolean preparados = listos.await(5, TimeUnit.SECONDS);
            empezar.countDown();
            assertThat(preparados).isTrue();
            assertThat(posterior.get(10, TimeUnit.SECONDS).getVersionAplicada()).isEqualTo(9L);
            try {
                anterior.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOfSatisfying(SnapshotCatalogoException.class,
                        error -> assertThat(error.getError()).isEqualTo(Error.VERSION_OBSOLETA));
            }
        } finally {
            empezar.countDown();
        }
        verificarContenido(9, 200);
    }

    @Test
    void falloInicialDejaServicioSinCheckpointYPermiteReintentarAlReiniciar(CapturedOutput output) {
        descarga.respuesta = () -> { throw new SnapshotCatalogoException(Error.NO_AUTENTICADO, 401); };
        var inicializador = new SnapshotCatalogoInitializer(service, new MonitorSincronizacionCatalogo("60s"));
        assertThatCode(() -> inicializador.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
        assertThat(catalogo.obtenerEstado()).isEmpty();
        assertThat(output).contains("Inicialización de catálogo pendiente: error=NO_AUTENTICADO httpStatus=401");
        descarga.respuesta = () -> SnapshotTestData.snapshot(7, 0);
        inicializador.run(new DefaultApplicationArguments());
        verificarContenido(7, 0);
    }

    private void verificarContenido(long version, long offset) {
        assertThat(catalogo.obtenerEstado().orElseThrow().getVersionAplicada()).isEqualTo(version);
        assertThat(catalogo.buscarCategoria(1 + offset)).isPresent();
        assertThat(catalogo.buscarProfesional(15 + offset)).isPresent();
        assertThat(catalogo.buscarHorario(21 + offset)).isPresent();
        for (String tabla : List.of("professional_category", "professional", "weekly_schedule")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class)).isEqualTo(1L);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuracion {
        @Bean
        @Primary
        DescargaControlada descargaControlada() {
            return new DescargaControlada();
        }
    }

    static class DescargaControlada implements SnapshotCatalogoClient {
        final AtomicInteger llamadas = new AtomicInteger();
        Supplier<SnapshotCatalogo> respuesta;

        @Override
        public SnapshotCatalogo descargar() {
            llamadas.incrementAndGet();
            return respuesta.get();
        }
    }
}
