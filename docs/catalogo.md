# Catálogo y sincronización

Catálogo mantiene una réplica de categorías, profesionales y horarios semanales de cátedra. Las búsquedas y agendas se sirven desde su PostgreSQL. Reservas usa la agenda para calcular los turnos libres, combinándola con las ocupaciones que obtiene de cátedra.

## Datos y propiedad

Cada backend administra su base, usuario y migraciones. El Compose integral de Reservas levanta un PostgreSQL para cada servicio. Catálogo conserva los identificadores de cátedra para poder actualizar cada fila por ID y mantener las referencias del snapshot y de los incrementos.

| Tabla | Contenido |
| --- | --- |
| `professional_category` | Categorías por ID de origen, con nombre, descripción, habilitación y fechas. |
| `professional` | Profesionales por ID de origen, con nombre, apellido, categoría y habilitación. |
| `weekly_schedule` | Franjas semanales por profesional, con día ISO, horas locales, duración del turno y habilitación. |
| `catalog_state` | Una fila con la versión aplicada y su fecha. La ausencia de fila significa que todavía falta inicializar la réplica. La versión cero es válida. |
| `catalog_processed_event` | Identificadores Kafka ya procesados, con versión notificada y fecha de procesamiento. |

Una categoría puede referenciarse desde varios profesionales, y un profesional puede tener varias franjas. Las claves foráneas resguardan esas relaciones. Los cambios incrementales representan bajas mediante `enabled=false`. Un snapshot reemplaza las tres colecciones con el conjunto suministrado por cátedra. Flyway crea y versiona el esquema. [Migraciones](../src/main/resources/db/migration)

## Contratos

| Operación | Ruta | Resultado |
| --- | --- | --- |
| Buscar | `GET /api/professionals` | Página `{versionAplicada, content, page, size, totalElements}`. Admite `categoryId`, `name`, `enabled`, `dayOfWeek` y `time`. La hora requiere día y debe alinearse con el inicio y duración de una franja. |
| Categorías | `GET /api/professional-categories` | `{versionAplicada, content}`, con categorías habilitadas y deshabilitadas. |
| Detalle | `GET /api/professionals/{id}` | Datos del profesional, incluido su estado de habilitación. |
| Agenda | `GET /api/professionals/{id}/schedule` | Profesional, versión y franjas semanales habilitadas. |
| Diagnóstico | `GET /api/catalog/sync/status` | `{versionAplicada, estado, ultimoError}`. El error es un código seguro o `null`. |

Las consultas exigen el JWT de usuario emitido por Reservas, con firma RS256, emisor compartido, audiencia de Catálogo y `ROLE_USER`. Catálogo verifica la firma con la clave pública de Reservas. Las rutas de salud de Actuator son públicas.

Un JWT ausente, inválido o vencido devuelve `401` y un JWT válido con rol insuficiente devuelve `403`. Los filtros mal formados devuelven `400`. El detalle devuelve `404` si el profesional no existe, y su agenda devuelve `409 PROFESSIONAL_DISABLED` si está deshabilitado. Las consultas de datos devuelven `503 CATALOG_NOT_READY` hasta aplicar el primer snapshot y `503 CATALOG_UNAVAILABLE` si falla la base. El diagnóstico devuelve `503 CATALOG_STATUS_UNAVAILABLE` si no puede leer el checkpoint.

Ejemplo de diagnóstico cuando la copia anterior sigue disponible durante una falla de Redis:

```json
{"versionAplicada":374,"estado":"DEGRADADO","ultimoError":"REDIS_NO_DISPONIBLE"}
```

`SIN_SNAPSHOT` indica una réplica sin checkpoint. `PENDIENTE` indica que falta una verificación reciente o una asignación Kafka. `AL_DIA` refleja la última comparación de versiones. El último error activo se guarda en memoria y vuelve a diagnosticarse tras reiniciar. El checkpoint permanece en PostgreSQL. [Filtros y paginación](../README.md)

## Sincronización y recuperación

1. Con la VPN conectada, Catálogo consulta `GET /api/student/integration` usando el JWT técnico. La configuración provisionada indica los recursos de Kafka y Redis. Los dos backends usan la misma cuenta técnica de cátedra.
2. En una réplica nueva, Catálogo descarga `GET /api/synchronization/snapshot`. Valida las colecciones y referencias y guarda datos y checkpoint en una transacción. Si falla, conserva la copia previa.
3. Kafka anuncia `CatalogUpdated` con la versión como clave. Catálogo lee la metadata de Redis, luego `catedra:sync:changes:{version}` y los IDs indicados en los hashes de categorías, profesionales y franjas. Procesa de `versionAplicada + 1` hasta `currentVersion` y guarda cada versión con sus cambios. Después de aplicar las versiones y registrar `eventId`, confirma el offset Kafka.
4. Al arrancar y periódicamente, Catálogo compara su checkpoint con Redis. La revisión funciona aunque falte un aviso de Kafka. Si `versionAplicada < oldestAvailableVersion`, falta historial y solicita el snapshot. También lo solicita ante un incremento ausente o inconsistente. Si la ventana todavía contiene lo necesario, aplica incrementos aunque hayan pasado varios días.
5. Ante un fallo de procesamiento, el consumidor se detiene y conserva el offset pendiente. La revisión vuelve a preparar la conexión y recuperar la réplica antes de reanudar el consumo. La última copia válida sigue disponible mientras la base local esté accesible.

El intervalo predeterminado es de 60 segundos desde el fin de la revisión anterior. En el Compose individual puede ajustarse con `CATEDRA_SYNC_RECOVERY_INTERVAL`. [Lógica incremental y snapshot](../src/main/java/catalogo/replica/application/usecases/IncrementosCatalogo.java) · [Revisión periódica](../src/main/java/catalogo/replica/infrastructure/config/IncrementalCatalogoInitializer.java)

## Ejecutar y comprobar

Para iniciar Catálogo por separado, instalá Docker Compose, prepará las variables de [`.env.example`](../.env.example) en un `.env` local y configurá:

- Contraseña de su PostgreSQL y UID/GID del usuario que puede leer los montajes.
- Clave pública de Reservas en `secrets/public.pem`, con emisor y audiencia correspondientes.
- URL de cátedra y ruta al archivo externo del JWT técnico, con la VPN conectada. El token va en el archivo local, sin el prefijo `Bearer`.

```bash
docker compose config --quiet
docker compose up --build -d
curl -fsS http://127.0.0.1:8082/actuator/health/readiness
```

El puerto local de Catálogo es `8082` y el de su PostgreSQL es `5434`, salvo configuración. Si vas a probar Reservas y Catálogo juntos, usá el Compose integral desde el repositorio de Reservas con `.env.integracion`.

Con un JWT de usuario vigente en Postman, consultá el diagnóstico, las categorías y una página de profesionales. Si ves `SIN_SNAPSHOT` o `CATALOG_NOT_READY`, revisá la VPN, el JWT técnico y el log de inicialización. `readiness` comprueba arranque y acceso a la base. El diagnóstico muestra si la copia está inicializada y sincronizada. `docker compose down` detiene el servicio individual conservando su volumen.

## Pruebas

Desde la raíz del repositorio, con JDK 25 y Docker:

```bash
./mvnw clean verify
```

| Área | Cobertura y punto de partida |
| --- | --- |
| Snapshot | Contrato HTTP, validación de referencias, versión cero, reemplazo, rollback y persistencia al reiniciar. [Contrato](../src/test/java/catalogo/SnapshotCatalogoHttpTest.java) · [Persistencia](../src/test/java/catalogo/SnapshotCatalogoPersistenceTest.java) |
| Incrementos y recuperación | Redis y Kafka locales, orden, duplicados, reentrega, límite de retención y recuperación por snapshot. [Integración incremental](../src/test/java/catalogo/IncrementalCatalogoIntegrationTest.java) · [Contrato Kafka](../src/test/java/catalogo/replica/infrastructure/kafka/IncrementalContratoTest.java) |
| Consultas y seguridad | Filtros combinados, paginación, códigos HTTP, JWT ausente, inválido o vencido y diagnóstico con PostgreSQL caído. [Búsqueda](../src/test/java/catalogo/BusquedaCatalogoIntegrationTest.java) · [Consultas y seguridad](../src/test/java/catalogo/CatalogoConsultasIntegrationTest.java) · [Arranque](../src/test/java/catalogo/ServiceBootstrapTest.java) |

La suite normal usa datos ficticios y dependencias locales o simuladas. La prueba de conexión real a cátedra es opcional.
