# SIGAA

Sistema Integral de Gestión Alerta Amber. SIGAA centraliza la gestión institucional de usuarios, reuniones, actas, tareas, documentos, grupos, asistencia, inventario, reportes y configuración administrativa.

Documentación actualizada el **31 de octubre de 2026**. La revisión de código anterior
y sus validaciones se conservan en
[Revisión de código del 26/10/2026](docs/REVISION_CODIGO_2026-10-26.md).

**Correcciones de preparación para producción del 1/11/2026:** se implementaron
los cambios de sesión, dependencias, APK, proxy, horarios, respaldos y operación.
La aceptación en el servidor institucional y teléfonos físicos sigue pendiente.
Consulte el [registro vigente de correcciones y pruebas](docs/CORRECCIONES_PRODUCCION_2026-11-01.md)
y la [guía de servidor y mantenimiento](docs/GUIA_SERVIDOR_Y_MANTENIMIENTO_2026-11-01.md),
preparada para unas 20 personas con acceso por red institucional o VPN.

La limpieza de referencias y la preparación del APK del 31 de octubre están
documentadas en [Limpieza del 31/10/2026](docs/LIMPIEZA_2026-10-31.md).

## Tecnología

| Capa | Tecnología |
| --- | --- |
| Frontend | Angular 20, Ionic 8, TypeScript y SCSS |
| Backend | Spring Boot 4, Java 21 y Maven |
| Datos | PostgreSQL 18 y esquema SQL canónico |
| Seguridad | Spring Security, JWT, BCrypt, 2FA y políticas configurables |
| Integraciones UI | FullCalendar, ApexCharts, QR, jsPDF y geolocalización |

## Requisitos

- JDK 21.
- Node.js 22.12 o superior de la rama 22.x, o Node.js 24.x, compatibles con Angular 20 y Capacitor 8.
- PostgreSQL 15 o superior.

## Configuración local

1. Copie `.env.example` como `.env` en la raíz del repositorio.
2. Configure al menos `DB_PASSWORD` y `JWT_SECRET`. El secreto JWT debe tener 32 caracteres o más.
3. Cree una base PostgreSQL vacía y ejecute una sola vez
   `backend/sigaa-api/src/main/resources/db/schema.sql`.

Ejemplo de estructura; sustituya los marcadores por valores locales y no publique
credenciales reales:

```properties
DB_URL=jdbc:postgresql://localhost:5432/sigaa-database
DB_USERNAME=<USUARIO_LOCAL_POSTGRESQL>
DB_PASSWORD=<CLAVE_LOCAL_POSTGRESQL>
JWT_SECRET=<VALOR_ALEATORIO_DE_32_O_MAS_CARACTERES>
```

### Backend: desarrollo local y red local

Desde la raíz del repositorio, abra una terminal de PowerShell:

```powershell
cd backend/sigaa-api
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

La API queda disponible por defecto en `http://localhost:8080`. Hibernate valida
el esquema, pero la API no crea ni modifica tablas. Espere el mensaje
`Started SigaaApiApplication` y mantenga esta terminal abierta. `BUILD SUCCESS`
por sí solo no confirma que Spring haya completado el arranque: con DevTools la
aplicación puede fallar durante la inicialización.

Use el perfil `dev` también cuando acceda desde otro equipo de la red. No se
activa automáticamente por ejecutar Angular en modo desarrollo.

### CORS: web, red local y móvil

CORS se configura únicamente desde el backend mediante `app.cors.*` y variables
del ambiente. La interfaz administrativa y los valores históricos de BD no
modifican la configuración efectiva.

El perfil `dev`, definido en
`backend/sigaa-api/src/main/resources/application-dev.properties`, admite los
orígenes locales de Angular/Ionic y Capacitor. Para utilizar una IP de la red,
configure `CORS_ALLOWED_ORIGINS` en el `.env` de la raíz o en el entorno del proceso.
La variable sustituye la lista completa; incluya todos los orígenes necesarios.

```properties
CORS_ALLOWED_ORIGINS=http://192.168.1.50:4200,http://localhost:4200,https://localhost:4200,http://localhost:8100,https://localhost:8100,http://localhost,https://localhost,capacitor://localhost
```

`192.168.1.50` es solo un ejemplo: reemplácela por la IP del equipo que sirve el
frontend. Cada origen debe incluir esquema, host y puerto cuando corresponda,
sin rutas como `/home`, sin barra final y sin comillas alrededor de la lista.
Se conservan credenciales/cookies; no utilice `*` como origen con credenciales.
Reinicie el backend después de cambiar esta configuración.

Los métodos, encabezados, credenciales y duración del preflight se configuran con
`CORS_ALLOWED_METHODS`, `CORS_ALLOWED_HEADERS`, `CORS_EXPOSED_HEADERS`,
`CORS_ALLOW_CREDENTIALS` y `CORS_MAX_AGE`. Consulte
[CORS por ambiente](docs/CORS_POR_AMBIENTE.md) para sus valores y validaciones.

### Salud y puerto de gestión

Actuator escucha por defecto en `127.0.0.1:8081`, separado de la API. El puerto y
la dirección se pueden configurar con `MANAGEMENT_PORT` y `MANAGEMENT_BIND_ADDRESS`.

| Consulta local | Componentes comprobados |
| --- | --- |
| `http://127.0.0.1:8081/actuator/health` | Todos los indicadores registrados, incluido SMTP |
| `http://localhost:8080/livez` | Estado de liveness y ping |
| `http://localhost:8080/readyz` | Estado de readiness, PostgreSQL y espacio en disco |

Los grupos también están disponibles como `/actuator/health/liveness` y
`/actuator/health/readiness` en el puerto de gestión. `/actuator/health` no está
publicado en el puerto de la API; consultarlo allí devuelve 404.

El health general puede devolver 503/`DOWN` aunque los probes devuelvan 200/`UP`:
SMTP no forma parte de esos grupos. Con la configuración actual, un `MAIL_HOST`
vacío sigue creando el componente de correo y su indicador falla al comprobar
una conexión sin credenciales. Configure el servidor previsto mediante
`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH` y
`MAIL_SMTP_STARTTLS` en la configuración local; los valores deben corresponder
al servidor utilizado. Mientras SMTP esté pendiente, mantenga ese `DOWN` como
señal real del servicio no disponible. No desactive el indicador para obtener
`UP`. Los detalles de salud permanecen ocultos y el puerto de gestión debe
mantenerse restringido.

### Frontend

Abra otra terminal desde la raíz del repositorio:

```powershell
cd frontend
npm.cmd ci
npm.cmd start
```

`npm.cmd ci` instala las dependencias del lockfile; no es necesario repetirlo en
cada arranque si no cambiaron. Para sesiones posteriores basta `npm.cmd start`.

La aplicación queda disponible en `http://localhost:4200` o
`http://IP_DEL_EQUIPO:4200` desde la red local. El script escucha en `0.0.0.0` y
utiliza `frontend/proxy.conf.json` para enviar `/api` a `http://localhost:8080`.
Mantenga ambas terminales abiertas. Si cambia la IP del equipo, actualice el origen
LAN en `CORS_ALLOWED_ORIGINS`; el teléfono no debe utilizar su propio `localhost`
como dirección de la API.

### Diagnóstico de arranque y archivos

| Síntoma | Qué revisar |
| --- | --- |
| `app.cors.allowed-origins requiere orígenes exactos sin rutas ni comodines` | Un origen tiene formato inválido. Revise la lista CORS y reinicie el backend. |
| Vite muestra `http proxy error` / `ECONNREFUSED` | La API no acepta conexiones en el destino del proxy. Confirme que Spring arrancó y que su puerto coincide con `proxy.conf.json`. |
| Maven muestra `BUILD SUCCESS`, pero no aparece `Started SigaaApiApplication` | Revise la excepción de inicialización de Spring; la terminación de Maven no acredita que la API esté disponible. |
| Tareas devuelve 404 al abrir un adjunto | Compruebe el vínculo tarea-adjunto y el archivo físico. El listado ahora verifica su existencia y marca como no disponible un archivo ausente; una copia de BD no reconstruye el original faltante. |

El backend utiliza archivos bajo `uploads/`, además de sus registros en PostgreSQL.
Inicie la API desde `backend/sigaa-api` para mantener la ubicación relativa prevista
y conserve el almacenamiento entre reinicios y despliegues. Un registro del adjunto
no permite reconstruir un archivo físico faltante. Si existe un respaldo o el
original, su recuperación debe realizarse de forma controlada; no borre referencias
ni amplíe permisos como solución al 404.

## Pruebas y compilación

```powershell
# Frontend
cd frontend
npm.cmd run build -- --configuration development
npm.cmd run lint
npm.cmd run test:ci

# Backend
cd ../backend/sigaa-api
# Ajuste esta ruta al directorio bin de PostgreSQL instalado.
$postgresTestBin = 'C:\ruta\PostgreSQL\bin'
.\mvnw.cmd '-Dsigaa.security.validation=true' `
  '-Djunit.jupiter.extensions.autodetection.enabled=true' `
  "-Dsigaa.test.postgres.bin=$postgresTestBin" `
  '-Dtest=*Test,*Tests,!ProductionProfileSecurityIntegrationTest' test
```

La validación aislada crea PostgreSQL temporal con datos ficticios dentro de
`backend/sigaa-api/target`, sin conectarse a la base habitual ni cargar `.env`.
La propiedad `sigaa.security.validation=true` activa también el perfil Maven
`isolated-security-validation`: compila en `target/security-build` para no
modificar las clases observadas por DevTools en una API que esté ejecutándose.
No ejecute pruebas de integración contra una base institucional.

El selector anterior reproduce el alcance de la revisión de código: excluye la
prueba que carga el perfil de producción. La verificación de ese perfil debe
realizarse por separado con configuración autorizada para ese ambiente. El perfil
aislado limita la caché a cuatro contextos Spring para evitar que sus pools agoten
las conexiones de PostgreSQL temporal.

`npm.cmd run test:ci` ejecuta Jasmine/Karma en ChromeHeadlessCI y genera el reporte
de cobertura en `frontend/coverage/app`. El lanzador usa un perfil temporal
aislado y desactiva GPU para funcionar también en CI.

La CI exige al frontend al menos 40 % de líneas, 35 % de
sentencias/funciones y 30 % de ramas.

Con el perfil aislado, la prueba de backend genera cobertura con JaCoCo en
`backend/sigaa-api/target/security-build/site/jacoco/index.html`. La cobertura mide lo que
ejercen las pruebas; no garantiza por sí sola cada flujo de negocio. La fase Maven
`verify` comprueba el mínimo de 40 % de líneas sin excluir clases de negocio;
`test` genera el informe de cobertura.

La revisión de código del **26/10/2026** registró:

| Comprobación | Resultado |
| --- | --- |
| Backend, con el alcance indicado arriba | 1184 casos: 1181 aprobados, 0 fallos, 0 errores y 3 omitidos por no configurar el conversor externo de documentos |
| Cobertura backend | Líneas 69,81 %; ramas 47,91 %; métodos 79,93 % |
| Frontend | 286 pruebas aprobadas |
| Cobertura frontend | Líneas 57,15 %; sentencias 52,89 %; funciones 40,88 %; ramas 37,91 % |
| Compilación backend y frontend de desarrollo | Correcta |
| Lint frontend | Correcto |

Estas cifras son un registro de esa ejecución, no una nueva validación del
27/10/2026 ni una certificación de todos los flujos. Las pruebas de renderizado real
se habilitan mediante `sigaa.note.render.command`. Quedan pendientes la verificación
manual, dispositivos móviles y el despliegue de producción. Los detalles y los
archivos candidatos a eliminación, que se conservaron, figuran en el
[informe de revisión](docs/REVISION_CODIGO_2026-10-26.md).

El frontend tiene activados `noUnusedLocals` y `noUnusedParameters`. La revisión
retiró código sin consumidores; los componentes invocados por Angular/Ionic o
Spring mediante configuración, anotaciones y ciclo de vida se conservaron.

## Despliegue a servidor

La guia completa y los archivos Docker de produccion estan en
[`PASOS_DESPLIEGUE_PRIVADO.md`](PASOS_DESPLIEGUE_PRIVADO.md). El despliegue es
privado por defecto: PostgreSQL y la API permanecen dentro de Docker y la web
solo escucha en localhost hasta que la red institucional o una VPN la publique.

La compilacion de produccion usa `frontend/src/environments/environment.prod.ts`.
Su `apiUrl` vacío significa que el frontend espera que el proxy inverso publique
la API bajo el mismo dominio, en `/api`. Los archivos privados de Documentos,
Actas, Reportes, Notas y Tareas se entregan mediante los endpoints autorizados
de cada módulo. El acceso directo a sus carpetas `/uploads/**` está bloqueado;
solo `/uploads/fotografias/**` se publica como recurso estático público.

### Lista de verificacion previa

- Cree una base PostgreSQL vacia y un usuario de aplicacion; no use desarrollo.
- Configure `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y un `JWT_SECRET` aleatorio
  de al menos 32 caracteres en el servidor.
- Configure `QR_PUBLIC_URL` con la URL HTTPS publica real.
- Configure SMTP real antes de habilitar recuperacion de contrasena y correos.
- Use HTTPS: termine TLS en Spring Boot con `SSL_*` o en el proxy inverso del despliegue.
- Configure `BACKUP_PG_DUMP`, `BACKUP_DIR` y `LIBREOFFICE_COMMAND` con rutas
  existentes del sistema operativo del servidor.
- Defina `CORS_ALLOWED_ORIGINS` en el backend con los dominios HTTPS reales y los orígenes móviles necesarios; no se administra desde Configuración > Seguridad.
- No copie `.env`, claves ni certificados dentro del repositorio o artefactos.

### Generar artefactos de produccion

```powershell
# API ejecutable, con pruebas y reporte JaCoCo
cd backend/sigaa-api
$postgresTestBin = 'C:\ruta\PostgreSQL\bin'
.\mvnw.cmd '-Dsigaa.security.validation=true' `
  '-Djunit.jupiter.extensions.autodetection.enabled=true' `
  "-Dsigaa.test.postgres.bin=$postgresTestBin" verify

# Aplicacion web optimizada
cd ../../frontend
npm.cmd ci
npm.cmd run build -- --configuration production
```

Con ese perfil, publique `backend/sigaa-api/target/security-build/sigaa-api-0.0.1-SNAPSHOT.jar` y el contenido
de `frontend/www/`. Antes de abrir el acceso institucional, valide la URL de
preproducción con una cuenta de prueba. Revise también la separación entre los
usuarios PostgreSQL de aplicación, migraciones y respaldos en
[`ops/A08-DESPLIEGUE.md`](ops/A08-DESPLIEGUE.md). Las alertas de presupuesto SCSS
y dependencias CommonJS son mejoras pendientes de rendimiento y deben revisarse
en la compilación de cada despliegue.

## APK Android (Capacitor)

SIGAA incluye un proyecto Android nativo en `frontend/android`, generado con Capacitor. El APK usa la misma API y, por tanto, la misma base de datos del despliegue institucional; no crea una base de datos paralela.

Al abrir la raíz del repositorio en VS Code, el servidor Java importa el backend
Maven y excluye el proyecto nativo Android de su importación Eclipse/Buildship.
Android se administra con Android Studio o su wrapper Gradle. Tras clonar o
reemplazar la copia local, ejecute `npm.cmd ci` y `npm.cmd run sync:android` desde
`frontend`, con la URL de API indicada abajo, para regenerar los archivos de
Capacitor que no se versionan, incluido `cordova.variables.gradle`.

Requisitos adicionales:

- Android Studio con Android SDK Platform 36.
- JDK 21 (Capacitor 8 no es compatible con Java 17 ni con Java 25 para esta compilación).
- La API debe estar accesible desde el teléfono. `localhost` no sirve desde un dispositivo Android.

La compilación Android obtiene la dirección de la API de `SIGAA_ANDROID_API_URL`.
El script `configure:android` valida esa URL y genera
`frontend/src/environments/environment.android.generated.ts`, ignorado por Git.
`npm.cmd run sync:android` ejecuta esa configuración, compila la web y sincroniza
Capacitor; no requiere editar manualmente el archivo generado.

```powershell
cd frontend
npm.cmd ci

# Abra una terminal usando JDK 21 e indique la URL HTTPS de su servidor.
# El dominio siguiente es solo un ejemplo; debe sustituirlo.
$env:SIGAA_ANDROID_API_URL = 'https://api.example.test'
npm.cmd run sync:android

# Genere el APK instalable de depuración.
cd android
.\gradlew.bat assembleDebug
```

El archivo resultante queda en `frontend/android/app/build/outputs/apk/debug/app-debug.apk`. Para abrir el proyecto nativo en Android Studio, ejecute `npm.cmd run android:open` desde `frontend`. La versión para distribución institucional debe generarse firmada desde Android Studio o mediante `assembleRelease` con un certificado que no se versiona.

Desde la carpeta `frontend`, para una prueba LAN mediante HTTP, establezca la URL
real del backend antes de sincronizar y habilite explícitamente la excepción de
desarrollo:

```powershell
$env:SIGAA_ANDROID_API_URL = 'http://192.168.1.50:8080'
$env:SIGAA_ANDROID_ALLOW_HTTP = 'true'
npm.cmd run sync:android
```

La IP anterior es ilustrativa. La excepción permite al script aceptar una URL
HTTP; el dispositivo y la configuración nativa también deben permitir ese tráfico.
Para distribución use HTTPS y retire esa excepción de la terminal antes de compilar.

En CORS, Android utiliza los orígenes WebView configurados (`http://localhost` o
`https://localhost`) e iOS usa `capacitor://localhost`. Inclúyalos cuando corresponda
en la lista efectiva del backend. La migración histórica `V68` no es la fuente
actual de CORS. Cambiar código frontend, recursos o la URL de API empaquetada requiere
recompilar y reinstalar el APK; un cambio exclusivamente backend no lo requiere
mientras conserve el contrato de la API.

## Roles y acceso

**Superadmin** es el único rol de administración técnica global. Los cargos
operativos incluyen Director/Directora, Subdirector/Subdirectora, Secretaría,
Jefe de Unidad y Miembro, con sus permisos y alcances por recurso.

Dirección tiene gestión operativa global; Subdirección mantiene el alcance
contextual definido por el módulo y no recibe ese alcance global por el cargo.
El rol **Administrador está retirado**: se conservan cuentas e historial, sin
promoverlas automáticamente a Superadmin ni conceder acceso por ese rol.
La navegación y las operaciones se validan en backend. Consulte
[Roles y alcance institucional](docs/ROLES_Y_ALCANCE_INSTITUCIONAL.md).

Las credenciales de acceso inicial no se publican en este documento. Solicite acceso a un administrador autorizado y cambie o deshabilite cualquier cuenta heredada antes del despliegue institucional.

## Módulos principales

| Módulo | Capacidades |
| --- | --- |
| Dashboard | Indicadores, tendencias calculadas, calendario, actividades y accesos rápidos. |
| Usuarios y configuración | Usuarios, roles, permisos, módulos, auditoría, respaldos, catálogos, sedes, perfil y preferencias, sujetos a los permisos vigentes. |
| Seguridad | Configuraciones funcionales de contraseña, bloqueo, 2FA y archivos dentro de los máximos del servidor. CORS, duración técnica de tokens y parámetros del despliegue quedan en backend; la API rechaza cambios administrativos de esos valores protegidos. |
| Reuniones | Creación, participantes, notificaciones, calendario y detalle de reuniones. |
| Actas | Borradores y actas terminadas, responsables reales, acuerdos, adjuntos y creación de tareas desde acuerdos. |
| Tareas | Responsables, estados, progreso, historial cronológico, adjuntos mediante descarga autorizada, notificaciones y calendario. |
| Documentos | Carpetas personales o compartidas, acceso por miembros, carga de archivos y solicitudes de aprobación. |
| Aprobaciones | El solicitante y quien responde se obtienen de la autenticación; solo el aprobador seleccionado puede aprobar o rechazar una solicitud pendiente. |
| Grupos | Grupos por unidad, dirigidos por su jefe, con miembros y permisos gestionables. |
| Correspondencia > Notas | Numeración anual, borradores, destinatarios por institución y departamento, documentos institucionales y seguimiento por un usuario interno independiente. |
| Correspondencia > Memos | Numeración, destinatarios, documentos con membrete institucional compartido con Notas y trazabilidad según sus permisos. |
| Asistencia | QR para sesiones, registro diario con geolocalización, invitados y consulta pública de QR. |
| Organización | Instituciones, sus directores y departamentos/áreas con responsable, cargo y estado; además de la estructura interna institucional. |
| Inventario y reportes | Gestión según roles y permisos vigentes; reportes programados con transacciones separadas por ejecución y notificación posterior a una confirmación exitosa. |

## Notificaciones

El panel de la campana muestra exclusivamente notificaciones no leídas del usuario autenticado. Permite marcarlas individualmente o todas como leídas, actualiza el contador y navega al registro relacionado sin eliminar el recurso original.

La pantalla actual de Configuración administra el canal Sistema, los tipos de
evento y las preferencias, programación, plantillas e historial disponibles.
La entrega externa depende de la infraestructura y de los servicios implementados;
un tipo de canal en el catálogo no demuestra por sí solo que exista un proveedor
operativo. Las solicitudes de aprobación notifican al aprobador seleccionado y las
respuestas al solicitante.

## Tareas: historial y adjuntos

El historial registra cambios reales de responsable, estado y avance, con usuario,
fecha/hora y valores anteriores y nuevos. Se guarda desde el backend en la misma
transacción que la modificación, reutilizando la auditoría existente. La consulta
`GET /api/tareas/{id}/historial` exige `Tareas:Ver` y acceso a la tarea concreta.

Los adjuntos se abren mediante
`GET /api/tareas/{id}/adjuntos/{adjuntoId}/descargar`. El backend comprueba el acceso
a la tarea y que el adjunto le pertenezca. No se publican sus archivos directamente
bajo `/uploads/tareas/**`. Consulte el diagnóstico anterior para los 404: la revisión
identificó el problema del indicador de disponibilidad, pero aún no lo corrigió ni
confirmó la recuperación de ningún archivo real.

## Organización institucional

La sección **Instituciones** administra los departamentos/áreas de cada
institución. Cada área conserva nombre, responsable o destinatario externo,
cargo y estado activo/inactivo. Esta información reutiliza el catálogo
existente; sus responsables y cargos son texto, no usuarios internos de SIGAA.

La sección **Estructura interna** mantiene el organigrama y las unidades propias
de SIGAA, de forma independiente del catálogo de destinatarios externos.

La configuración inicial del organigrama es:

```text
Dirección
├── Secretaría
├── Asesoría Legal
└── Subdirección
    ├── Unidad de Operaciones
    ├── Unidad de Comunicación Digital
    ├── Unidad de Análisis y Estadística
    ├── Unidad de Tecnología y Estructura
    ├── Unidad de Comunicación Estratégica
    └── Unidad de Gestión Administrativa
```

## Correspondencia > Notas

En Nueva Nota y en la edición de un borrador, el destinatario se configura así:

```text
Institución activa → Departamento / Área → Destinatario → Cargo
```

1. Seleccione una institución. Sus departamentos activos se cargan mediante
   `GET /api/notas/instituciones/{institucionId}/departamentos`; no se consulta
   el catálogo completo de departamentos.
2. Seleccione el departamento/área para completar el nombre y cargo del
   destinatario. Ambos campos conservan su edición manual existente.
3. Si cambia de institución, se limpian departamento, destinatario y cargo.
   Si cambia únicamente el departamento, se actualizan destinatario y cargo.
4. Seleccione por separado el **Responsable del seguimiento**, que continúa
   siendo un usuario interno de SIGAA. La selección de institución o área no
   lo asigna ni lo cambia, ni modifica sus notificaciones, permisos o trazabilidad.

Las consultas de departamentos se reutilizan durante una misma apertura del
formulario y las solicitudes anteriores se cancelan al cambiar de institución.
Una nueva apertura vuelve a consultar el catálogo. Los errores de carga se
muestran con la opción de reintentar.

El backend valida que la institución exista y que una nueva selección de
departamento esté activa y pertenezca a ella. El catálogo para Notas requiere
permiso de crear, editar o ver Notas; no concede administración de Organización.
La edición conserva la regla vigente: solo borradores sin oficializar.

Al guardar se conserva el nombre del departamento y se mantienen los valores
guardados de institución, destinatario y cargo. Los cambios posteriores del
catálogo no reemplazan automáticamente esos datos ni el contenido del documento
generado. Una referencia histórica desactivada puede conservarse al editar;
para oficializar se mantienen las validaciones de institución y departamento
activos cuando corresponde.

Las notas antiguas sin departamento continúan sin él. El vínculo es opcional
para mantener su compatibilidad y permitir borradores incompletos; no se infieren
áreas ni se reconstruyen datos históricos que no fueron guardados.

Notas y Memos reutilizan el motor documental y el membrete institucional de
Correspondencia. La generación Word, PDF y vista previa se realiza a través de los
servicios existentes; el formato institucional del documento es independiente de
la tipografía de la interfaz. La creación de una Nota no incluye adjuntar el PDF
oficial. **Marcar firmada** conserva el significado de firma física, sin certificados
ni firma digital; la carga del PDF firmado se realiza desde el detalle según el
estado y los permisos vigentes.

## Esquema de base de datos

`backend/sigaa-api/src/main/resources/db/schema.sql` es la única fuente de instalación
del esquema. Contiene 79 tablas, restricciones, índices, secuencias, vistas y los
catálogos técnicos indispensables. Los únicos datos de usuario son una cuenta
Superadmin y su asignación de rol; las tablas operativas comienzan vacías.

El script es transaccional y rechaza una base que ya contenga tablas en `public`.
Debe ejecutarse una sola vez sobre una base vacía. Las actualizaciones futuras del
modelo se realizan regenerando y validando este archivo para instalaciones nuevas;
una base existente requiere un procedimiento de mantenimiento y respaldo explícito.

## Seguridad de secretos

- No versionar `.env`, contraseñas, tokens, certificados ni archivos de claves.
- Usar `.env.example` como plantilla sin valores reales.
- Configurar correo, SMS y push mediante variables de entorno de cada ambiente.
- Los logs base mantienen nivel `INFO` y desactivan la impresión de SQL y sus
  parámetros. Los diagnósticos de aplicación revisados conservan el tipo de fallo y
  su ubicación en código sin volcar mensajes completos que puedan contener datos.

## Estructura resumida

```text
backend/sigaa-api/     API REST, seguridad, servicios, entidades y esquema SQL único
frontend/              Aplicación Ionic/Angular
frontend/proxy.conf.json  Proxy de desarrollo hacia la API
frontend/scripts/       Herramientas de compilación y mantenimiento
docs/                   CORS, roles, acceso y revisión de código
ops/                    Guías y herramientas de operación
.env.example           Plantilla de variables de entorno
```

## Licencia

Uso institucional interno.
