package catalogo;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicLong;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import catalogo.replica.domain.model.CategoriaProfesional;
import catalogo.replica.domain.model.EstadoCatalogo;
import catalogo.replica.domain.model.HorarioSemanal;
import catalogo.replica.domain.model.Profesional;
import catalogo.replica.domain.ports.out.CatalogoRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class PersistenceIntegrationTest {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");
    private static final AtomicLong IDS = new AtomicLong(10000);
    private static final Instant FECHA_ORIGEN = Instant.parse("2026-09-10T13:40:00Z");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired private CatalogoRepository catalogo;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Flyway flyway;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void limpiarCheckpoint() {
        jdbc.update("DELETE FROM catalog_state");
    }

    @Test
    void migraSoloTablasPropiasYSinInventarUnaVersionInicial() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(flyway.info().applied()).hasSize(1);
        assertThat(jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class))
                .containsExactlyInAnyOrder("professional_category", "professional", "weekly_schedule",
                        "catalog_state", "flyway_schema_history");
        assertThat(catalogo.obtenerEstado()).isEmpty();
        assertThat(catalogo.buscarCategoria(Long.MAX_VALUE)).isEmpty();
        assertThat(catalogo.buscarProfesional(Long.MAX_VALUE)).isEmpty();
        assertThat(catalogo.buscarHorario(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void conservaIdsExternosYTodosLosDatosDeOrigen() {
        var categoria = categoria();
        categoria.setName("Categoría de prueba ".repeat(30));
        categoria.setDescription(null);
        var profesional = profesional(categoria.getId());
        var horario = horario(profesional.getId());
        catalogo.guardarCategoria(categoria);
        catalogo.guardarProfesional(profesional);
        catalogo.guardarHorario(horario);

        assertThat(catalogo.buscarCategoria(categoria.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(categoria);
        assertThat(catalogo.buscarProfesional(profesional.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(profesional);
        assertThat(catalogo.buscarHorario(horario.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(horario);
    }

    @Test
    void actualizaPorIdYConservaLasBajasLogicasSinDuplicarFilas() {
        var categoria = catalogo.guardarCategoria(categoria());
        var profesional = catalogo.guardarProfesional(profesional(categoria.getId()));
        var horario = catalogo.guardarHorario(horario(profesional.getId()));
        categoria.setEnabled(false);
        categoria.setName("Nombre actualizado");
        profesional.setEnabled(false);
        profesional.setLastName("Apellido actualizado");
        horario.setEnabled(false);
        horario.setStartTime(LocalTime.of(10, 0));

        catalogo.guardarCategoria(categoria);
        catalogo.guardarProfesional(profesional);
        catalogo.guardarHorario(horario);

        assertThat(catalogo.buscarCategoria(categoria.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(categoria);
        assertThat(catalogo.buscarProfesional(profesional.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(profesional);
        assertThat(catalogo.buscarHorario(horario.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(horario);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM professional_category WHERE id = ?", Long.class,
                categoria.getId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM professional WHERE id = ?", Long.class,
                profesional.getId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM weekly_schedule WHERE id = ?", Long.class,
                horario.getId())).isEqualTo(1L);
    }

    @Test
    void rechazaReferenciasAEntidadesInexistentes() {
        assertThatThrownBy(() -> catalogo.guardarProfesional(profesional(Long.MAX_VALUE)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> catalogo.guardarHorario(horario(Long.MAX_VALUE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void noBorraEnCascadaLosDatosReferenciados() {
        var categoria = catalogo.guardarCategoria(categoria());
        var profesional = catalogo.guardarProfesional(profesional(categoria.getId()));
        var horario = catalogo.guardarHorario(horario(profesional.getId()));
        assertThatThrownBy(() -> jdbc.update("DELETE FROM professional_category WHERE id = ?", categoria.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM professional WHERE id = ?", profesional.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(catalogo.buscarHorario(horario.getId())).isPresent();
    }

    @ParameterizedTest
    @EnumSource(DayOfWeek.class)
    void conservaTodosLosDiasYHorasLocalesSinImponerCruceDeMedianoche(DayOfWeek dia) {
        var categoria = catalogo.guardarCategoria(categoria());
        var profesional = catalogo.guardarProfesional(profesional(categoria.getId()));
        var horario = horario(profesional.getId());
        horario.setDayOfWeek(dia);
        horario.setStartTime(LocalTime.of(23, 30));
        horario.setEndTime(LocalTime.of(0, 30));
        catalogo.guardarHorario(horario);
        assertThat(catalogo.buscarHorario(horario.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(horario);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -30})
    void rechazaDuracionesNoPositivas(int duracion) {
        var categoria = catalogo.guardarCategoria(categoria());
        var profesional = catalogo.guardarProfesional(profesional(categoria.getId()));
        var horario = horario(profesional.getId());
        horario.setSlotDurationMinutes(duracion);
        assertThatThrownBy(() -> catalogo.guardarHorario(horario))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laBaseTambienRestringeElDiaDeLaSemana() {
        var categoria = catalogo.guardarCategoria(categoria());
        var profesional = catalogo.guardarProfesional(profesional(categoria.getId()));
        var horario = catalogo.guardarHorario(horario(profesional.getId()));
        assertThatThrownBy(() -> jdbc.update("UPDATE weekly_schedule SET day_of_week = ? WHERE id = ?",
                "INVALID", horario.getId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persisteUnSoloCheckpointYRechazaVersionesNegativas() {
        assertThat(catalogo.obtenerEstado()).isEmpty();
        catalogo.guardarEstado(estado(0L));
        assertThat(catalogo.obtenerEstado().orElseThrow().getVersionAplicada()).isZero();
        var nuevo = estado(7L);
        catalogo.guardarEstado(nuevo);
        assertThat(catalogo.obtenerEstado().orElseThrow()).usingRecursiveComparison().isEqualTo(nuevo);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog_state", Long.class)).isEqualTo(1L);
        assertThatThrownBy(() -> catalogo.guardarEstado(estado(-1L)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO catalog_state (id, applied_version, updated_at) VALUES (2, 8, CURRENT_TIMESTAMP)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(catalogo.obtenerEstado().orElseThrow().getVersionAplicada()).isEqualTo(7L);
    }

    @Test
    void losDatosYLaVersionPuedenParticiparDeUnaMismaTransaccion() {
        catalogo.guardarEstado(estado(3L));
        var categoria = categoria();
        var profesional = profesional(categoria.getId());
        var transaccion = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaccion.executeWithoutResult(status -> {
            catalogo.guardarCategoria(categoria);
            catalogo.guardarProfesional(profesional);
            catalogo.guardarEstado(estado(4L));
            // Error al final: ni los datos anteriores ni el checkpoint deben quedar aplicados.
            catalogo.guardarHorario(horario(Long.MAX_VALUE));
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(catalogo.buscarCategoria(categoria.getId())).isEmpty();
        assertThat(catalogo.buscarProfesional(profesional.getId())).isEmpty();
        assertThat(catalogo.obtenerEstado().orElseThrow().getVersionAplicada()).isEqualTo(3L);
    }

    @Test
    void conservaDatosYVersionAlReabrirLaAplicacionSobreLaMismaBase() {
        var categoria = catalogo.guardarCategoria(categoria());
        var profesional = catalogo.guardarProfesional(profesional(categoria.getId()));
        var horario = catalogo.guardarHorario(horario(profesional.getId()));
        var estado = catalogo.guardarEstado(estado(9L));

        try (var context = new SpringApplicationBuilder(TurnosCatalogoApplication.class)
                .initializers(application -> TestPropertyValues.of(
                        "server.port=0", "spring.datasource.url=" + DATABASE.getJdbcUrl(),
                        "spring.datasource.username=" + DATABASE.getUsername(),
                        "spring.datasource.password=" + DATABASE.getPassword()).applyTo(application))
                .run()) {
            var reabierto = context.getBean(CatalogoRepository.class);
            assertThat(reabierto.buscarCategoria(categoria.getId()).orElseThrow())
                    .usingRecursiveComparison().isEqualTo(categoria);
            assertThat(reabierto.buscarProfesional(profesional.getId()).orElseThrow())
                    .usingRecursiveComparison().isEqualTo(profesional);
            assertThat(reabierto.buscarHorario(horario.getId()).orElseThrow())
                    .usingRecursiveComparison().isEqualTo(horario);
            assertThat(reabierto.obtenerEstado().orElseThrow()).usingRecursiveComparison().isEqualTo(estado);
            assertThat(context.getBean(Flyway.class).info().applied()).hasSize(1);
        }
    }

    private CategoriaProfesional categoria() {
        return CategoriaProfesional.builder().id(IDS.incrementAndGet()).name("Clínica médica")
                .description("Categoría de prueba").enabled(true)
                .createdAt(FECHA_ORIGEN).updatedAt(FECHA_ORIGEN).build();
    }

    private Profesional profesional(Long categoryId) {
        return Profesional.builder().id(IDS.incrementAndGet()).categoryId(categoryId)
                .firstName("Ana").lastName("Gómez").enabled(true)
                .createdAt(FECHA_ORIGEN).updatedAt(FECHA_ORIGEN).build();
    }

    private HorarioSemanal horario(Long professionalId) {
        return HorarioSemanal.builder().id(IDS.incrementAndGet()).professionalId(professionalId)
                .dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0))
                .slotDurationMinutes(30).enabled(true).createdAt(FECHA_ORIGEN).updatedAt(FECHA_ORIGEN).build();
    }

    private EstadoCatalogo estado(Long version) {
        return EstadoCatalogo.builder().versionAplicada(version).actualizadoEn(FECHA_ORIGEN).build();
    }
}
