package catalogo.replica.infrastructure.kafka;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.json.JsonMapper;
import catalogo.integracion.domain.model.EstadoProvisionamiento;
import catalogo.integracion.domain.model.IntegracionCatedra;
import catalogo.integracion.domain.ports.in.ObtenerIntegracionCatedraUseCase;
import catalogo.replica.application.exception.IncrementalCatalogoException;
import catalogo.replica.application.exception.IncrementalCatalogoException.Error;
import catalogo.replica.application.service.CatalogoService;
import catalogo.replica.domain.ports.in.RecuperarCatalogoUseCase;
import catalogo.replica.infrastructure.config.IncrementalCatalogoInitializer;
import catalogo.replica.infrastructure.config.SincronizacionProperties;
import catalogo.replica.infrastructure.redis.RedisCambiosCatalogoAdapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;

@ExtendWith(OutputCaptureExtension.class)
class IncrementalContratoTest {
    private static final String EVENTO = """
            {"eventId":"evento-8","eventType":"CatalogUpdated","newVersion":8,
             "occurredAt":"2026-09-10T13:40:00Z","source":"SCHEDULED","schemaVersion":1,"extra":true}
            """;
    private CatalogoService catalogo;
    private CatalogUpdatedListener listener;
    private Acknowledgment ack;

    @BeforeEach void preparar() {
        catalogo = mock(CatalogoService.class); ack = mock(Acknowledgment.class);
        listener = new CatalogUpdatedListener(catalogo, JsonMapper.builder().build());
    }

    @Test void respetaElContratoYElOrdenDePersistenciaYConfirmacion() {
        listener.onMessage(record("8", EVENTO), ack);
        var order = inOrder(catalogo, ack);
        order.verify(catalogo).actualizar("evento-8", 8L);
        order.verify(ack).acknowledge();
    }

    @ParameterizedTest
    @ValueSource(strings = {"json", "tipo", "esquema", "id", "version", "fecha", "source", "key", "null"})
    void eventoInvalidoNoLlegaAlCasoDeUsoNiConfirma(String caso, CapturedOutput output) {
        String body = switch (caso) {
            case "json" -> "{secreto-no-registrar";
            case "tipo" -> EVENTO.replace("CatalogUpdated", "Other");
            case "esquema" -> EVENTO.replace("\"schemaVersion\":1", "\"schemaVersion\":2");
            case "id" -> EVENTO.replace("evento-8", "");
            case "version" -> EVENTO.replace("\"newVersion\":8", "\"newVersion\":-1");
            case "fecha" -> EVENTO.replace("2026-09-10T13:40:00Z", "ayer");
            case "source" -> EVENTO.replace("SCHEDULED", "");
            case "null" -> null;
            default -> EVENTO;
        };
        assertThatThrownBy(() -> listener.onMessage(record(caso.equals("key") ? "9" : "8", body), ack))
                .isInstanceOfSatisfying(IncrementalCatalogoException.class, e -> {
                    assertThat(e.getError()).isEqualTo(Error.EVENTO_INVALIDO);
                    assertThat(e.getCause()).isNull();
                });
        verifyNoInteractions(catalogo, ack);
        assertThat(output).doesNotContain("secreto-no-registrar");
    }

    @Test void errorDePersistenciaNoExponeLaCausaNiConfirma(CapturedOutput output) {
        when(catalogo.actualizar("evento-8", 8L)).thenThrow(new DataAccessResourceFailureException("secreto-no-registrar"));
        assertThatThrownBy(() -> listener.onMessage(record("8", EVENTO), ack))
                .isInstanceOfSatisfying(IncrementalCatalogoException.class, e -> {
                    assertThat(e.getError()).isEqualTo(Error.PERSISTENCIA_NO_DISPONIBLE);
                    assertThat(e.getCause()).isNull();
                });
        verifyNoInteractions(ack); assertThat(output).doesNotContain("secreto-no-registrar");
    }

    @Test void configuraSoloElTopicYGrupoAsignadosSinAutoCommitNiRedisPassword() {
        var config = new AtomicReference<Map<String, Object>>();
        var topic = new AtomicReference<String>();
        @SuppressWarnings("unchecked") var container = (KafkaMessageListenerContainer<String, String>) mock(KafkaMessageListenerContainer.class);
        var consumer = new KafkaCatalogoConsumer(properties(true), listener) {
            @Override protected KafkaMessageListenerContainer<String, String> crearContenedor(Map<String, Object> values, String value) {
                config.set(values); topic.set(value); return container;
            }
        };
        var integration = integracion(EstadoProvisionamiento.PROVISIONED);
        consumer.arrancar(integration);
        assertThat(config.get()).containsEntry("bootstrap.servers", "broker.test:9092")
                .containsEntry("group.id", "grupo-asignado").containsEntry("enable.auto.commit", false)
                .containsEntry("allow.auto.create.topics", false).containsEntry("max.poll.records", 1)
                .doesNotContainValue(integration.getRedisPassword());
        assertThat(topic).hasValue("catalogo-asignado");
        verify(container).start(); consumer.cerrar(); verify(container).stop();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProvisionamiento.class, names = {"PENDING", "FAILED", "REVOKED"})
    void noArrancaClientesSinProvisionar(EstadoProvisionamiento estado) {
        var recuperacion = mock(RecuperarCatalogoUseCase.class); var redis = mock(RedisCambiosCatalogoAdapter.class);
        var kafka = mock(KafkaCatalogoConsumer.class); var integration = mock(ObtenerIntegracionCatedraUseCase.class);
        when(integration.obtener()).thenReturn(integracion(estado));
        new IncrementalCatalogoInitializer(properties(true), integration, recuperacion, redis, kafka).run(new DefaultApplicationArguments());
        verifyNoInteractions(redis, recuperacion);
        verify(kafka, never()).arrancar(any());
    }

    @Test void conConsumidorActivoRevisaVersionesSinVolverAConectar() {
        var recuperacion = mock(RecuperarCatalogoUseCase.class);
        var redis = mock(RedisCambiosCatalogoAdapter.class);
        var kafka = mock(KafkaCatalogoConsumer.class);
        var integration = mock(ObtenerIntegracionCatedraUseCase.class);
        when(kafka.activo()).thenReturn(true);
        new IncrementalCatalogoInitializer(properties(true), integration, recuperacion, redis, kafka).revisar();
        verify(recuperacion).recuperar();
        verifyNoInteractions(integration, redis);
        verify(kafka, never()).arrancar(any());
    }

    @Test void sincronizacionDeshabilitadaNoHaceConexiones() {
        var recuperacion = mock(RecuperarCatalogoUseCase.class); var redis = mock(RedisCambiosCatalogoAdapter.class);
        var kafka = mock(KafkaCatalogoConsumer.class); var integration = mock(ObtenerIntegracionCatedraUseCase.class);
        new IncrementalCatalogoInitializer(properties(false), integration, recuperacion, redis, kafka).run(new DefaultApplicationArguments());
        verifyNoInteractions(recuperacion, integration, redis, kafka);
    }

    @Test void falloDeRedisCierraRecursosYMantieneElServicioLocalDisponible(CapturedOutput output) {
        var recuperacion = mock(RecuperarCatalogoUseCase.class); var redis = mock(RedisCambiosCatalogoAdapter.class);
        var kafka = mock(KafkaCatalogoConsumer.class); var integration = mock(ObtenerIntegracionCatedraUseCase.class);
        var config = integracion(EstadoProvisionamiento.PROVISIONED); when(integration.obtener()).thenReturn(config);
        doThrow(new IncrementalCatalogoException(Error.REDIS_NO_DISPONIBLE)).when(redis).preparar(config);
        new IncrementalCatalogoInitializer(properties(true), integration, recuperacion, redis, kafka).run(new DefaultApplicationArguments());
        verify(kafka, never()).arrancar(any()); verify(kafka).cerrar(); verify(redis).cerrar();
        assertThat(output).contains("REDIS_NO_DISPONIBLE").doesNotContain(config.getRedisPassword());
    }

    @Test void reintentaEnElSiguienteCicloYRecuperaAntesDeReanudarKafka() {
        var recuperacion = mock(RecuperarCatalogoUseCase.class);
        var redis = mock(RedisCambiosCatalogoAdapter.class);
        var kafka = mock(KafkaCatalogoConsumer.class);
        var integration = mock(ObtenerIntegracionCatedraUseCase.class);
        var config = integracion(EstadoProvisionamiento.PROVISIONED);
        when(integration.obtener()).thenReturn(config);
        doThrow(new IncrementalCatalogoException(Error.REDIS_NO_DISPONIBLE)).doNothing()
                .when(redis).preparar(config);

        var initializer = new IncrementalCatalogoInitializer(properties(true), integration, recuperacion, redis, kafka);
        initializer.revisar();
        verify(redis, times(1)).preparar(config);
        verifyNoInteractions(recuperacion);
        verify(kafka, never()).arrancar(any());

        initializer.revisar();
        var orden = inOrder(redis, recuperacion, kafka);
        orden.verify(redis).cerrar();
        orden.verify(redis).preparar(config);
        orden.verify(recuperacion).recuperar();
        orden.verify(kafka).arrancar(config);
    }

    @Test void springEjecutaLaRevisionPeriodicaDespuesDelArranque() {
        var ciclos = new java.util.concurrent.atomic.AtomicInteger();
        var kafka = mock(KafkaCatalogoConsumer.class);
        when(kafka.activo()).thenReturn(true);
        try (var context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of("app.catedra.enabled=true", "app.catedra.sync.recovery-interval=50ms")
                    .applyTo(context);
            context.registerBean(IncrementalCatalogoInitializer.class,
                    () -> new IncrementalCatalogoInitializer(properties(true),
                            mock(ObtenerIntegracionCatedraUseCase.class),
                            () -> {
                                ciclos.incrementAndGet();
                                return null;
                            },
                            mock(RedisCambiosCatalogoAdapter.class), kafka));
            context.refresh();
            var initializer = context.getBean(IncrementalCatalogoInitializer.class);
            initializer.revisarPeriodicamente();
            assertThat(ciclos).hasValue(0);
            initializer.run(new DefaultApplicationArguments());
            await().atMost(Duration.ofSeconds(3)).until(() -> ciclos.get() >= 2);
        }
    }

    private ConsumerRecord<String, String> record(String key, String value) { return new ConsumerRecord<>("catalogo", 0, 13, key, value); }
    private SincronizacionProperties properties(boolean enabled) {
        return new SincronizacionProperties(enabled, Duration.ofSeconds(3), Duration.ofSeconds(5), "PLAINTEXT", Duration.ofSeconds(5), Duration.ofSeconds(10));
    }
    private IntegracionCatedra integracion(EstadoProvisionamiento status) {
        return IntegracionCatedra.builder().provisioningStatus(status).kafkaBootstrapServers("broker.test:9092")
                .kafkaConsumerGroupId("grupo-asignado").kafkaCatalogTopic("catalogo-asignado").redisPassword("secreto-redis-ficticio").build();
    }
}
