# turnos-catalogo

Servicio de catálogo y sincronización: Java 25, Spring Boot 4.1.0 y PostgreSQL 17.
Paquete base: `catalogo`, con la misma organización hexagonal por funcionalidad
que reservas. CAT-01 prepara arranque, configuración, logs y salud. CAT-02 agrega
persistencia de categorías, profesionales, horarios y versión con Flyway.
CAT-03 expone consultas locales protegidas con JWT RS256. CAT-04 consulta la
integración técnica de cátedra y CAT-05 inicializa la copia mediante snapshot.
CAT-08 agrega búsqueda local paginada y listado de categorías.
CAT-06 aplica cambios incrementales notificados por Kafka y leídos desde Redis.

## Ejecutar

Con Docker Compose:

```bash
cp .env.example .env
# Configurar DB_PASSWORD en .env.
mkdir -p secrets
# Copiar la clave pública de reservas a secrets/public.pem.
# Configurar CATEDRA_BASE_URL y CATEDRA_TOKEN_SOURCE en .env (ver abajo).
docker compose up --build -d
curl --fail http://localhost:8082/actuator/health/readiness
docker compose down
```

PostgreSQL se publica solo en localhost, puerto `5434`, y el servicio en `8082`.
Ambos tienen recursos propios, separados de reservas. El volumen conserva los
datos al detener los contenedores. Configuración en [.env.example](.env.example).
Al ejecutar Java en el host, proporcionar las variables de entorno; Spring no
carga `.env` automáticamente.

Catálogo solo necesita la clave pública. Configurar `JWT_PUBLIC_KEY`,
`JWT_ISSUER` y `JWT_AUDIENCE` según el ejemplo. Exige audiencia `turnos-catalogo`
y `ROLE_USER`. Reservas emite el JWT con ambas audiencias y conserva la privada.

## Cuenta técnica de cátedra

Conectarse a la VPN provista por la cátedra. Registrar la cuenta una sola vez con
Postman o una herramienta HTTP equivalente: `POST /api/student/register`, con
`login`, `password`, `groupId` y los datos opcionales del anexo. Guardar `id_token`
en un archivo externo, sin `Bearer`, con acceso restringido (`chmod 600` en Linux).
En Compose, configurar `APP_UID`/`APP_GID` con el UID/GID de su dueño (`id -u` /
`id -g`), usando un usuario sin privilegios. No guardar la respuesta en
documentación ni subirla al repositorio.

Ambos backends deben usar la misma cuenta y **el mismo JWT técnico**. Configurar
`CATEDRA_BASE_URL` y `CATEDRA_TOKEN_SOURCE` en `.env`; Compose monta únicamente el
archivo del token como solo lectura. Para Java en el host, usar
`CATEDRA_ENABLED=true` y `CATEDRA_TOKEN_FILE` con la ruta del archivo externo.
Este JWT es independiente del de usuarios y nunca se entrega a Android.

Con la integración habilitada, al arrancar se consulta
`GET /api/student/integration` con Bearer. Se conservan los nombres y credenciales
asignados por cátedra, sin inventar topics ni namespaces. `PENDING`, `FAILED` y
`REVOKED` no habilitan Redis/Kafka.
Los logs muestran solo estado o categoría de error, sin cuerpos ni secretos.
Un fallo externo no impide consultar la copia local disponible. Sin habilitar
la integración, el servicio funciona localmente y no contacta a cátedra.

Los timeouts de conexión/lectura son configurables (3s/5s por defecto). No se
siguen redirecciones ni se reintenta automáticamente un rechazo de autenticación.
Para obtener otro JWT, realizar el login operativo con
`POST /api/authenticate` (`username`, `password`, `rememberMe: true`) y actualizar
el archivo compartido. No se implementan login automático ni refresh técnico.
Según el anexo §5.4, ante exposición se debe crear otra cuenta, actualizar ambos
backends e informar a cátedra; la revocación anticipada queda fuera del alcance.

## Inicialización por snapshot

Con `CATEDRA_ENABLED=true`, si falta el checkpoint local, al arrancar se descarga
`GET /api/synchronization/snapshot` usando el mismo JWT técnico externo. Se validan
las tres colecciones, sus IDs/referencias y campos obligatorios antes de escribir.
Se conservan también las entidades deshabilitadas.

El reemplazo de categorías, profesionales y horarios y la actualización de
`snapshotVersion` se confirman en una sola transacción. Un fallo conserva el
estado anterior. Se serializan los reemplazos y se rechaza una versión inferior
a la aplicada; una versión igual puede reaplicarse para reconstruir la copia.

Al reiniciar con checkpoint existente se conserva la copia y se comprueba
qué versiones faltan. Si falla la primera carga, las consultas responden
`CATALOG_NOT_READY` hasta que se aplique un snapshot válido.

## Sincronización incremental — CAT-06

Con integración `PROVISIONED`, checkpoint existente y `CATEDRA_SYNC_ENABLED=true`,
se conecta Lettuce a Redis y se inicia Spring Kafka sobre el topic y consumer
group asignados. Las credenciales se obtienen de la integración técnica;
no se configuran cuentas adicionales ni se crean topics de cátedra.

`CatalogUpdated` notifica una versión. Se lee la metadata y, en orden, únicamente
los cambios posteriores al checkpoint y los IDs afectados de sus hashes. Se
aplican altas, modificaciones y bajas lógicas (`enabled=false`), conservando
las filas no afectadas. Cada versión y su checkpoint se guardan en una transacción.
Los hashes contienen el estado vigente de las entidades, no versiones históricas.

Los `eventId` procesados se guardan en PostgreSQL para deduplicar reentregas.
El offset Kafka se confirma después de persistir; no hay auto-commit. Una
notificación duplicada o atrasada no retrocede el catálogo. No se descarga un
snapshot por evento ni se lee todo el hash de entidades.

Si falla el procesamiento de un mensaje, el consumidor se detiene y conserva
el último checkpoint válido. El mensaje queda pendiente para su reentrega.

## Recuperación de la sincronización — CAT-07

Con la integración y el consumo habilitados, Catálogo verifica la versión en
Redis al arrancar y luego cada 60 segundos desde el fin de la última revisión.
El intervalo se configura con `CATEDRA_SYNC_RECOVERY_INTERVAL`. Esto también
detecta atrasos cuando se pierde una notificación Kafka.

Si Redis conserva los cambios pendientes, Catálogo se pone al día por
incrementos. Si la ventana expiró o un cambio falta o es inconsistente, solicita
un snapshot REST del catálogo como máximo una vez en ese ciclo. Solo lo aplica
si pasa la validación y alcanza la versión publicada observada. Las consultas
siguen usando el último checkpoint válido durante una caída de infraestructura.

Cuando el consumidor está detenido, cada ciclo intenta reconectar con la
integración asignada y recuperar el catálogo antes de reanudar Kafka. Los
mensajes pendientes se reentregan y se confirman según el checkpoint y la
deduplicación existentes.

Los timeouts por defecto son 3s para conexión Redis, 5s para comandos, 5s para
solicitudes Kafka y 10s para operaciones Kafka. Variables en [.env.example](.env.example).
En el Compose integral de Reservas se usan estos valores predeterminados; para
personalizarlos allí hay que pasarlos al entorno del servicio Catálogo.

## Consultas

| Método | Ruta | Resultado |
| --- | --- | --- |
| GET | `/api/professionals` | Búsqueda local paginada con filtros combinables. |
| GET | `/api/professional-categories` | Categorías con ID, nombre, habilitación y versión aplicada. |
| GET | `/api/professionals/{id}` | Detalle del profesional, incluido su estado habilitado. |
| GET | `/api/professionals/{id}/schedule` | Versión aplicada y horarios semanales habilitados. |

Todas requieren JWT Bearer de usuario. Usan únicamente la última versión aplicada localmente.
Sin una sincronización inicial responden `503 CATALOG_NOT_READY`. La agenda
semanal no representa turnos libres: reservas combinará
horarios, fecha y ocupaciones.

### Búsqueda — CAT-08

Ejemplo: `/api/professionals?categoryId=1&name=ana&enabled=true&dayOfWeek=MONDAY&time=09:30&page=0&size=20`.
Todos los filtros son opcionales y se combinan por AND:

- `categoryId`: ID positivo existente; categoría inexistente devuelve `400 INVALID_CATEGORY`.
- `name`: coincidencia parcial sobre nombre, apellido o nombre completo, sin
  distinguir mayúsculas; se recortan espacios extremos. Máximo 100 caracteres,
  con tildes significativas y `%`/`_` literales.
- `enabled`: estado del profesional. Omitido incluye habilitados y deshabilitados.
- `dayOfWeek`: día ISO de `MONDAY` a `SUNDAY`, con al menos un turno completo en
  una franja habilitada de un profesional habilitado.
- `time`: inicio potencial, alineado a la duración desde el inicio de la franja
  y con final dentro de ella. Requiere `dayOfWeek`. No verifica ocupaciones.
- `page`: desde cero; `size`: entre 1 y 100, por defecto 20. Orden fijo por
  apellido, nombre e ID. Páginas o combinaciones sin coincidencias devuelven lista vacía.

Respuesta: `{versionAplicada, content, page, size, totalElements}`; cada elemento
usa el mismo formato del detalle de profesional. Sin día no se exige tener agenda;
`enabled=false` combinado con día devuelve vacío. Parámetros malformados devuelven
`400 INVALID_REQUEST`. El listado de categorías devuelve `{versionAplicada, content}`,
ordenado por nombre e ID, incluyendo categorías deshabilitadas para informar su estado.
Los IDs del selector se obtienen de este listado, sin hardcodearlos.

Cada respuesta lee sus datos y versión en una transacción consistente. Si cambia
el catálogo entre dos peticiones, sus versiones pueden diferir. Una caída de la
base devuelve `503 CATALOG_UNAVAILABLE`, sin simular un resultado vacío.

## Verificar

Con JDK 25 y Docker activos:

```bash
./mvnw verify
```

Las pruebas usan PostgreSQL real con Testcontainers: persistencia, transacciones,
salud, contrato de consultas y rechazo de JWT o permisos inválidos. Las pruebas
de integración técnica usan respuestas simuladas y servidores HTTP locales:
no necesitan la VPN ni credenciales reales. Los GET de salud siguen siendo públicos.
El snapshot tiene pruebas de reemplazo, rollback, validación, reinicio y
escrituras concurrentes sin retroceso de versión.

El wrapper incluido utiliza Maven 3.9.11; requiere JDK 25.

Una rama por issue, con PR a `dev`. Promociones a `test` y `main` mediante PR.
Licencia [GPL-3.0](LICENSE).
