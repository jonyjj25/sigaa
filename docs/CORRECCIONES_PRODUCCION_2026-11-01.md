# Correcciones y validación de preparación para producción

Fecha: 1 de noviembre de 2026. Alcance: SIGAA web, API y Android, aproximadamente 20 usuarios por LAN/VPN.

**Las correcciones de código están implementadas y verificadas localmente. Falta la aceptación del despliegue institucional antes de abrir producción.** No se ha desplegado en un servidor, no se ha emitido una APK release institucional y no se han utilizado datos ni credenciales reales. La revisión inicial se conserva en [Revisión histórica](REVISION_PRODUCCION_2026-11-01.md).

## Qué se corrigió

| Hallazgo inicial | Cambio implementado | Evidencia / límite |
| --- | --- | --- |
| Capacitor y dependencias vulnerables | Angular 20.3.34, herramientas 20.3.42 y Capacitor core/Android/iOS/CLI 8.6.0; lockfile regenerado sin forzar peers incompatibles. Jackson 3.1.8 y 2.21.8 y Tomcat 11.0.27 en Maven. | npm de ejecución: 0 avisos. OSV sobre 109 dependencias Maven de ejecución: 0 avisos. No equivale a una certificación de seguridad de imágenes/servidor. |
| APK Debug por HTTP | Flujo `npm run android:release` que exige HTTPS y keystore, valida metadatos del origen, sincroniza y firma. Gradle rechaza claves faltantes, mixed content y recursos ajenos al build solicitado. Versión 1.0.8, código 9. | Build nativo Android recompilado con 305 tareas; la APK de verificación usa `https://api.invalid`, `release=false` y NO se distribuye. Dominio y keystore institucionales aún no existen, según confirmó el usuario. |
| Token reutilizable tras logout | Logout autentica cookie/Bearer y hace un incremento atómico de `tokenVersion` dentro de una transacción; Android envía también Bearer al cerrar. | Regresión permanente exige GET autorizado → logout → 401 con el Bearer y cookie anteriores. Política elegida: logout revoca **todas** las sesiones de la cuenta, incluido otro teléfono/PC. |
| CSP bloqueaba estilos | Se desactivó `inlineCritical` en el build production sin abrir `script-src`; se retiró la importación remota de Google Fonts. Se mantiene la pila tipográfica de sistema. | El HTML compilado tiene stylesheet normal, sin `onload` inline ni fuentes remotas. Se ensayó con Caddy 2.10.2 real. |
| Fallback Angular interceptaba API | Caddy usa un bloque exclusivo para `/api` y `/uploads`; el fallback y caché pública de estáticos quedan solo en el bloque web. | Ensayo real con API ficticia: JSON, POST login, 401 privado, 403 de archivo, SPA y CSS correctos; no se aplica caché pública al recurso privado. CI Docker incorpora una comprobación equivalente contra Spring. |
| Pérdida/falsificación de IP tras el proxy | `SIGAA_TRUSTED_PROXIES` obligatorio, evaluación estricta de derecha a izquierda, envío a Spring de la IP validada y retirada del encabezado alternativo `Forwarded`. | Caddy real comprobado con proxy confiable y cliente no confiable. Infraestructura debe poner las IP/CIDR efectivas y repetir con la red real. |
| Relojes inconsistentes | Compose establece zona de JVM y PostgreSQL mediante `APP_TIME_ZONE`; el arranque directo de Java configura la zona antes de iniciar Spring. Valor por defecto Panamá. | Validación de configuración y suite de negocio. La aceptación debe incluir fecha/QR alrededor de medianoche y cambio de año en el contenedor real. |
| Solo existía copia de BD | Job de copia completa con API/web detenidos brevemente, dump + uploads + manifiesto, cifrado autenticado en bloques, retención y reanudación del estado anterior. Herramienta offline y Compose de recuperación aislado. | Binarios/adjuntos, corrupción, truncado, clave incorrecta y rutas maliciosas probados. `pg_dump`/`pg_restore` reales y permisos restaurados comprobados en PostgreSQL temporal. Montaje, timer y ensayo de desastre Docker siguen requiriendo el servidor. |

Otros cambios:

- La disponibilidad de adjuntos de Tareas verifica el archivo físico y rechaza rutas fuera de su almacenamiento, conservando la referencia histórica.
- Los fallbacks de Configuración ya disponían de estado de error y aviso global: se confirmó ese comportamiento, se conserva el aviso durante el reintento y se añadió un control para reintentar. No se presentan esos mecanismos previos como una implementación nueva.
- Se corrigieron nombres/títulos accesibles de botones y usos de colores; el auditor UX ahora resuelve la interpolación Sass antes de medir contraste. No se inventaron tokens para ocultar falsos positivos del parser. La auditoría pasa.
- Android guarda exportaciones en caché privada y abre el diálogo nativo de abrir/guardar; se adaptaron Documentos, Tareas, Actas, Notas, Memos, Reportes, Inventario, Auditoría, Grupos y QR. Nombres de archivo saneados y acotados en bytes. FileProvider se limita a esa caché y `allowBackup=false`.
- La fecha de vencimiento de contraseña, que antes se guardaba sin impedir acceso, ahora bloquea login, segundo factor y sesiones previas cuando está vencida; logout sigue disponible. La recuperación de contraseña establece una nueva fecha conforme a la política.
- Se estabilizó la fixture de procesos de respaldo esperando la creación efectiva del hijo antes de medir su timeout. Se mantienen las aserciones de terminación, limpieza y ausencia de copias completas en caso de fallo; no se amplió el timeout productivo.
- Se corrigió la alerta de porcentaje de 5xx a bajo tráfico, se añadieron métricas/alertas de SMTP, uploads y copia completa, y rotación de logs Docker.
- CI usa Node 24, verifica avisos de ejecución npm/Java, prueba la lógica del job de copia y el proxy en Docker. El trabajo antes llamado Flyway se renombró como instalación del esquema SQL. Los resultados locales no sustituyen la ejecución de CI después de publicar en el repositorio remoto.

## Pruebas y artefactos

| Validación | Resultado |
| --- | --- |
| Backend `clean verify`, JDK 21, PostgreSQL temporal 18 | **1.253 casos, 0 fallos, 0 errores y 3 omitidos**. Compilación, empaquetado y umbral de cobertura aprobados. No se conectó una BD habitual. |
| JaCoCo limpio | Líneas 72,63 %, ramas 51,33 %, métodos 81,54 %. No se excluyeron clases de negocio para alcanzar el mínimo. |
| Recuperación con PostgreSQL real | Dump cifrado antiguo → paquete completo con PDF ficticio → extracción → `pg_restore` en otra BD → permisos runtime. Consulta permitida y CREATE TABLE denegado al runtime. |
| Orquestación del job completo | 5 escenarios aprobados con controlador Docker ficticio: éxito, fallo del helper, interrupción TERM, montaje perdido y servicios previamente detenidos. Verifica reanudación tras error/interrupción y ausencia de arranque de servicios que ya estaban detenidos. No simula un motor Docker real. |
| Frontend | **365/365 aprobadas**, lint y auditoría UX aprobados, build production correcto. Cobertura: líneas 60,91 %, sentencias 56,02 %, funciones 43,40 %, ramas 41,35 %. |
| Android: configuración | 4/4 aprobadas: URL obligatoria, HTTP limitado a debug, retiro de excepción y rechazo de URLs inválidas para release. |
| Android nativo | Recompilación correcta con Capacitor Filesystem y Share; inspección de paquete 1.0.8/código 9 y mixed content desactivado. No hay prueba en dispositivo físico. |
| Caddy real | Rutas y encabezados probados con proxy confiable y no confiable, incluido intento de falsificar IP. |
| npm ejecución / Maven ejecución | 0 avisos en las consultas realizadas en esta fecha. Las auditorías quedan integradas en CI para que no dependan de una cifra histórica. |
| Scripts/configuración | Sintaxis Bash, PowerShell y YAML comprobada. Lockfile y manifiesto npm coinciden. |
| Herramienta offline empaquetada | Extracción desde el JAR ejecutable con PropertiesLauncher, manifiesto y adjunto ficticio correctos, sin iniciar Spring ni conectar una BD. |

Logs locales, ignorados por Git:

- `backend/sigaa-api/target/production-fixes-backend-final.log` y `target/security-build/surefire-reports/`.
- `backend/sigaa-api/target/security-build/site/jacoco/index.html`.
- `frontend/.angular/production-fixes-tests-final.log`, `production-fixes-build-final.log` y `production-fixes-lint.log`.
- `frontend/.angular/production-fixes-android-sync-final.log` y `.runtime/production-fixes-gradle-online.log`.
- `frontend/.angular/production-fixes-android-resync.log` registra la sincronización final completada tras un error de acceso de Windows; `.runtime/production-fixes-gradle-final.log` registra el APK final y `production-fixes-release-guard.log` la comprobación de rechazo sin keystore.
- `frontend/.angular/production-fixes-runtime-audit.json`, `production-fixes-audit.json` y `.runtime/production-java-osv.json`.

El JAR comprobado queda en `backend/sigaa-api/target/security-build/sigaa-api-0.0.1-SNAPSHOT.jar`. La web comprobada queda en `frontend/.angular/production-fixes-web/browser/`; Docker hace su propio build desde fuentes. No desplegar las copias de pruebas bajo `.runtime` ni la APK Debug histórica de `output/apk`.

## Pendientes reales antes de producción

1. **Infraestructura:** servidor dedicado inicial de 4 vCPU/8 GB/100 GB ampliable para probar el escenario de 20 personas; DNS privado, VPN Android/PC, proxy HTTPS y certificado confiable en WebView. Dimensionamiento propuesto, pendiente de carga y volumen documental.
2. **Configuración institucional:** dominio definitivo, IP efectiva del proxy, SMTP real, credenciales nuevas, fuentes Verdana autorizadas, montaje externo y receptor de alertas. No se han usado datos o secretos reales.
3. **Firma Android:** generar y custodiar el keystore fuera del repositorio; no existe todavía. Emitir la release contra el dominio definitivo y probarla en teléfonos físicos. La APK de verificación no se entrega a usuarios.
4. **Despliegue/operación:** ejecutar CI Docker, instalar el job systemd, probar una recuperación completa en otra máquina/proyecto y medir el RPO/RTO. El host Windows de esta sesión no dispone de Docker; se probaron Caddy y PostgreSQL nativos, no los contenedores reales.
5. **Aceptación:** validar cada rol, rechazo entre unidades, reuniones/actas/tareas, notas/memos, adjuntos, QR, reportes programados, inventario, 2FA y logout en web y APK. Ensayar hasta 20 usuarios concurrentes y el reinicio del servidor.

No ocultar health SMTP ni reducir controles para conseguir un indicador verde. El job escribe un evento de fallo en el registro del sistema; infraestructura debe conectar ese registro y Alertmanager a un receptor que se pruebe. Un monitor alojado fuera del host debe cubrir apagado del servidor y vencimiento TLS.

## Mejoras residuales sin mezclar con los bloqueos corregidos

- npm completo conserva **43 avisos moderados solo en herramientas de desarrollo**, derivados de glob, minimatch y uuid. npm propone incluso retroceder Angular/Capacitor a versiones mayores antiguas; no se aplicó ese cambio ni se forzaron reemplazos de API incompatibles. No se empaquetan esas herramientas en la web/API final. Construir solo con repositorio y patrones de entrada confiables y seguir los parches de sus mantenedores. Su eliminación total requiere una actualización compatible de la cadena de lint/pruebas; no se declara que el proyecto completo tenga cero avisos.
- Tres pruebas de renderizado requieren LibreOffice y fuentes configurados. Permanecen omitidas, con esa causa visible; la prueba en contenedor debe ejecutarse con documentos representativos y Verdana autorizada.
- Permanecen dos advertencias de presupuesto de estilos: Actas 28,06 kB y Reuniones 31,90 kB frente a aviso de 27 kB. El build cumple el límite de error de 32 kB. No se aumentaron presupuestos para esconderlas.
- Gradle informa APIs obsoletas de la cadena Android actual; no se migró a Gradle 9 sin soporte comprobado de los plugins.
- Los códigos 2FA y límites de intentos siguen en memoria: desplegar una sola API inicialmente. Una arquitectura con múltiples réplicas requiere otro diseño de estado compartido y coordinación.

Durante la corrección y las pruebas no se desplegó, no se emitieron certificados ni se modificó una base institucional. Los cambios locales previos se conservaron. El destino autorizado posteriormente para publicar el código es `main` de `https://github.com/jonyjj25/sigaa`; los respaldos locales de BD y los secretos quedan fuera de esa publicación.

## Documentación operativa vigente

- [Servidor, solicitud a infraestructura, instalación y mantenimiento](GUIA_SERVIDOR_Y_MANTENIMIENTO_2026-11-01.md).
- [Copias completas y recuperación aislada](../ops/RECUPERACION_COMPLETA.md).
- [Mínimo privilegio PostgreSQL](../ops/A08-DESPLIEGUE.md).
- [Generación Android](../INSTRUCCIONES_APK_ANDROID.txt).
