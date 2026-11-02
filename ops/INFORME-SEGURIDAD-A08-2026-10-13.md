# SIGAA: A08, seguridad general y despliegue — 13 de octubre de 2026

**Evaluación: NO RECOMENDADO PARA PRODUCCIÓN.** El endurecimiento de A08 está implementado y probado en PostgreSQL aislado;
todavía requiere aplicación y comprobación en el despliegue real. Hay una cuenta inicial predefinida pendiente de verificar
y un diagnóstico adicional fallido de reutilización de token después de logout.

No se hicieron commits, push, cambios de credenciales reales, restauraciones institucionales ni cambios del servidor.
No se abrió `.env`, ni se usaron datos, uploads, documentos o backups reales.

## 1. Configuración PostgreSQL encontrada

Compose de producción utiliza PostgreSQL 18. La API recibía como DB_USERNAME el POSTGRES_USER de inicialización
y compartía esa conexión con Flyway y respaldos. El Dockerfile de API también utiliza la imagen PostgreSQL para disponer
de clientes pg_dump/pg_restore compatibles. La configuración nueva separa las tres conexiones sin nuevas dependencias.

No se consultó la base institucional. Los privilegios efectivos del servidor existente permanecen pendientes de inventario;
la validación equivalente se hizo con PostgreSQL **18.4**, un cluster nuevo y datos exclusivamente ficticios.

## 2. Privilegios anteriores

El diseño versionado reutilizaba la cuenta administradora de inicialización durante el funcionamiento habitual.
En una inicialización normal esa cuenta tiene privilegios administrativos. No se afirma que los flags del servidor existente
se hayan medido o que sigan coincidiendo con el diseño original. API y web no tenían USER en sus imágenes finales.

## 3. Privilegios finalmente necesarios

| Conexión | Permisos y límites preparados |
| --- | --- |
| sigaa_runtime | CONNECT, USAGE de public, SELECT/INSERT/UPDATE/DELETE de tablas de negocio, USAGE/SELECT de secuencias, EXECUTE de funciones SECURITY INVOKER. Sin DDL, TEMP, propiedad, membresías, SUPERUSER, CREATEDB, CREATEROLE ni BYPASSRLS. Sin acceso al historial Flyway. |
| sigaa_migrator | Propietario de la base dedicada y objetos SIGAA, DDL local y administración del historial Flyway. Sin privilegios administrativos globales, CREATEDB ni membresías. |
| sigaa_backup | CONNECT y SELECT de tablas/secuencias, incluido el historial necesario para el dump. Sin escritura en SIGAA, SUPERUSER, CREATEROLE ni BYPASSRLS. CREATEDB para la restauración temporal existente. |
| Inicializador | Cuenta administrativa usada para preparar PostgreSQL; no se inyecta en la API. |

CREATEDB no puede restringirse a un prefijo mediante ese atributo de PostgreSQL. El rol de backup conserva esta capacidad
global por la operación legítima existente; no puede eliminar SIGAA porque no es su propietario.
El servicio restringe el nombre de las bases que elimina y revoca CONNECT de PUBLIC en cada restauración temporal.
Las funciones SECURITY DEFINER no se conceden automáticamente al runtime.

## 4. Cambios realizados

Se añadieron provisionamiento de roles, inventario de metadatos y callback afterMigrate de producción.
El callback retira grants previos de runtime/backup y aplica los necesarios a objetos actuales y nuevos, excluyendo
flyway_schema_history del runtime. No se modificaron las migraciones históricas ni sus checksums.

Una guardia de arranque prod rechaza flags administrativos, membresías, propiedad de base/objetos o capacidad de DDL/TEMP.
Se comprueba la propiedad de cualquier base, incluso si el propietario revocó sus privilegios CREATE/TEMP explícitos.
BackupService admite su conexión independiente y administra bases temporales conectándose a SIGAA, sin necesitar CONNECT a postgres.

Los roles se provisionan automáticamente solo al inicializar un volumen PostgreSQL nuevo. Los existentes requieren
un procedimiento del operador con inventario y mantenimiento. Las contraseñas de roles existentes nunca se cambian por el script.

En la validación se reprodujeron y corrigieron errores reales de parámetros/métodos que devolvían 500.
IDs/fechas/JSON/parámetros ausentes ahora reciben 400 y métodos no admitidos conservan 405 y Allow. Los ErrorResponse 4xx de Spring conservan su estado (incluidos 415/406/410) sin publicar detalles internos.

## 5. Usuario del contenedor

API y web declaran **USER 10001:10001**. Caddy escucha en 8080 dentro del contenedor para evitar permisos de puerto privilegiado;
el puerto externo por defecto sigue siendo 8088 en loopback. No se instaló Docker ni se arrancaron contenedores en este equipo.
Estos son valores preparados en las imágenes, no mediciones de un contenedor desplegado.

## 6. Permisos y capabilities

API/web usan read_only, cap_drop ALL y no-new-privileges. Los directorios persistentes de API se preparan con propietario
10001:10001 y modo 0750. /tmp se monta como tmpfs privado 0700 del mismo usuario, limitado a 512 MB, nosuid/nodev.
No se concedió 777 ni se añadieron montajes sensibles del host. Caddy tiene el endpoint administrativo deshabilitado.

La base conserva el entrypoint oficial para inicialización y ejecución PostgreSQL. Los permisos efectivos de imágenes,
procesos LibreOffice y montajes existentes deben comprobarse en el despliegue de prueba.

## 7. Volúmenes y carpetas escribibles

| Servicio | Ubicaciones |
| --- | --- |
| API | uploads_data en /app/uploads; backups_data en /var/lib/sigaa/backups; copia externa en /var/lib/sigaa/backups-external; temporal privado /tmp. |
| Web | tmpfs privados 0750 en /data y /config. Código y assets de solo lectura. |
| PostgreSQL | postgres_data, usado únicamente por db. |
| Fuentes | Directorio del operador montado de solo lectura; debe contener Verdana autorizada y legible por UID 10001. |

No se movieron archivos ni se cambiaron permisos de volúmenes reales. Los volúmenes anteriores y el montaje externo requieren
revisión/preparación del operador. La persistencia y el reinicio de contenedores aún no se probaron.

## 8. Revisión de secretos

Las configuraciones nuevas contienen referencias de entorno, sin credenciales reales. Se retiró el env_file amplio de la API
para no inyectarle la credencial administradora ni variables ajenas. Se conservaron explícitamente correo, rate limit,
programación de reportes, respaldos, zona horaria y configuración de sesión utilizada por el despliegue.

Se añadieron .dockerignore a ambos contextos: excluyen .env, archivos de claves, uploads, backups, logs y salidas de pruebas/build.
En frontend también evitan sobrescribir npm ci con node_modules del host. No se realizó una inspección de secretos reales.

La migración inicial contiene una cuenta con credencial predefinida. No se utilizó su contraseña/hash para pruebas ni se
reproduce aquí. Las copias de V1 utilizadas en la fixture sustituyen completamente esa cuenta por una ficticia y conservan el DDL.
Esto valida la estructura/migraciones, sin certificar el uso seguro de la semilla administrativa original en producción.

## 9. Red y puertos

Compose no publica PostgreSQL, API ni el puerto de gestión. Prometheus/Alertmanager tampoco tienen puertos publicados.
Web publica por defecto 127.0.0.1:8088 y conserva la arquitectura de proxy institucional/VPN.
La red bridge permite comunicación interna y salida necesaria para SMTP; no es una red sin salida.

La terminación HTTPS/certificados del proxy institucional no se verificó. No se añadió un certificado ficticio ni se expuso
la API para simular HTTPS. Overrides del operador y puertos reales quedan pendientes de inspección.

## 10. Actuator y seguridad web

En el entorno aislado, con SMTP simulado fallando:

| Endpoint | Resultado probado |
| --- | --- |
| Gestión /actuator/health | 503 / DOWN, sin componentes, excepciones ni credenciales en la respuesta. |
| Aplicación /livez | 200 / UP. |
| Aplicación /readyz | 200 / UP. |
| Gestión health/liveness y health/readiness | 200 / UP. |
| Aplicación /actuator/health | 404, al estar publicado exclusivamente en el puerto de gestión. |

La prueba también confirma recuperación del health global cuando el SMTP simulado vuelve a responder.
Se mantuvo la decisión de dejar SMTP pendiente y conservar DOWN; no se desactivó su indicador.
No se consultó el health de la instancia institucional durante esta validación.

Se conservaron HSTS, nosniff, frame DENY y Referrer-Policy; se añadió CSP compatible en código con assets locales y previews blob.
Se habilitó geolocation self para la ubicación legítima del módulo QR. Styles unsafe-inline permite los estilos dinámicos Ionic.
Cookies prod conservan Secure/HttpOnly/SameSite; se probó rechazo de un origen CORS no autorizado.
CSRF sigue deshabilitado en la arquitectura actual, con cookies Strict y CORS explícito como controles presentes.
No se acredita un análisis completo de CSRF ni la navegación móvil con el proxy real; requieren comprobación operativa.

## 11. Resultados generales de seguridad

Suite backend: **954 casos registrados, 952 aprobados, 0 errores/fallos y 2 omitidos**.
Las dos omisiones son conversiones reales LibreOffice de Carta/paginación y marca de anulación; falta el entorno requerido.
Después del ajuste de propiedad de base se repitieron 5 casos de privilegios/arranque prod, todos aprobados. También se repitieron 10 casos de errores/seguridad general, incluido un caso nuevo de estados 415/406/410; todos aprobados.

Se probaron acceso anónimo, sesión/contraseña temporal expirada, permisos funcionales y de recurso, recuperación concurrente,
consumo único, cambios de contraseña/revocación, archivos privados, rutas/nombres/tipos, inexistentes, payloads inválidos,
fechas/IDs/longitudes/valores y respuestas sin detalles internos. La matriz de roles cubre 262 casos con usuarios ficticios persistidos.
Las pruebas antiguas dependientes de la cuenta inicial o de una sesión QR sin propietario se ajustaron a fixtures propias,
sin relajar autorizaciones, y se añadió rechazo de resultados 500 a la matriz.

**Diagnóstico adicional: 1 prueba fallida de reutilización tras logout.** GET con sesión válida da 200; logout devuelve 204 y
Max-Age=0; repetir GET con el token anterior da 200 cuando la expectativa de revocación es 401.
La firma/versión válidas están simuladas; se ejecutan la cadena de autorización y controlador reales. No hubo tokens reales.
Este resultado no se cuenta como seguridad aprobada. El reproductor se conserva fuera de la suite ordinaria, con explicación
en [diagnóstico logout](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/ops/security-diagnostics/README.md).

Frontend: **239 pruebas aprobadas**, lint y TypeScript app/spec correctos. Cobertura de líneas **54,61%**.
Compilación production aprobada; advertencia previa SCSS Reuniones 31,32 kB frente al límite de advertencia de 27 kB.
No se cambiaron páginas Angular ni controles de autenticación como parte de A08.

Backend: compilación/empaquetado correctos, cobertura nueva **65.88%**, umbral 40% aprobado sin bajar requisitos.
La primera comprobación JaCoCo leyó un archivo previo distinto; se corrigió la entrada a la cobertura fresca de esta ejecución
y se repitió verify. El verify final de los 5 casos de A08/arranque también terminó BUILD SUCCESS.

## 12. Revalidación A01–A08

| Hallazgo | Evidencia y estado |
| --- | --- |
| A01 archivos privados | 15 casos específicos aprobados, además de tests de Documentos/archivos. URL física no concede acceso; fotos públicas preservadas. |
| A02 Actas | 31 casos aprobados: permiso funcional más acceso al recurso en edición/eliminación y acciones sensibles. |
| IDOR QR | 37 casos aprobados: sesión propia/alcance, códigos/IDs ajenos, invitados/asistentes y privilegios explícitos. |
| A03 contraseña temporal | 26 casos aprobados con Bearer/cookie: bloqueo, expiración, rollback y revocación al cambio de contraseña. Logout es un hallazgo separado pendiente. |
| A04 Horarios | 28 casos aprobados: DTO mínimo y ausencia de campos sensibles. |
| A05 recuperación | 18 casos PostgreSQL reales y aislados: carreras, bloqueos, consumo único, vencimiento, rollback y callbacks. |
| A06/A07 backups/procesos | 23 resiliencia + 4 scheduler + 1 integración nativa aprobados; además dump/restauración con sigaa_backup en A08. |
| A08 mínimo privilegio | 4 integraciones nativas y arranque prod aprobados. No cerrado en producción: faltan contenedores, volúmenes y conversión documental real. |

## 13. Pruebas del despliegue equivalente

115 migraciones aplicadas hasta V117 con sigaa_migrator; validación JPA/arranque prod con sigaa_runtime;
SCRAM rechaza contraseña ficticia incorrecta; CRUD/secuencias/funciones autorizadas funcionan;
DDL, administración, TRUNCATE, historial Flyway y función SECURITY DEFINER quedan rechazados.
La guardia rechaza SUPERUSER/propietario/CREATEDB/membresías.

Como sigaa_backup: escritura/eliminación de la base fuente rechazadas; dump nativo, cifrado, igualdad de copia externa,
restauración temporal, verificación y eliminación final aprobados. Runtime no puede conectarse a esa base temporal.
Reprovisionar roles existentes con otras credenciales ficticias no cambia sus contraseñas.

Se probaron archivos y documentos generados con fixtures, notificaciones internas y correos mediante mocks/callbacks.
No se enviaron correos reales. No se ejecutó LibreOffice real en un contenedor ni se acreditó Verdana.

YAML Compose/CI y sintaxis PowerShell/shell correctos; campos de hardening/puertos comprobados estructuralmente.
Readiness script: fixture válida pasa, credenciales repetidas fallan sin imprimirlas. Eso valida configuración, no el despliegue.
Docker build/config/runtime, reinicio y volúmenes quedan pendientes porque Docker/WSL no están disponibles.
La CI está preparada pero no se ejecutó ni se publicó desde esta sesión.

## 14. Archivos modificados o añadidos

- [.github/workflows/ci.yml](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/.github/workflows/ci.yml)
- [backend/sigaa-api/Dockerfile](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/Dockerfile)
- [backend/sigaa-api/pom.xml](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/pom.xml)
- [backend/sigaa-api/src/main/java/com/sigaa/config/GlobalExceptionHandler.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/main/java/com/sigaa/config/GlobalExceptionHandler.java)
- [backend/sigaa-api/src/main/java/com/sigaa/service/BackupService.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/main/java/com/sigaa/service/BackupService.java)
- [backend/sigaa-api/src/main/resources/application-prod.properties](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/main/resources/application-prod.properties)
- [backend/sigaa-api/src/test/java/com/sigaa/CodigoConcurrenciaTest.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/test/java/com/sigaa/CodigoConcurrenciaTest.java)
- [backend/sigaa-api/src/test/java/com/sigaa/RoleEndpointAuthorizationIntegrationTests.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/test/java/com/sigaa/RoleEndpointAuthorizationIntegrationTests.java)
- [backend/sigaa-api/src/test/java/com/sigaa/config/GlobalExceptionHandlerTest.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/test/java/com/sigaa/config/GlobalExceptionHandlerTest.java)
- [backend/sigaa-api/src/test/java/com/sigaa/service/BackupPostgresIntegrationTest.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/test/java/com/sigaa/service/BackupPostgresIntegrationTest.java)
- [backend/sigaa-api/src/test/java/com/sigaa/service/PasswordResetConcurrencyIntegrationTest.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/test/java/com/sigaa/service/PasswordResetConcurrencyIntegrationTest.java)
- [docker-compose.prod.yml](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/docker-compose.prod.yml)
- [frontend/Caddyfile](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/frontend/Caddyfile)
- [frontend/Dockerfile](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/frontend/Dockerfile)
- [ops/scripts/Test-ProductionReadiness.ps1](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/ops/scripts/Test-ProductionReadiness.ps1)
- [.gitattributes](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/.gitattributes)
- [backend/sigaa-api/.dockerignore](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/.dockerignore)
- [backend/sigaa-api/src/main/java/com/sigaa/config/RuntimeDatabasePrivilegeGuard.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/main/java/com/sigaa/config/RuntimeDatabasePrivilegeGuard.java)
- [backend/sigaa-api/src/main/resources/db/production/afterMigrate__least_privilege.sql](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/main/resources/db/production/afterMigrate__least_privilege.sql)
- [backend/sigaa-api/src/test/java/com/sigaa/ProductionProfileSecurityIntegrationTest.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/test/java/com/sigaa/ProductionProfileSecurityIntegrationTest.java)
- [backend/sigaa-api/src/test/java/com/sigaa/controller/GeneralSecurityValidationTest.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/test/java/com/sigaa/controller/GeneralSecurityValidationTest.java)
- [backend/sigaa-api/src/test/java/com/sigaa/service/IsolatedSecuritySuiteExtension.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/test/java/com/sigaa/service/IsolatedSecuritySuiteExtension.java)
- [backend/sigaa-api/src/test/java/com/sigaa/service/LeastPrivilegePostgresFixture.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/test/java/com/sigaa/service/LeastPrivilegePostgresFixture.java)
- [backend/sigaa-api/src/test/java/com/sigaa/service/RuntimeDatabasePrivilegeIntegrationTest.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/test/java/com/sigaa/service/RuntimeDatabasePrivilegeIntegrationTest.java)
- [backend/sigaa-api/src/test/resources/META-INF/services/org.junit.jupiter.api.extension.Extension](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/test/resources/META-INF/services/org.junit.jupiter.api.extension.Extension)
- [frontend/.dockerignore](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/frontend/.dockerignore)
- [ops/A08-DESPLIEGUE.md](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/ops/A08-DESPLIEGUE.md)
- [ops/INFORME-SEGURIDAD-A08-2026-10-13.md](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/ops/INFORME-SEGURIDAD-A08-2026-10-13.md)
- [ops/postgres/010-sigaa-roles.sh](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/ops/postgres/010-sigaa-roles.sh)
- [ops/postgres/inspect-privileges.sql](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/ops/postgres/inspect-privileges.sql)
- [ops/postgres/provision-roles.sql](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/ops/postgres/provision-roles.sql)
- [ops/scripts/Test-ProductionContainer.sh](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/ops/scripts/Test-ProductionContainer.sh)
- [ops/security-diagnostics/LogoutReplayDiagnosticTest.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/ops/security-diagnostics/LogoutReplayDiagnosticTest.java)
- [ops/security-diagnostics/README.md](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/ops/security-diagnostics/README.md)

## 15. Pruebas pendientes y motivos

- Inventario PostgreSQL real, aplicación a roles/volúmenes existentes y comprobación de sus permisos: no se usaron credenciales ni base institucional.
- Build/ejecución de Docker, UID/capabilities/readonly efectivos, reinicio y persistencia: Docker/WSL no disponibles.
- LibreOffice real y Verdana en la imagen: entorno/fuentes autorizadas no disponibles; no se descargaron ni sustituyeron fuentes.
- HTTPS/HSTS/CSP efectivos, interfaz Angular/Ionic y cookies detrás del proxy: falta entorno de despliegue de prueba.
- SMTP, recepción de correos y alertas reales: pendientes por decisión del usuario; solo mocks y callbacks verificados.
- Estado de la cuenta administrativa inicial, políticas CORS persistidas y logs reales: fuera del acceso seguro utilizado.
- CVE/dependencias actuales y APK/dispositivo físico: no se consultaron servicios externos ni se realizó esa certificación.

## 16. Hallazgos adicionales y pendientes

| Prioridad | Hallazgo | Acción |
| --- | --- | --- |
| P0 condicionado al despliegue | Cuenta inicial con credencial predefinida en la migración histórica. Una instalación nueva no debe publicarse con esa cuenta utilizable. Estado real no verificado. | Administrador debe confirmar cambio/deshabilitación antes de producción, preservando migraciones históricas. |
| P1 | Logout borra la cookie, pero no revoca el token anterior: reproducción obtiene 200 tras logout. | Definir revocación por sesión o global y corregir con el mecanismo existente; no se cambió dentro de A08. |
| P1 operativo existente | SMTP pendiente; recuperación y avisos por correo requieren un servicio configurado. | Conservar DOWN y configurar/verificar en entorno autorizado. |
| P2 | Evidencia de contenedores, permisos de montajes, Verdana/TLS/CSP móvil y configuración efectiva todavía incompleta. | Ejecutar checklist aislado del runbook; no interpretar un arranque como cierre de A08. |
| P2 residual documentado | CREATEDB del backup es global, aunque la aplicación acota los nombres y elimina únicamente su base temporal. | Mantener credencial separada; evaluar aislamiento administrativo adicional solo si la política lo exige. |
| P3 existente | Advertencia de tamaño SCSS de Reuniones. | Mejora posterior fuera de este alcance. |

No se cambiaron la política de logout, la cuenta inicial, datos históricos ni reglas funcionales de Notas.
La ausencia previa de dockerignore y los 500 de parámetros/métodos sí se corrigieron por pertenecer a despliegue/validación.

## 17. Evaluación final y cierre

**NO RECOMENDADO PARA PRODUCCIÓN.** No se considera cerrada la seguridad general ni A08 en el servidor real.
Las operaciones PostgreSQL legítimas y el mínimo privilegio están demostrados en el entorno aislado.
Los archivos de despliegue y el procedimiento están preparados para revisión/aplicación controlada.

Antes de publicar: verificar la cuenta inicial, resolver la revocación pendiente según política, aplicar roles y permisos revisados,
ejecutar contenedores con volúmenes de prueba, comprobar documentos/Verdana, TLS/CSP/cookies y los servicios operativos necesarios.
Procedimiento: [A08-DESPLIEGUE](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/ops/A08-DESPLIEGUE.md).
