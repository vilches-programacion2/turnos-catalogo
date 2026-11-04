# turnos-catalogo

Servicio de catálogo y sincronización: Java 25, Spring Boot 4.1.0 y PostgreSQL 17.
Paquete base: `catalogo`, con la misma organización hexagonal por funcionalidad
que reservas. CAT-01 prepara arranque, configuración, logs y salud. CAT-02 agrega
persistencia de categorías, profesionales, horarios y versión con Flyway.
CAT-03 expone consultas locales protegidas con JWT RS256. La sincronización
con cátedra se implementará en sus issues.

## Ejecutar

Con Docker Compose:

```bash
cp .env.example .env
# Configurar DB_PASSWORD en .env.
mkdir -p secrets
# Copiar la clave pública de reservas a secrets/public.pem.
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
y `ROLE_USER`; la emisión compatible desde reservas se completará en RES-05.

## Consultas

| Método | Ruta | Resultado |
| --- | --- | --- |
| GET | `/api/professionals/{id}` | Detalle del profesional, incluido su estado habilitado. |
| GET | `/api/professionals/{id}/schedule` | Versión aplicada y horarios semanales habilitados. |

Ambas requieren JWT Bearer. Usan únicamente la última versión aplicada localmente.
Sin una sincronización inicial responden `503 CATALOG_NOT_READY`; CAT-05 la
implementará. La agenda semanal no representa turnos libres: reservas combinará
horarios, fecha y ocupaciones. La búsqueda con filtros corresponde a CAT-08.

## Verificar

Con JDK 25 y Docker activos:

```bash
./mvnw verify
```

Las pruebas usan PostgreSQL real con Testcontainers: persistencia, transacciones,
salud, contrato de consultas y rechazo de JWT o permisos inválidos. Los GET de
salud siguen siendo públicos.

El wrapper incluido utiliza Maven 3.9.11; requiere JDK 25.

Una rama por issue, con PR a `dev`. Promociones a `test` y `main` mediante PR.
Licencia [GPL-3.0](LICENSE).
