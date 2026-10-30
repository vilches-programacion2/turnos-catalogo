# turnos-catalogo

Servicio de catálogo y sincronización: Java 25, Spring Boot 4.1.0 y PostgreSQL 17.
Paquete base: `catalogo`, con la misma organización hexagonal por funcionalidad
que reservas. CAT-01 prepara arranque, configuración, logs y salud. Persistencia
del catálogo, consultas protegidas y sincronización se implementarán en sus issues.

## Ejecutar

Con Docker Compose:

```bash
cp .env.example .env
# Configurar DB_PASSWORD en .env.
docker compose up --build -d
curl --fail http://localhost:8082/actuator/health/readiness
docker compose down
```

PostgreSQL se publica solo en localhost, puerto `5434`, y el servicio en `8082`.
Ambos tienen recursos propios, separados de reservas. El volumen conserva los
datos al detener los contenedores. Configuración en [.env.example](.env.example).
Al ejecutar Java en el host, proporcionar las variables de entorno; Spring no
carga `.env` automáticamente.

## Verificar

Con JDK 25 y Docker activos:

```bash
./mvnw verify
```

La prueba usa PostgreSQL real con Testcontainers: arranque, log de inicio,
endpoints cerrados y readiness/liveness ante caída de la base. Por ahora solo
los GET de salud son públicos; JWT y consultas corresponden a CAT-03.

Entorno Java aislado opcional, igual al de reservas:

```bash
./scripts/setup-java-env.sh /ruta/al/jdk-25
./mvnw-local verify
```

Una rama por issue, con PR a `dev`. Promociones a `test` y `main` mediante PR.
Licencia [GPL-3.0](LICENSE).
