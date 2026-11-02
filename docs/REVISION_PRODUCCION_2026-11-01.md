# Revisión de SIGAA para producción, web y APK

> **Registro histórico de la revisión inicial.** Después se implementaron y probaron
> correcciones. El estado actual y los pendientes institucionales están en
> [Correcciones de producción](CORRECCIONES_PRODUCCION_2026-11-01.md).

Fecha: 1 de noviembre de 2026. Escenario confirmado: aproximadamente 20 personas, solo red institucional o VPN.

**Dictamen: todavía no está listo para producción.** Hay defectos y pendientes verificables aunque la web compile y la mayoría de las pruebas pase. La [guía operativa actualizada](GUIA_SERVIDOR_Y_MANTENIMIENTO_2026-11-01.md) explica qué solicitar, instalar, mantener y aceptar después de corregirlos.

## Alcance y límites

Se revisaron la estructura de backend/frontend/Android, autenticación, permisos y acceso a recursos, archivos, numeración y transacciones mediante código y pruebas existentes, planificadores, respaldos, esquema inicial, configuración Docker/proxy, CI y documentación. Se ejecutaron las suites automatizadas indicadas abajo y se inspeccionó el APK existente, incluida su firma, manifest y URL empaquetada.

Se revisó el árbol de trabajo actual, que ya contenía numerosos cambios sin commit. HEAD era `f54a2e6d983c2a30941f5ede843e0ed2d8c4556b`; ese hash no representa por sí solo todos los archivos revisados. Se conservaron los cambios previos. No se consultó el `.env` privado ni se usaron cuentas, archivos de usuarios o bases institucionales. Las integraciones PostgreSQL utilizaron la fixture temporal del proyecto.

Esto no es una certificación exhaustiva de cada pantalla, combinación de roles o condición de carrera. No hubo dispositivo Android conectado, Docker no estaba disponible en PATH y no se ejecutó un despliegue real. No se realizó un análisis completo de vulnerabilidades de dependencias Java, paquetes del SO o imágenes de contenedor. Los resultados de npm son los del registro consultado en esta fecha y no implican que cada aviso sea explotable en estos flujos.

## Hallazgos que deben cerrarse antes de producción

### P1-01. Capacitor Android instalado tiene un aviso crítico vigente

`frontend/package-lock.json` y `npm ls` resuelven `@capacitor/android` y `@capacitor/ios` a **8.4.0**. El aviso oficial [GHSA-rvm3-566m-v7fv](https://github.com/ionic-team/capacitor/security/advisories/GHSA-rvm3-566m-v7fv) incluye esa versión y describe carga de contenido remoto bajo el origen de la aplicación mediante el proxy HTTP interno. La línea 8.4 tiene corrección en 8.4.3.

`npm audit --omit=dev --json` señala **8 paquetes afectados: 3 críticos, 1 alto y 4 moderados**. Los críticos corresponden a Capacitor Android/iOS y DOMPurify transitivo de jsPDF. Angular Router tiene avisos de SSR; el build revisado es una SPA estática y no se demostró explotación SSR. DOMPurify tiene condiciones específicas que deben contrastarse con los usos de jsPDF, sin presentar todos los avisos como ataques reproducidos.

Acción: actualizar a versiones corregidas y compatibles, mantener coordinadas las versiones Capacitor, regenerar el lockfile, sincronizar Android y repetir build/pruebas y revisión de avisos. Confirmar también [aviso Angular](https://github.com/angular/angular/security/advisories/GHSA-jfgx-762w-99vr) y [aviso DOMPurify](https://github.com/cure53/DOMPurify/security/advisories/GHSA-cg2g-xh64-v4q7). No aplicar actualizaciones mayores a ciegas.

### P1-02. El APK disponible es de desarrollo y lleva una API LAN por HTTP

Artefacto inspeccionado: `output/apk/SIGAA-1.0.7-debug.apk`, 7.386.152 bytes, paquete `com.sigaa.app`, versionCode 8, Android mínimo API 24 y target 36.

Evidencia directa de `aapt`, `apksigner` y lectura ZIP:

- `application-debuggable` habilitado.
- Certificado `CN=Android Debug, O=Android, C=US`.
- `assets/capacitor.config.json`: `allowMixedContent=true`.
- Bundle `assets/public/chunk-SBGDSJJE.js`: API `http://192.168.0.52:8080`.
- SHA-256: `cd8067d62f1918f917985497fc45c9a89e396446212e186c4955bf64c5ddf4c4`.

Ese APK depende de un equipo de desarrollo y expone el tráfico de prueba sin TLS; no sirve como entrega institucional final. La configuración fuente principal bloquea cleartext release y el script prueba excepciones HTTP limitadas al host de depuración: es una protección útil, pero no convierte el APK existente en release.

Acción: generar una nueva APK release con dominio HTTPS privado estable, dependencias corregidas, sin mixed content, firma institucional y versionCode superior. El `build.gradle` actual no define `signingConfig` release; `assembleRelease` no basta para acreditar firma. Custodiar el keystore. [Firma Android](https://developer.android.com/studio/publish/app-signing).

### P1-03. Logout no revoca la credencial del servidor

Ubicaciones: `AuthController.java:386`, `JwtFilter.java:26` y `frontend/src/app/services/auth.service.ts:65`.

El controlador elimina `sigaa_access`, pero no cambia `tokenVersion` ni registra revocación. `JwtFilter` excluye `/api/auth/logout`, por lo que además no autentica esa petición a través de su validación JWT. El frontend elimina el token local; una copia previa del Bearer conserva su validez hasta expirar o producirse otra revocación.

El proyecto ya conservaba un diagnóstico fuera de la suite ordinaria en `ops/security-diagnostics/LogoutReplayDiagnosticTest.java`; se reejecutó durante esta revisión. **Falló como se esperaba: se exigía 401 después de logout y el endpoint protegido devolvió 200.** Usa filtro/controlador reales con verificador JWT simulado; no es una prueba sobre cuentas reales. La copia temporal del test y su clase compilada se retiraron al terminar, manteniendo el diagnóstico original y el log. No se debe interpretar una suite ordinaria aprobada como cierre de este hallazgo.

Acción: definir revocación por sesión o revocación de todas las sesiones del usuario, procesar su autenticación al cerrar y convertir el diagnóstico en regresión permanente. Criterio: GET autorizado → logout → mismo token devuelve 401, tanto desde web como Android.

### P1-04. El HTML de producción necesita un handler que CSP bloquea

Ubicaciones: `frontend/Caddyfile:14` y la configuración de optimización de `frontend/angular.json`.

El build optimizado generado en esta revisión contiene:

```html
<link rel="stylesheet" href="styles-QHR3GCFA.css" media="print" onload="this.media='all'">
```

Caddy devuelve `script-src 'self'`, sin autorización de handlers inline. Ese `onload` es JavaScript inline y queda bloqueado; la hoja global permanece con medio `print` cuando JavaScript está habilitado. El elemento `noscript` no resuelve el caso. Puede haber estilos críticos/componentes visibles, pero no se aplica normalmente toda la hoja global.

Es una incompatibilidad confirmada entre los archivos producidos y la política; no se hizo una inspección visual de un contenedor Caddy ejecutándose. Además, el build conserva URLs de fuentes `fonts.gstatic.com`, mientras `font-src` permite únicamente `self` y `data:`. Esto provoca fallback tipográfico.

Acción: alinear la optimización con CSP —por ejemplo, revisar la desactivación de inlining crítico o una solución CSP compatible— y alojar fuentes localmente o aprobar sus orígenes. No resolverlo abriendo indiscriminadamente `script-src`. Probar el build con los encabezados reales. Referencias: [Angular y CSP](https://angular.dev/best-practices/security), [CSP en Chrome](https://developer.chrome.com/docs/privacy-security/csp).

### P1-05. El respaldo automático no permite recuperar por sí solo el sistema completo

Ubicaciones: `BackupService.java:226` y volúmenes `uploads_data`, `backups_data` de Compose.

El servicio ejecuta `pg_dump`, cifra el archivo, valida y copia al destino externo. No incorpora el volumen `uploads_data`. Restaurar únicamente PostgreSQL conserva referencias a documentos pero no recupera los archivos físicos de tareas, documentos, actas, correspondencia, reportes o fotografías.

El ensayo de restauración crea una base desechable y verifica estructura/cuenta inicial. No restaura producción ni comprueba todos los adjuntos. El formato cifrado SIGAABK1 requiere descifrado compatible antes de usar `pg_restore`; no se encontró un procedimiento operativo independiente y completo para reconstruir el sistema desde un servidor perdido.

Acción: automatizar y ensayar copias coordinadas de BD + uploads + versión/configuración, con custodia separada de claves y copia externa protegida. Confirmar que el montaje externo realmente existe; el código comprueba rutas/archivos pero una carpeta del disco local puede aparentar un destino externo cuando falla un montaje.

### P1-06. APP_TIME_ZONE no gobierna todos los relojes usados por la aplicación

Ubicaciones: `docker-compose.prod.yml:43`, `AsistenciaController.java:385`, `InventarioService.java:76`, `NotaNumeroService.java:41`.

Algunos servicios usan explícitamente Panamá o `app.time-zone`; otros usan `LocalDate.now()`/`LocalDateTime.now()` sin zona. Compose define `APP_TIME_ZONE`, pero no configura `TZ`, `-Duser.timezone` ni el timezone PostgreSQL. No hay una inicialización global que lo unifique.

Si la JVM del contenedor usa UTC, a las 19:30 de Panamá ya es el día siguiente para el código sin zona. Un QR de la fecha local puede considerarse vencido/archivarse antes de tiempo; a fin de año, inventario y numeración de notas pueden usar años distintos. Este escenario se deduce del código; no se ejecutó un contenedor para medir su zona efectiva.

Acción: fijar y verificar zona JVM/BD en el despliegue y unificar el código con relojes explícitos. Añadir pruebas de medianoche y cambio de año en Panamá. La guía contiene un override propuesto como configuración operativa temporal, pendiente de ensayo.

### P1-07. La topología de proxy documentada pierde la IP real sin confianza explícita

Ubicaciones: `frontend/Caddyfile`, `application-prod.properties:7`, `AuthController.java:180` y `JwtFilter.java:75`.

El diseño pone un proxy HTTPS delante de Caddy por HTTP. Caddy no configura `trusted_proxies`; por defecto descarta los `X-Forwarded-*` recibidos de un par no confiable y genera los suyos. Spring sí procesa esos encabezados. La IP final puede ser la del proxy/puente Docker, no la del usuario: afecta auditoría, whitelist/blacklist y límites de intentos por IP.

Acción: infraestructura y aplicación deben establecer la cadena de confianza con IPs/CIDR exactos de proxies controlados y sanitizar encabezados en la entrada. Probar desde dos clientes y con un encabezado falsificado; no habilitar restricciones IP antes de esa comprobación. [Comportamiento documentado de Caddy](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy#defaults).

### P1-08. El fallback SPA se ejecuta antes del proxy de API y uploads

Ubicación: `frontend/Caddyfile:21` a final del archivo. `try_files {path} /index.html` está en el mismo bloque que los `reverse_proxy` con matchers de ruta. Caddy ordena directivas por su orden predeterminado, no por su posición escrita: **`try_files` se evalúa antes de `reverse_proxy`**.

Para una ruta como `/api/config`, que no existe como archivo en `/srv`, el fallback reescribe el path a `/index.html`. Los matchers `/api/*` y `/uploads/*` dejan de coincidir. Un GET termina sirviendo HTML de la SPA; peticiones de otros métodos también dejan de llegar a la API y pueden terminar en 405. El servidor de desarrollo Angular usa otro proxy, por lo que sus pruebas/build no detectan este error del despliegue Caddy.

El hallazgo se deriva del Caddyfile y del [orden oficial de directivas](https://caddyserver.com/docs/caddyfile/directives#directive-order); no se ejecutó Caddy localmente. Acción: aislar API/uploads en bloques `handle` mutuamente excluyentes y poner `try_files`/`file_server` solo en el fallback web, o usar un `route` con orden explícito correctamente probado. Mantener los prefijos que espera Spring. Véase el [patrón oficial SPA + API](https://caddyserver.com/docs/caddyfile/patterns#single-page-apps-spas).

Criterio de aceptación: `/api/config` entrega JSON real a través del puerto web; login llega al controlador; descargas llegan al backend y conservan su autorización; una ruta Angular sigue resolviendo la SPA.

## Otros fallos y deuda de operación

| Prioridad | Hallazgo y evidencia | Impacto / corrección |
| --- | --- | --- |
| P2 | `TareaController.java:648` marca disponible un adjunto si su ruta no es null, mientras `TareaAdjuntoService.archivo` comprueba que el archivo exista. | La pantalla ofrece abrir archivos que terminan en 404. Comprobar existencia de manera segura y comunicar referencia sin archivo, sin borrar su historial. |
| P2 | `ConfigDataService.getUsers`, historial, resumen/facetas de auditoría y otros métodos convierten errores HTTP en listas vacías o ceros; por ejemplo líneas 219 y 498. | Una caída o un 403 aparenta ausencia de usuarios/eventos. Propagar error o representar explícitamente carga fallida y reintento. |
| P2 | `npm run audit:ux` falla con 47 observaciones y CI lo exige. | Botones sin nombre/título, tokens de tema ausentes y colores que incumplen reglas locales. No todas son errores funcionales, pero el pipeline frontend queda bloqueado. |
| P2 | `frontend/Dockerfile:1` construye con Node 20; la documentación exige Node 22/24 y Capacitor CLI declara `node >=22`. Node 20 terminó soporte el 30/4/2026. | Alinear imagen/CI y validar build limpio. Node aquí es etapa de construcción, no servidor de ejecución de la web. [Calendario oficial](https://github.com/nodejs/Release#release-schedule). |
| P2 | La suite backend completa falló en una prueba de procesos de respaldo; su repetición aislada pasó. | Prueba intermitente no resuelta. El fallo ocurrió al comprobar `child.pid`, no al demostrar un hijo vivo; no hay evidencia suficiente para afirmar que producción deja procesos huérfanos. Investigar el arranque de la fixture bajo carga y repetir CI Linux. |
| P2 | Android usa descargas `blob`, enlaces `download`, ventanas e impresión web; no hay implementación Filesystem/Share/FileOpener ni prueba física de esos flujos. | Riesgo de incompatibilidad WebView pendiente de reproducción. Validar PDF/DOCX/CSV y guardado/apertura en dispositivos; implementar adaptación nativa si falla. No se declara que todas las descargas estén rotas. |
| P2 | `ops/monitoring/alert-rules.yml` divide el porcentaje de 5xx por `clamp_min(tasa_total, 1)`. | A menos de una petición por segundo diluye el porcentaje; con una petición fallida/minuto marca ~1,67 % aunque falle el 100 %. Ajustar el umbral mínimo/volumen y probar reglas, especialmente con solo 20 usuarios. |
| P2 | Monitorización actual sin alertas específicas de uploads, disco/host, TLS y SMTP; receptores/servicios externos pendientes. Logs Docker sin rotación explícita en Compose principal. | Completar operación y rotación; un monitor externo debe detectar la caída del host. `unhealthy` no implica reinicio automático. |
| P2 | La documentación previa omite `SIGAA_FONTS_DIR_HOST`, que Compose exige; `.env.example` tampoco lo incluía. CI tiene un trabajo llamado Flyway, pero la API usa instalación SQL y `ddl-auto=validate`. | La receta anterior falla o induce expectativas incorrectas de migración. Se añadió la variable de ejemplo y una guía vigente; no se cambió el esquema ni la CI. |
| P3 | Build producción avisa de presupuestos SCSS: Actas 28,06 kB y Reuniones 31,90 kB. Manifest Android conserva `allowBackup=true` sin política específica de exclusión. | Optimizar estilos y acordar política de copias de datos locales del teléfono según lo almacenado; revisar antes de distribuir. |

La aplicación guarda información de perfil/preferencias localmente y el token en sessionStorage. No ofrece sincronización offline ni un actualizador institucional propio de APK. El correo 2FA y los códigos en memoria también obligan a probar reinicios y reanudación de sesión.

## Validaciones ejecutadas

| Comprobación | Resultado de esta revisión |
| --- | --- |
| Lint frontend | Aprobado. |
| Configuración Android, Node test runner | 3/3 aprobadas: URL obligatoria, HTTP explícito de prueba y retirada de excepción al volver a HTTPS. |
| Auditoría UX | Falló: 47 observaciones. |
| Suite frontend | 360/360 aprobadas en ChromeHeadlessCI. Cobertura: líneas 60,49 %, sentencias 55,59 %, funciones 43,10 %, ramas 41,15 %. |
| Build web production | Aprobado; 1,14 MB inicial sin comprimir, advertencias SCSS indicadas arriba. Salida aislada, sin sincronizarla al APK. |
| Suite backend aislada, incluyendo perfil prod | 1.245 casos: 1.241 aprobados, 1 fallo, 0 errores y 3 omitidos por falta de conversor externo. `verify` terminó en BUILD FAILURE, antes de completar las fases posteriores. |
| Perfil prod con PostgreSQL temporal/restringido | 1/1 aprobado: privilegios runtime y distinción entre probes y fallo SMTP. No valida TLS/SMTP institucionales. |
| Repetición aislada BackupServiceResilienceTest | 25/25 aprobados sin modificar código. La ejecución completa anterior continúa registrada como fallida. |
| Backend desde fuentes copiadas a un directorio nuevo | Compilación y empaquetado aprobados con `-DskipTests package`, incluida compilación de fuentes de pruebas. Comprueba que no depende de clases antiguas, pero omite ejecutar pruebas en esa copia. |
| Reproductor logout | 1 caso fallido: esperado 401, recibido 200 después de logout. Filtro/controlador reales con verificador de token simulado y datos ficticios. |
| Android Gradle assembleDebug offline | Aprobado; 244 tareas up-to-date. Valida el estado nativo/recursos ya sincronizados, no constituye un rebuild completo de Angular ni una prueba funcional física. |
| APK existente | Firma, paquete, API level, modo debug, configuración y URL inspeccionados directamente. `adb devices -l` no mostró dispositivos. |
| Dependencias npm | Total con desarrollo: 76 paquetes afectados (8 críticos, 18 altos, 49 moderados, 1 bajo). Sin desarrollo: 8 (3 críticos, 1 alto, 4 moderados). |
| Docker/servidor institucional | No ejecutado; no había comando Docker disponible. |

La cobertura backend no se presenta como una medición nueva limpia: el directorio de ejecución existente acumula datos y el `verify` completo falló. Las tres pruebas de renderizado real requieren `sigaa.note.render.command`; no se encontró LibreOffice local en la ruta habitual. Deben ejecutarse en el entorno preparado con conversor y fuentes.

Las pruebas existentes cubren autorización por rol/recurso, contraseñas/2FA, archivos privados, correspondencia, concurrencia de códigos y reservas, inventario, reuniones/actas/tareas, transacciones de reportes y privilegios PostgreSQL. Su aprobación parcial no sustituye las pruebas de aceptación por cada rol sobre web y APK release.

## Evidencia local reproducible

Archivos ignorados de trabajo, con datos ficticios:

- `backend/sigaa-api/target/revision-2026-11-01-maven.log`: ejecución completa.
- `backend/sigaa-api/target/revision-2026-11-01-evidence/backup-full-suite.txt` y `.xml`: fallo original conservado antes de repetir.
- `backend/sigaa-api/target/revision-2026-11-01-backup-retry.log`: repetición de resiliencia.
- `backend/sigaa-api/target/revision-2026-11-01-clean-package.log`: empaquetado desde copia de fuentes nueva.
- `backend/sigaa-api/target/revision-2026-11-01-logout.log`: reproducción de logout.
- `frontend/revision-2026-11-01-lint.log`.
- `frontend/.angular/revision-2026-11-01-tests.log`, `revision-2026-11-01-build.log` y carpeta `revision-2026-11-01-production/browser`.
- `frontend/.angular/revision-2026-11-01-audit.json` y `revision-2026-11-01-audit-production.json`.
- `.runtime/revision-2026-11-01-gradle.log`.

Las copias de fuentes de verificación y sus artefactos bajo `.runtime` no son la versión que debe desplegarse.

## Documentación modificada y siguiente paso

Se creó este informe y la guía operativa. Se añadieron enlaces/avisos en README y en el procedimiento anterior, además de la variable de fuentes en `.env.example`. No se corrigió en esta revisión la lógica de la aplicación, no se generó una APK release, no se cambiaron datos/secretos y no se hizo commit, push ni despliegue.

Orden propuesto: corregir P1 de código y dependencias → recuperar CI estable → preparar copias/restauración → instalar preproducción por LAN/VPN → validar web/APK release por rol y carga → autorizar producción con responsables y evidencia. No entregar únicamente la APK ni ejecutar `git pull && up --build` como sustituto de esa preparación.
