# turnos-catalogo

Servicio de catálogo y sincronización: Java 25, Spring Boot 4.1.0 y PostgreSQL 17.
Paquete base: `catalogo`, con la misma organización hexagonal por funcionalidad
que reservas. CAT-01 prepara arranque, configuración, logs y salud. CAT-02 agrega
persistencia de categorías, profesionales, horarios y versión con Flyway.
CAT-03 expone consultas locales protegidas con JWT RS256. CAT-04 consulta la
integración técnica de cátedra y CAT-05 inicializa la copia mediante snapshot.

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
`REVOKED` no habilitan Redis/Kafka; sus clientes corresponden a CAT-06.
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

Al reiniciar con checkpoint existente se conserva la copia, sin descargar todo
otra vez. Si falla el primer intento, las consultas siguen respondiendo
`CATALOG_NOT_READY`; corregir el acceso y reiniciar vuelve a intentar la carga.
No hay endpoint manual nuevo ni sincronización periódica. Los incrementos y la
recuperación automática corresponden a CAT-06/CAT-07. El caso de uso de reemplazo
queda disponible internamente para esa recuperación.

## Consultas

| Método | Ruta | Resultado |
| --- | --- | --- |
| GET | `/api/professionals/{id}` | Detalle del profesional, incluido su estado habilitado. |
| GET | `/api/professionals/{id}/schedule` | Versión aplicada y horarios semanales habilitados. |

Ambas requieren JWT Bearer. Usan únicamente la última versión aplicada localmente.
Sin una sincronización inicial responden `503 CATALOG_NOT_READY`. La agenda
semanal no representa turnos libres: reservas combinará
horarios, fecha y ocupaciones. La búsqueda con filtros corresponde a CAT-08.

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
