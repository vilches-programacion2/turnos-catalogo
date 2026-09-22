# Backlog inicial

Las claves son referencias estables, no números de issue de GitHub.

## CAT-01 — Inicializar servicio de catálogo con Java y Spring Boot

Hito: **Base y contratos** · Etiquetas: `infraestructura`, `prioridad:alta`

<!-- backlog:CAT-01 -->
## Objetivo
Inicializar servicio de catálogo con Java y Spring Boot.

## Criterios de aceptación
- [ ] El servicio compila e inicia con configuración externalizada y .env.example sin secretos.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- Sin dependencias previas.

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.

## Logging obligatorio
- [ ] Configurar Lombok y usar `@Slf4j` en el backend.
- [ ] Verificar logs de inicio y errores con niveles configurables mediante Spring Boot y sin secretos.


## CAT-02 — Crear persistencia y migraciones del catálogo

Hito: **Base y contratos** · Etiquetas: `infraestructura`, `prioridad:alta`

<!-- backlog:CAT-02 -->
## Objetivo
Crear persistencia y migraciones del catálogo.

## Criterios de aceptación
- [ ] Categorías, profesionales, horarios y versión se persisten con migraciones independientes en una base servidor; no se permiten accesos cruzados.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- CAT-01

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## CAT-03 — Definir contratos de consulta y seguridad JWT

Hito: **Base y contratos** · Etiquetas: `funcionalidad`, `prioridad:alta`

<!-- backlog:CAT-03 -->
## Objetivo
Definir contratos de consulta y seguridad JWT.

## Criterios de aceptación
- [ ] Documentar operaciones, DTO y errores para app y reservas; validar firma, vigencia y permisos del JWT. Distinguir el filtro local de disponibilidad del cálculo de turnos concretos libres.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- CAT-01

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## CAT-04 — Configurar autenticación técnica e integración con cátedra

Hito: **Base y contratos** · Etiquetas: `funcionalidad`, `prioridad:alta`, `bloqueado`

<!-- backlog:CAT-04 -->
## Objetivo
Configurar autenticación técnica e integración con cátedra.

## Criterios de aceptación
- [ ] Autenticar la cuenta técnica y recuperar configuración según el anexo; ninguna credencial llega a Android ni a logs. Documentar el registro único de la cuenta del proyecto.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- CAT-01
- **Bloqueado:** falta el anexo técnico oficial para concretar contratos o validaciones externas. No inventar endpoints, mensajes ni reglas.

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## CAT-05 — Implementar sincronización completa por snapshot

Hito: **Catálogo y búsqueda** · Etiquetas: `funcionalidad`, `prioridad:alta`, `bloqueado`

<!-- backlog:CAT-05 -->
## Objetivo
Implementar sincronización completa por snapshot.

## Criterios de aceptación
- [ ] Inicializar una base vacía; aplicar snapshot y su versión consistentemente, sin dejar datos parciales ante un fallo.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- CAT-02
- CAT-04
- **Bloqueado:** falta el anexo técnico oficial para concretar contratos o validaciones externas. No inventar endpoints, mensajes ni reglas.

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## CAT-06 — Implementar sincronización incremental con Kafka y Redis

Hito: **Catálogo y búsqueda** · Etiquetas: `funcionalidad`, `prioridad:alta`, `bloqueado`

<!-- backlog:CAT-06 -->
## Objetivo
Implementar sincronización incremental con Kafka y Redis.

## Criterios de aceptación
- [ ] Ante cambios de cátedra descargar SOLO los cambios posteriores a la versión local, localizar cada entidad por su identificador y aplicar altas, modificaciones o bajas según el contrato. Kafka notifica y Redis aporta metadata e incrementales. Aplicar en orden, sin descargar un snapshot completo por cada evento; avanzar versión solo al persistir correctamente. Verificar con un cambio aislado que entidades no afectadas permanecen intactas y que duplicados no alteran el resultado.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- CAT-05
- **Bloqueado:** falta el anexo técnico oficial para concretar contratos o validaciones externas. No inventar endpoints, mensajes ni reglas.

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## CAT-07 — Recuperar sincronización ante discontinuidades y desconexiones

Hito: **Catálogo y búsqueda** · Etiquetas: `funcionalidad`, `prioridad:alta`, `bloqueado`

<!-- backlog:CAT-07 -->
## Objetivo
Recuperar sincronización ante discontinuidades y desconexiones.

## Criterios de aceptación
- [ ] Tras estar offline, recuperar solo los cambios pendientes si el historial aún cubre la versión local. Una desconexión larga NO obliga por sí sola a descargar todo: hacerlo cuando los cambios necesarios ya no están disponibles o una discontinuidad impide reconstruir de forma segura. Entonces obtener snapshot completo DEL CATÁLOGO, nunca reemplazar usuarios o reservas. Probar offline corto y largo, conservar consistencia y limitar reintentos.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- CAT-06
- **Bloqueado:** falta el anexo técnico oficial para concretar contratos o validaciones externas. No inventar endpoints, mensajes ni reglas.

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## CAT-08 — Implementar búsqueda local de profesionales

Hito: **Catálogo y búsqueda** · Etiquetas: `funcionalidad`, `prioridad:alta`

<!-- backlog:CAT-08 -->
## Objetivo
Implementar búsqueda local de profesionales.

## Criterios de aceptación
- [ ] Ofrecer filtros combinables por categoría, nombre, estado habilitado y disponibilidad usando exclusivamente la copia local; manejar referencias inválidas y resultados vacíos.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- CAT-03
- CAT-05

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## CAT-09 — Exponer estado y errores de sincronización

Hito: **Catálogo y búsqueda** · Etiquetas: `funcionalidad`, `prioridad:alta`

<!-- backlog:CAT-09 -->
## Objetivo
Exponer estado y errores de sincronización.

## Criterios de aceptación
- [ ] Consultar versión aplicada, estado y último error mediante endpoint protegido, sin revelar secretos.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- CAT-03
- CAT-05

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## CAT-10 — Agregar pruebas automatizadas del catálogo

Hito: **Robustez y entrega** · Etiquetas: `pruebas`, `prioridad:media`

<!-- backlog:CAT-10 -->
## Objetivo
Agregar pruebas automatizadas del catálogo.

## Criterios de aceptación
- [ ] Cubrir snapshot, incrementales, duplicados, discontinuidades, filtros y JWT inválido, ausente o vencido mediante pruebas unitarias, de integración y de contratos pertinentes.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- CAT-07
- CAT-08
- CAT-09

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## CAT-11 — Documentar modelo de datos y sincronización

Hito: **Robustez y entrega** · Etiquetas: `documentacion`, `prioridad:media`

<!-- backlog:CAT-11 -->
## Objetivo
Documentar modelo de datos y sincronización.

## Criterios de aceptación
- [ ] Documentar propiedad de datos, contratos, ejecución reproducible, pruebas y recuperación; reflejar el comportamiento implementado.

## Dependencias
- CAT-10

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.
