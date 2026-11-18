package catalogo;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import catalogo.integracion.domain.model.EstadoProvisionamiento;
import catalogo.integracion.domain.model.IntegracionCatedra;
import catalogo.replica.application.exception.IncrementalCatalogoException;
import catalogo.replica.application.exception.IncrementalCatalogoException.Error;
import catalogo.replica.application.service.CatalogoService;
import catalogo.replica.domain.model.CambioCatalogo;
import catalogo.replica.domain.ports.out.CatalogoRepository;
import catalogo.replica.infrastructure.kafka.CatalogUpdatedListener;
import catalogo.replica.infrastructure.kafka.KafkaCatalogoConsumer;
import catalogo.replica.infrastructure.redis.RedisCambiosCatalogoAdapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
class IncrementalCatalogoIntegrationTest {
    private static final String PASSWORD = "clave-ficticia-redis-cat06";
    @Container static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379).withCommand("redis-server", "--requirepass", PASSWORD);
    @Container static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.8.0");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestKeys.configurar(registry);
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired private CatalogoRepository catalogo;
    @Autowired private CatalogoService service;
    @Autowired private RedisCambiosCatalogoAdapter adapter;
    @Autowired private KafkaCatalogoConsumer consumer;
    @Autowired private CatalogUpdatedListener listener;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JsonMapper json;
    private RedisClient writer;
    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> redis;

    @BeforeEach
    void preparar() {
        consumer.cerrar(); adapter.cerrar();
        for (var table : List.of("catalog_processed_event", "weekly_schedule", "professional", "professional_category", "catalog_state")) {
            jdbc.update("DELETE FROM " + table);
        }
        catalogo.reemplazarSnapshot(SnapshotTestData.snapshot(7, 0));
        var otro = SnapshotTestData.snapshot(7, 100);
        catalogo.guardarCategoria(otro.getProfessionalCategories().getFirst());
        catalogo.guardarProfesional(otro.getProfessionals().getFirst());
        catalogo.guardarHorario(otro.getWeeklySchedules().getFirst());
        writer = RedisClient.create(RedisURI.builder().withHost(REDIS.getHost()).withPort(REDIS.getMappedPort(6379))
                .withAuthentication("default", PASSWORD).withTimeout(Duration.ofSeconds(2)).build());
        connection = writer.connect(); redis = connection.sync(); redis.flushall();
        adapter.preparar(config("topic-prueba", "grupo-prueba"));
        metadata(8, 0);
    }

    @AfterEach
    void cerrar() {
        consumer.cerrar(); adapter.cerrar();
        if (connection != null) connection.close();
        if (writer != null) writer.shutdown(Duration.ZERO, Duration.ofSeconds(1));
    }

    @Test
    void aplicaSoloElProfesionalAfectadoYConfirmaDespuesDePersistir(CapturedOutput output) {
        var sinTocar = catalogo.buscarProfesional(115L).orElseThrow();
        var horario = catalogo.buscarHorario(21L).orElseThrow();
        cambioProfesional(8, false);
        var confirmados = new AtomicInteger();
        listener.onMessage(record("evento-8", 8), () -> {
            assertThat(catalogo.obtenerEstado().orElseThrow().getVersionAplicada()).isEqualTo(8);
            assertThat(catalogo.buscarEventoProcesado("evento-8")).contains(8L);
            assertThat(catalogo.buscarProfesional(15L).orElseThrow().getEnabled()).isFalse();
            confirmados.incrementAndGet();
        });
        assertThat(confirmados).hasValue(1);
        var intacto = catalogo.buscarProfesional(115L).orElseThrow();
        assertThat(intacto).usingRecursiveComparison().isEqualTo(sinTocar);
        assertThat(catalogo.buscarHorario(21L).orElseThrow()).usingRecursiveComparison().isEqualTo(horario);
        assertThat(service.obtenerProfesional(15L).getEnabled()).isFalse();
        assertThatThrownBy(() -> service.obtenerAgenda(15L))
                .isInstanceOf(catalogo.replica.application.exception.ProfesionalDeshabilitadoException.class);
        assertThat(output).doesNotContain(PASSWORD);
    }

    @Test
    void funcionaConAclDeSoloLecturaSinHgetallNiAccesoFueraDelNamespace() {
        redis.aclSetuser("lector", new io.lettuce.core.AclSetuserArgs().on().addPassword("clave-lector-ficticia")
                .keyPattern("catedra:sync:*").addCategory(io.lettuce.core.AclCategory.CONNECTION)
                .addCommand(io.lettuce.core.protocol.CommandType.GET).addCommand(io.lettuce.core.protocol.CommandType.HMGET));
        var config = config("topic-prueba", "grupo-prueba");
        config.setRedisUsername("lector"); config.setRedisPassword("clave-lector-ficticia");
        adapter.preparar(config);
        cambioProfesional(8, false);
        assertThat(service.actualizar("solo-lectura-8", 8L).getVersionAplicada()).isEqualTo(8);
    }

    @Test
    void redisNoDisponibleNoConfirmaNiAlteraElCheckpoint() {
        adapter.cerrar();
        var ack = new AtomicInteger();
        assertThatThrownBy(() -> listener.onMessage(record("redis-caido-8", 8), ack::incrementAndGet))
                .isInstanceOfSatisfying(IncrementalCatalogoException.class, e -> assertThat(e.getError()).isEqualTo(Error.REDIS_NO_DISPONIBLE));
        assertThat(ack).hasValue(0);
        assertThat(catalogo.obtenerEstado().orElseThrow().getVersionAplicada()).isEqualTo(7);
        assertThat(catalogo.buscarEventoProcesado("redis-caido-8")).isEmpty();
    }

    @Test
    void duplicadoNoReleeRedisNiModificaCheckpointYSobreviveAReentrega() {
        cambioProfesional(8, false);
        service.actualizar("evento-8", 8L);
        var previo = catalogo.obtenerEstado().orElseThrow();
        adapter.cerrar();
        var estado = service.actualizar("evento-8", 8L);
        assertThat(estado).usingRecursiveComparison().isEqualTo(previo);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog_processed_event", Long.class)).isEqualTo(1);
        assertThatThrownBy(() -> service.actualizar("evento-8", 9L))
                .isInstanceOfSatisfying(IncrementalCatalogoException.class, e -> assertThat(e.getError()).isEqualTo(Error.EVENTO_CONFLICTIVO));
    }

    @Test
    void aplicaVersionesPendientesEnOrdenYEventosDesordenadosNoRetroceden() {
        metadata(9, 0); cambioProfesional(8, false);
        redis.set("catedra:sync:changes:9", cambioJson(9, "[]", "[]", "[]"));
        assertThat(service.actualizar("evento-9", 9L).getVersionAplicada()).isEqualTo(9);
        var previo = catalogo.obtenerEstado().orElseThrow().getActualizadoEn();
        adapter.cerrar();
        assertThat(service.actualizar("evento-tardio-8", 8L).getVersionAplicada()).isEqualTo(9);
        assertThat(catalogo.obtenerEstado().orElseThrow().getActualizadoEn()).isEqualTo(previo);
    }

    @Test
    void altaDeCategoriaProfesionalYHorarioMantieneReferencias() {
        var nuevo = SnapshotTestData.snapshot(8, 200);
        redis.hset("catedra:sync:professional-categories", "201", json.writeValueAsString(nuevo.getProfessionalCategories().getFirst()));
        redis.hset("catedra:sync:professionals", "215", json.writeValueAsString(nuevo.getProfessionals().getFirst()));
        redis.hset("catedra:sync:weekly-schedules", "221", json.writeValueAsString(nuevo.getWeeklySchedules().getFirst()));
        redis.set("catedra:sync:changes:8", cambioJson(8, "[201]", "[215]", "[221]"));
        service.actualizar("alta-8", 8L);
        assertThat(catalogo.buscarHorario(221L).orElseThrow().getProfessionalId()).isEqualTo(215);
        assertThat(catalogo.buscarProfesional(215L).orElseThrow().getCategoryId()).isEqualTo(201);
        assertThat(catalogo.buscarProfesional(15L)).isPresent();
    }

    @Test
    void bajaLogicaDeCategoriaYHorarioSeConservaSinBorradoFisico() {
        var snapshot = SnapshotTestData.snapshot(8, 0);
        snapshot.getProfessionalCategories().getFirst().setEnabled(false);
        snapshot.getWeeklySchedules().getFirst().setEnabled(false);
        redis.hset("catedra:sync:professional-categories", "1", json.writeValueAsString(snapshot.getProfessionalCategories().getFirst()));
        redis.hset("catedra:sync:weekly-schedules", "21", json.writeValueAsString(snapshot.getWeeklySchedules().getFirst()));
        redis.set("catedra:sync:changes:8", cambioJson(8, "[1]", "[]", "[21]"));
        service.actualizar("baja-8", 8L);
        assertThat(catalogo.buscarCategoria(1L).orElseThrow().getEnabled()).isFalse();
        assertThat(catalogo.buscarHorario(21L).orElseThrow().getEnabled()).isFalse();
    }

    @Test
    void rollbackDeVersionFallidaConservaFilasPreviasYNoConfirma() {
        var profesional = SnapshotTestData.snapshot(8, 0).getProfessionals().getFirst();
        profesional.setCategoryId(999L);
        redis.hset("catedra:sync:professionals", "15", json.writeValueAsString(profesional));
        redis.set("catedra:sync:changes:8", cambioJson(8, "[]", "[15]", "[]"));
        var ack = new AtomicInteger();
        assertThatThrownBy(() -> listener.onMessage(record("fallo-8", 8), ack::incrementAndGet))
                .isInstanceOfSatisfying(IncrementalCatalogoException.class, e -> assertThat(e.getError()).isEqualTo(Error.PERSISTENCIA_NO_DISPONIBLE));
        assertThat(ack).hasValue(0);
        assertThat(catalogo.obtenerEstado().orElseThrow().getVersionAplicada()).isEqualTo(7);
        assertThat(catalogo.buscarProfesional(15L).orElseThrow().getCategoryId()).isEqualTo(1);
        assertThat(catalogo.buscarEventoProcesado("fallo-8")).isEmpty();
    }

    @Test
    void falloEnVersionSiguienteNoDeshaceLaAnteriorYPermiteReentrega() {
        metadata(9, 0); cambioProfesional(8, false);
        assertThatThrownBy(() -> service.actualizar("evento-9", 9L)).isInstanceOf(IncrementalCatalogoException.class);
        assertThat(catalogo.obtenerEstado().orElseThrow().getVersionAplicada()).isEqualTo(8);
        assertThat(catalogo.buscarEventoProcesado("evento-9")).isEmpty();
        redis.set("catedra:sync:changes:9", cambioJson(9, "[]", "[]", "[]"));
        assertThat(service.actualizar("evento-9", 9L).getVersionAplicada()).isEqualTo(9);
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-cambio", "no-entidad", "version-incorrecta", "id-incorrecto", "json-invalido", "coleccion-ausente", "ids-repetidos"})
    void respuestasInvalidasNoAvanzanVersionNiRegistranEvento(String caso) {
        cambioProfesional(8, false);
        switch (caso) {
            case "no-cambio" -> redis.del("catedra:sync:changes:8");
            case "no-entidad" -> redis.hdel("catedra:sync:professionals", "15");
            case "version-incorrecta" -> redis.set("catedra:sync:changes:8", cambioJson(9, "[]", "[15]", "[]"));
            case "id-incorrecto" -> redis.hset("catedra:sync:professionals", "15", "{\"id\":16}");
            case "json-invalido" -> redis.set("catedra:sync:changes:8", "{");
            case "coleccion-ausente" -> redis.set("catedra:sync:changes:8", "{\"version\":8,\"changes\":{}}");
            default -> redis.set("catedra:sync:changes:8", cambioJson(8, "[]", "[15,15]", "[]"));
        }
        assertThatThrownBy(() -> service.actualizar("invalido-8", 8L)).isInstanceOf(IncrementalCatalogoException.class);
        assertThat(catalogo.obtenerEstado().orElseThrow().getVersionAplicada()).isEqualTo(7);
        assertThat(catalogo.buscarEventoProcesado("invalido-8")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ventana", "adelantada", "metadata"})
    void validaMetadataAntesDeAplicar(String caso) {
        if (caso.equals("ventana")) metadata(9, 8);
        else if (caso.equals("adelantada")) metadata(7, 0);
        else redis.hdel("catedra:sync:metadata", "publishedAt");
        assertThatThrownBy(() -> service.actualizar("metadata-8", 8L)).isInstanceOf(IncrementalCatalogoException.class);
        assertThat(catalogo.obtenerEstado().orElseThrow().getVersionAplicada()).isEqualTo(7);
    }

    @Test
    void concurrenciaNoAplicaDosVecesLaVersionNiDuplicaEvento() throws Exception {
        cambioProfesional(8, false);
        try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var a = pool.submit(() -> service.actualizar("concurrente-8", 8L));
            var b = pool.submit(() -> service.actualizar("concurrente-8", 8L));
            assertThat(a.get(5, TimeUnit.SECONDS).getVersionAplicada()).isEqualTo(8);
            assertThat(b.get(5, TimeUnit.SECONDS).getVersionAplicada()).isEqualTo(8);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog_processed_event", Long.class)).isEqualTo(1);
    }

    @Test
    void noOmiteUnaVersionNiRegistraUnEventoAntesDeSuCheckpoint() {
        var c = CambioCatalogo.builder().version(9L).categorias(List.of()).profesionales(List.of()).horarios(List.of()).build();
        assertThatThrownBy(() -> catalogo.aplicarCambio(8, c)).isInstanceOf(IncrementalCatalogoException.class);
        assertThatThrownBy(() -> catalogo.registrarEventoProcesado("antes", 8)).isInstanceOf(IncrementalCatalogoException.class);
    }

    @Test
    void kafkaRealConfirmaSoloDespuesDePostgresYElDuplicadoNoAlteraDatos() throws Exception {
        String topic = "catalogo-" + UUID.randomUUID(); String group = "grupo-" + UUID.randomUUID();
        crearTopic(topic); cambioProfesional(8, false);
        try (var producer = productor()) {
            consumer.arrancar(config(topic, group));
            producer.send(new ProducerRecord<>(topic, "8", evento("real-8", 8))).get(5, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(offset(group, topic)).isEqualTo(1));
            assertThat(catalogo.buscarEventoProcesado("real-8")).contains(8L);
            assertThat(catalogo.buscarProfesional(15L).orElseThrow().getEnabled()).isFalse();
            var previo = catalogo.obtenerEstado().orElseThrow().getActualizadoEn();
            producer.send(new ProducerRecord<>(topic, "8", evento("real-8", 8))).get(5, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(offset(group, topic)).isEqualTo(2));
            assertThat(catalogo.obtenerEstado().orElseThrow().getActualizadoEn()).isEqualTo(previo);
        }
    }

    @Test
    void kafkaRealNoConfirmaErrorYReinicioManualReentregaElMismoMensaje() throws Exception {
        String topic = "catalogo-" + UUID.randomUUID(); String group = "grupo-" + UUID.randomUUID();
        crearTopic(topic); redis.set("catedra:sync:changes:8", cambioJson(8, "[]", "[15]", "[]"));
        try (var producer = productor()) {
            consumer.arrancar(config(topic, group));
            producer.send(new ProducerRecord<>(topic, "8", evento("recuperable-8", 8))).get(5, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(20)).until(() -> !consumer.activo());
            assertThat(offset(group, topic)).isEqualTo(0);
            assertThat(catalogo.obtenerEstado().orElseThrow().getVersionAplicada()).isEqualTo(7);
            cambioProfesional(8, false);
            consumer.arrancar(config(topic, group));
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(offset(group, topic)).isEqualTo(1));
            assertThat(catalogo.buscarEventoProcesado("recuperable-8")).contains(8L);
        }
    }

    private void metadata(long current, long oldest) {
        redis.hset("catedra:sync:metadata", Map.of("currentVersion", "" + current, "oldestAvailableVersion", "" + oldest,
                "publishedAt", SnapshotTestData.FECHA.toString()));
    }

    private void cambioProfesional(long version, boolean enabled) {
        var p = SnapshotTestData.snapshot(version, 0).getProfessionals().getFirst(); p.setEnabled(enabled);
        redis.hset("catedra:sync:professionals", "15", json.writeValueAsString(p));
        redis.set("catedra:sync:changes:" + version, cambioJson(version, "[]", "[15]", "[]"));
    }

    private String cambioJson(long version, String categories, String professionals, String schedules) {
        return "{\"version\":" + version + ",\"changes\":{\"professionalCategories\":" + categories
                + ",\"professionals\":" + professionals + ",\"weeklySchedules\":" + schedules + "}}";
    }

    private ConsumerRecord<String, String> record(String eventId, long version) {
        return new ConsumerRecord<>("catalogo-prueba", 0, 0, "" + version, evento(eventId, version));
    }

    private String evento(String eventId, long version) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"CatalogUpdated\",\"newVersion\":" + version
                + ",\"occurredAt\":\"2026-09-10T13:40:00Z\",\"source\":\"SCHEDULED\",\"schemaVersion\":1,\"extra\":true}";
    }

    private IntegracionCatedra config(String topic, String group) {
        return IntegracionCatedra.builder().provisioningStatus(EstadoProvisionamiento.PROVISIONED)
                .redisHost(REDIS.getHost()).redisPort(REDIS.getMappedPort(6379)).redisUsername("default").redisPassword(PASSWORD)
                .kafkaBootstrapServers(KAFKA.getBootstrapServers()).kafkaConsumerGroupId(group).kafkaCatalogTopic(topic).build();
    }

    private void crearTopic(String topic) throws Exception {
        // Infraestructura efímera de pruebas, nunca topics de cátedra.
        try (var admin = Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get(5, TimeUnit.SECONDS);
        }
    }

    private long offset(String group, String topic) throws Exception {
        try (var admin = Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            var value = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS)
                    .get(new TopicPartition(topic, 0));
            return value == null ? 0 : value.offset();
        }
    }

    private KafkaProducer<String, String> productor() {
        return new KafkaProducer<>(Map.of("bootstrap.servers", KAFKA.getBootstrapServers(), "key.serializer", StringSerializer.class,
                "value.serializer", StringSerializer.class));
    }
}
