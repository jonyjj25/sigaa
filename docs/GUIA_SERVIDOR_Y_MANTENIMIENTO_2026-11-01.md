# SIGAA: servidor, instalación y mantenimiento

Fecha: 1 de noviembre de 2026. Escenario confirmado: aproximadamente 20 usuarios; acceso exclusivamente por red institucional o VPN, también desde Android.

**Estado: correcciones de código implementadas; aceptación institucional pendiente.** Consulte el [registro de correcciones y pruebas](CORRECCIONES_PRODUCCION_2026-11-01.md). La [revisión inicial](REVISION_PRODUCCION_2026-11-01.md) se conserva como evidencia histórica. Antes de abrir acceso deben comprobarse el servidor real, HTTPS/VPN, SMTP, copias externas y APK release. No se ha desplegado en un servidor institucional.

## 1. Qué pedir al área de servidores

Puede enviar esta solicitud:

> Necesitamos una máquina virtual Linux dedicada para SIGAA, inicialmente para 20 personas. Propuesta inicial: Ubuntu Server 24.04 LTS de 64 bits, 4 vCPU, 8 GB de RAM y al menos 100 GB de SSD ampliable. Necesitamos Docker Engine con Compose v2, DNS privado estable, HTTPS confiable desde computadoras y Android, acceso por LAN/VPN, SMTP institucional y almacenamiento externo para copias cifradas de PostgreSQL y de los archivos adjuntos. Solicitamos también preproducción separada, monitorización con avisos a un responsable, acceso administrativo por SSH restringido y una prueba documentada de restauración completa. El tamaño final y la retención se ajustarán al volumen documental y a una prueba con hasta 20 usuarios concurrentes.

Es una **estimación inicial**, no un resultado de pruebas de carga. Si se compilan imágenes en esa misma máquina o se convierten documentos grandes simultáneamente, valorar 16 GB de RAM o construir las imágenes en CI. Los adjuntos y sus copias pueden superar ampliamente el tamaño de la base de datos.

Solicitar por escrito:

| Recurso | Entrega necesaria |
| --- | --- |
| Servidor | IP privada fija, CPU/RAM/disco asignados, SO soportado y política de parches. No basta un hosting compartido PHP. |
| Acceso administrativo | Cuenta nominal con SSH por VPN y permisos de administración; custodio y sustituto. Acceso a Docker equivale a acceso administrativo. |
| DNS y acceso de usuarios | Un nombre privado, por ejemplo `sigaa.interno.example`, resoluble desde LAN y VPN; ruta VPN disponible también en teléfonos. Sustituir todos los dominios de ejemplo. |
| TLS | Certificado y cadena completa confiables en navegadores y WebView Android, responsable de renovación y aviso de vencimiento. |
| Proxy | HTTPS en 443 hacia `127.0.0.1:8088`, manteniendo rutas y verificando la IP real del cliente. Si el proxy está en otra máquina, loopback no es accesible: usar una IP privada concreta y ACL que permita solo ese proxy. |
| Firewall | 443 solo desde LAN/VPN; SSH solo administradores/VPN. Mantener 5432, 8080, 8081, 9090 y 9093 sin publicación exterior; 8088 solo loopback o proxy autorizado. |
| Salidas | DNS, sincronización de reloj, SMTP y acceso controlado a registros de imágenes/repositorios para construir o actualizar. |
| SMTP | Host, puerto, STARTTLS/TLS, usuario, contraseña, remitente autorizado, límites de envío y contacto de soporte. La aplicación actual configura STARTTLS; un puerto 465 con TLS implícito requiere configuración adicional. |
| Copias externas | Montaje en otro equipo o almacenamiento con un dominio de fallo distinto, espacio/retención, cifrado, disponibilidad al arrancar y acceso UID/GID 10001. Una carpeta en el mismo disco no protege de perder ese disco. |
| Fuentes | Verdana autorizada para el servidor, directorio absoluto y permisos de lectura. No descargar fuentes licenciadas sin autorización. |
| Monitorización | Canal real de correo/webhook, responsables y horario de atención; monitor externo al host para detectar su caída completa. |
| Preproducción | Base, volúmenes, secretos, DNS y correo de pruebas separados. Usar información ficticia o anonimizada. |

Con Docker no hace falta instalar Java, Node, PostgreSQL ni LibreOffice en el host: las imágenes los contienen. Sí se necesitan JDK 21, Node compatible y Android SDK en la estación/CI que genera la APK.

## 2. Arquitectura prevista

```text
PC o Android -- LAN/VPN -- HTTPS:443 (proxy institucional)
                                    |
                              127.0.0.1:8088
                                    |
                                web / Caddy
                                    |
                               api:8080
                               /      \
                         db:5432     uploads persistente
                                    + respaldos cifrados externos
```

Todo el tráfico web y API utiliza el mismo dominio. La web emplea `/api` y Android empaqueta la URL base HTTPS de ese dominio, **sin añadir `/api`**. La API y la APK comparten la misma base de datos; la APK no sincroniza otra base local ni ofrece trabajo sin conexión.

No se necesita una IP pública ni abrir el sistema a Internet. Los teléfonos fuera de la institución necesitarán VPN y DNS institucional antes de abrir SIGAA. Los invitados que escaneen un QR también necesitan acceso a esa red.

Usar inicialmente una sola instancia API. Los códigos 2FA y límites de intentos tienen estado en memoria; replicar la API requiere revisar ese diseño y coordinar los planificadores.

## 3. Preparación de la versión

Responsable: mantenimiento de aplicación, antes de la instalación.

1. Utilizar la versión corregida y ejecutar CI, lint, auditoría UX, pruebas y build de producción. El registro de correcciones contiene los resultados locales y sus límites.
2. Conservar el lockfile: Angular 20.3.34, herramientas Angular 20.3.42 y Capacitor 8.6.0. Las auditorías npm de ejecución y OSV de Java deben seguir sin avisos al liberar. No usar `audit fix --force`; los avisos residuales de herramientas de desarrollo requieren seguimiento separado.
3. La imagen de construcción y CI usan Node 24. Validar las imágenes Docker limpias en CI/preproducción; no se ejecutaron contenedores Docker localmente durante estas correcciones.
4. Caddy ya separa API/uploads del fallback y el build evita el handler inline de CSS y las fuentes remotas. Se ensayó Caddy real con una API ficticia; repetir el acceso completo con Spring en el servidor.
5. Configurar `SIGAA_TRUSTED_PROXIES` con las IP/CIDR concretas del proxy vistas dentro del contenedor. Probar IP real y límites de acceso desde dos equipos, además de un encabezado falsificado.
6. Utilizar el commit aprobado de `main` en `https://github.com/jonyjj25/sigaa` y registrar su identificador. No desplegar directamente desde la antigua rama Android de `copia`. Conservar imágenes actuales y anteriores por identificador inmutable.
7. Configurar y ensayar el job de copia completa y la recuperación aislada descritos en [Recuperación completa](../ops/RECUPERACION_COMPLETA.md), además de los dumps de BD que mantiene la aplicación.

El esquema vigente es `backend/sigaa-api/src/main/resources/db/schema.sql`. No existe ejecución Flyway en la aplicación actual. CI valida la instalación del esquema canónico y se renombró su antiguo trabajo Flyway para reflejar ese comportamiento.

## 4. Preparar el servidor vacío

Responsable: infraestructura.

1. Instalar Docker Engine y Compose desde el repositorio oficial para el SO elegido. No mezclar las instrucciones de paquetes `docker.io` con las del repositorio Docker. Procedimiento: [instalación oficial en Ubuntu](https://docs.docker.com/engine/install/ubuntu/).
2. Instalar Git, Python 3, curl y util-linux (findmnt/flock), y preparar el proxy TLS aprobado. Configurar DNS, VPN, reloj y firewall antes de dar acceso a usuarios.
3. Verificar:

```bash
docker version
docker compose version
timedatectl status
df -h
```

4. Crear un directorio de instalación, por ejemplo `/srv/sigaa`, propiedad del operador autorizado, y descargar la versión aprobada del repositorio privado:

```bash
git clone --branch main https://github.com/jonyjj25/sigaa.git /srv/sigaa
cd /srv/sigaa
git checkout <ETIQUETA_O_COMMIT_APROBADO>
git status --short
umask 077
cp .env.example .env
chmod 600 .env
```

5. Montar el almacenamiento externo y probarlo. Confirmar con `findmnt -T /mnt/respaldo-institucional/sigaa` que la ruta corresponde al montaje previsto, no al disco principal tras un fallo de montaje. El arranque del servicio debe depender de ese montaje.
6. Preparar **solo el directorio dedicado a SIGAA** con dueño 10001:10001 y modo 0750. Revisar primero rutas, ACL y política NFS/SMB; no usar `chmod 777` ni cambiar permisos masivamente. Los volúmenes Docker nuevos heredan los directorios preparados en la imagen; los existentes requieren comprobar su propiedad.
7. Colocar Verdana autorizada en una ruta como `/srv/sigaa-fonts`, legible por UID 10001. Esta ruta está fuera del repositorio y se monta de solo lectura.

## 5. Configurar `.env`

No copiar el `.env` del equipo de desarrollo. Generar secretos nuevos, distintos por ambiente. Puede generar valores aleatorios con `openssl rand -base64 48`; la clave de respaldo requiere exactamente 32 bytes, por ejemplo `openssl rand -base64 32`, generados y custodiados desde una estación administrativa. No guardar la única copia de esa clave en el servidor.

Editar `.env` en el servidor:

```dotenv
WEB_BIND_ADDRESS=127.0.0.1
WEB_PORT=8088
# Valor de arranque seguro; sustituir por el par real del proxy antes de dar acceso.
SIGAA_TRUSTED_PROXIES=127.0.0.1/32
POSTGRES_DB=sigaa
POSTGRES_USER=sigaa_bootstrap
POSTGRES_PASSWORD=<CLAVE_ADMINISTRATIVA_ALEATORIA>
APP_DB_PASSWORD=<OTRA_CLAVE_RUNTIME>
BACKUP_DB_PASSWORD=<OTRA_CLAVE_BACKUP>
JWT_SECRET=<SECRETO_ALEATORIO_LARGO>
AUTH_COOKIE_SECURE=true
AUTH_COOKIE_SAME_SITE=Strict
APP_TIME_ZONE=America/Panama
QR_PUBLIC_URL=https://sigaa.interno.example
CORS_ALLOWED_ORIGINS=https://sigaa.interno.example,https://localhost

MAIL_HOST=<SMTP_INSTITUCIONAL>
MAIL_PORT=587
MAIL_USERNAME=<USUARIO_SMTP>
MAIL_PASSWORD=<CLAVE_SMTP>
MAIL_FROM=<REMITENTE_AUTORIZADO>
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true

BACKUP_ENCRYPTION_KEY_BASE64=<CLAVE_DE_32_BYTES_EN_BASE64>
BACKUP_EXTERNAL_DIR_HOST=/mnt/respaldo-institucional/sigaa
SIGAA_FONTS_DIR_HOST=/srv/sigaa-fonts
BACKUP_AUTOMATIC_ENABLED=true
BACKUP_SCHEDULE_CRON=0 0 2 * * *
BACKUP_RETENTION_DAYS=30
BACKUP_RETENTION_COPIES=30
BACKUP_RESTORE_TEST_ENABLED=true
BACKUP_RESTORE_TEST_CRON=0 0 4 * * SUN
ALERTMANAGER_CONFIG_FILE=./ops/monitoring/alertmanager.local.yml
```

No dejar ningún marcador `<...>`. `POSTGRES_USER` es el inicializador administrativo, no el usuario de ejecución: Compose conecta la API como `sigaa_runtime` y los respaldos como `sigaa_backup`.

`https://localhost` es el origen local del WebView Android, **no la dirección del servidor**. La APK llama al dominio HTTPS institucional con Bearer. Confirmar el origen real de la compilación final; no añadir orígenes HTTP de desarrollo por defecto. Si se incorpora iOS, añadir explícitamente `capacitor://localhost`; el validador admite ese origen nativo exacto.

Las variables `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SSL_*` y rutas locales de herramientas de `.env.example` sirven también al arranque directo sin Docker. Compose sobrescribe la conexión y rutas de la API con sus valores internos; no cambiarla para usar `postgres` como runtime. En esta arquitectura el proxy termina TLS y Spring recibe HTTP interno.

Compose ya fija zona JVM y PostgreSQL mediante `APP_TIME_ZONE`, y configura rotación local de logs en todos los servicios. No hace falta el override de hora/logs propuesto en la guía inicial. Si infraestructura agrega un override específico, custodiarlo y repetir sus mismos archivos `-f` y nombre de proyecto en todas las operaciones; el job de respaldo acepta `SIGAA_COMPOSE_OVERRIDE`.

Copiar una plantilla de `ops/monitoring/alertmanager.email.example.yml` o `alertmanager.webhook.example.yml` a la ruta configurada, completar su receptor real y limitar su lectura. No dejar el receptor vacío de la plantilla predeterminada.

## 6. Construcción e inicialización

Ejecutar desde `/srv/sigaa`. Los comandos siguientes usan un nombre fijo para evitar crear otros volúmenes accidentalmente:

```bash
docker compose -p sigaa --env-file .env -f docker-compose.prod.yml config --quiet
docker compose -p sigaa --env-file .env -f docker-compose.prod.yml build api web
docker compose -p sigaa --env-file .env -f docker-compose.prod.yml up -d db
docker compose -p sigaa --env-file .env -f docker-compose.prod.yml logs --tail=100 db
```

En una base **nueva y vacía**, el entrypoint PostgreSQL ejecuta primero los roles y después `schema.sql`. No ejecutar además el mismo esquema manualmente. Si falla la inicialización, detenerse y diagnosticar: un directorio de datos parcialmente inicializado puede impedir que los scripts se repitan. No resolverlo borrando un volumen con datos.

Antes de iniciar web, obtener la dirección del puente si el proxy HTTPS está en el mismo host:

```bash
docker network inspect sigaa_private --format '{{(index .IPAM.Config 0).Gateway}}'
```

Editar `SIGAA_TRUSTED_PROXIES` con esa IP y `/32` si es IPv4, siempre confirmando el par efectivo en la topología elegida. Para un proxy en otro equipo usar su IP efectiva, no todos los rangos privados.

Después:

```bash
docker compose -p sigaa --env-file .env -f docker-compose.prod.yml up -d api web
docker compose -p sigaa --env-file .env -f docker-compose.prod.yml --profile observability up -d
docker compose -p sigaa --env-file .env -f docker-compose.prod.yml ps
docker compose -p sigaa --env-file .env -f docker-compose.prod.yml logs --tail=100 api web
docker compose -p sigaa --env-file .env -f docker-compose.prod.yml exec -T api curl -fsS http://localhost:8081/actuator/health/readiness
docker compose -p sigaa --env-file .env -f docker-compose.prod.yml exec -T api curl -fsS http://localhost:8081/actuator/health
curl -I http://127.0.0.1:8088/
curl -i http://127.0.0.1:8088/api/config
```

`/api/config` debe devolver JSON de la API, nunca `index.html`: un 200 con HTML revela que el fallback de Caddy está interceptando la ruta. `readiness` comprueba BD y disco; el health general incluye SMTP. Un readiness correcto no acredita que se envíen correos. Investigar un health general 503; no ocultarlo desactivando el indicador. Docker marca un contenedor `unhealthy`, pero `restart: unless-stopped` no reinicia automáticamente un proceso solo por ese estado.

Comprobar en PostgreSQL `SHOW timezone;` y en la JVM el valor de `user.timezone`. Ejecutar `ops/scripts/Test-ProductionContainer.sh` según `ops/A08-DESPLIEGUE.md`, incluyendo PDF real y fuentes; requiere un entorno de prueba y conservar su salida.

**Base existente:** no ejecutar `schema.sql` sobre ella ni suponer que `up --build` la actualiza. Inventariar versión, objetos y propietarios; preparar SQL incremental específico, ensayarlo en una restauración y acordar ventana y rollback. Cambiar una contraseña en `.env` tampoco rota automáticamente un rol PostgreSQL ya creado.

## 7. HTTPS y acceso privado

Infraestructura configura el proxy local. Ejemplo de bloque Nginx, ajustado por el administrador a sus certificados y políticas:

```nginx
server {
    listen 443 ssl;
    server_name sigaa.interno.example;
    ssl_certificate /ruta/segura/fullchain.pem;
    ssl_certificate_key /ruta/segura/privkey.pem;
    client_max_body_size 50m;
    location / {
        proxy_pass http://127.0.0.1:8088;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_read_timeout 180s;
    }
}
```

El proxy reenvía las rutas completas, incluidas `/api` y `/uploads`, al contenedor web. No quitar el prefijo `/api`. Este ejemplo asume un único proxy de entrada; si existe balanceador adicional, infraestructura debe definir la cadena de confianza y la IP real.

Caddy confía solo en `SIGAA_TRUSTED_PROXIES`, procesa la cadena de derecha a izquierda y entrega a Spring la IP validada. Conserva la protección frente a `X-Forwarded-*` enviados por pares no confiables. Verificar la dirección efectiva después del proxy y Docker. No habilitar una whitelist hasta comprobar la IP efectiva y asegurar un canal de recuperación administrativa.

Comprobar desde PC y Android que HTTPS funciona sin advertencias. El `network_security_config` actual de Android no incorpora una CA privada ni confía explícitamente en certificados instalados por el usuario. Un certificado autofirmado o una CA instalada solo en ese almacén puede fallar en WebView. Resolverlo mediante certificado de una CA confiable o una estrategia institucional de MDM/confianza probada; no desactivar la validación TLS.

## 8. Preparar y distribuir Android

La APK existente `output/apk/SIGAA-1.0.7-debug.apk` es para pruebas LAN, no para distribución institucional. Incluye HTTP a una IP de desarrollo, firma Android Debug y modo depurable. No distribuirla a las 20 personas como versión final.

El usuario confirmó que todavía no hay dominio institucional ni keystore. No se emitió una APK de distribución con un dominio inventado. El build de verificación usa un dominio reservado y debe permanecer fuera de distribución.

Cuando servidores entregue el dominio HTTPS, crear el keystore en una estación administrativa con JDK 21 usando `ops/scripts/New-AndroidSigningKey.ps1`, o desde Android Studio. El script exige guardarlo fuera del repositorio y recibe la contraseña por el entorno. Guardar keystore, alias y contraseña en el gestor institucional y una segunda custodia offline; nunca en el chat ni en Git.

En una terminal administrativa con los secretos cargados desde ese gestor:

```powershell
cd frontend
npm.cmd ci
Remove-Item Env:SIGAA_ANDROID_ALLOW_HTTP -ErrorAction SilentlyContinue
$env:SIGAA_ANDROID_API_URL='https://DOMINIO-INSTITUCIONAL-REAL'
$env:SIGAA_ANDROID_KEYSTORE='C:\custodia\sigaa-institucional.p12'
$env:SIGAA_ANDROID_KEY_ALIAS='sigaa-institucional'
# SIGAA_ANDROID_STORE_PASSWORD y SIGAA_ANDROID_KEY_PASSWORD se cargan del gestor.
npm.cmd run android:release
```

La orden valida URL/secretos, compila, registra el origen, sincroniza y firma `android/app/build/outputs/apk/release/app-release.apk`. Gradle rechaza una release sin clave, con HTTP/mixed content o con recursos que no correspondan al origen registrado. No genera ni custodia claves automáticamente.

La versión corregida tiene `versionCode=9`, `versionName=1.0.8`. Incrementar el código en futuras distribuciones. Una instalación con firma Debug normalmente debe sustituirse antes de instalar la primera release; las actualizaciones posteriores deben conservar la firma institucional.

Verificar firma con `apksigner verify --print-certs <APK_RELEASE>`, paquete `com.sigaa.app`, `debuggable=false`, HTTPS empaquetado, `allowMixedContent=false` y ausencia de excepciones HTTP release. Registrar hash SHA-256, versión, commit y custodio del keystore.

Instalar primero en dos equipos piloto mediante el canal institucional/MDM. Comprobar VPN, DNS, TLS, login, 2FA, expiración/logout, cambio de contraseña, permisos, selección/carga/descarga de archivos, PDF/DOCX/CSV, QR, ubicación si se usa, botón Atrás y reanudación tras cerrar la app. Las descargas nativas ahora escriben en caché privada y ofrecen el diálogo Android para abrir o guardar; la web conserva su descarga habitual. La impresión móvil se entrega como archivo a una aplicación compatible. Verificar estos flujos en equipos físicos; la prueba automatizada no sustituye esa aceptación.

No hay un actualizador propio de APK ni push nativo configurado. Distribuir actualizaciones mediante MDM o un procedimiento institucional controlado. Cambiar frontend o URL empaquetada requiere nueva APK; cambios backend compatibles pueden desplegarse sin reinstalarla.

## 9. Copias y recuperación completa

La opción de respaldos de SIGAA mantiene dumps de PostgreSQL cifrados. La copia completa de desastre se programa aparte con `ops/scripts/full-backup.sh` y las unidades systemd incluidas. **Instalar y ensayar ese job es obligatorio**: incluye BD + uploads con escrituras detenidas y un manifiesto verificado. Su formato completo se llama SIGAAFULL1; los dumps antiguos mantienen SIGAABK1. Ver [procedimiento ejecutable de recuperación](../ops/RECUPERACION_COMPLETA.md).

Antes de producción debe existir un procedimiento automatizado y ensayado que cubra:

1. PostgreSQL: dump verificado, roles/provisionamiento y versión del esquema. Para recuperación entre dumps se necesita una solución adicional como archivado WAL/PITR.
2. Todo el volumen `uploads_data`: documentos, actas, tareas, fotografías y documentos generados de correspondencia/reportes presentes allí.
3. Manifiesto con versión/commit, imágenes, fecha y zona horaria, identificadores de los respaldos, checksums y correspondencia entre BD y archivos.
4. Configuración de despliegue, secretos, clave de cifrado y certificados en custodia separada, con permisos restringidos. Keystore Android bajo la custodia de aplicación.
5. Copia externa y al menos una copia inmutable u offline protegida de un borrado desde la cuenta del servidor.

Para este tamaño inicial, acordar como **objetivo propuesto** copia coordinada diaria y antes de cada cambio, retención diaria de 30 días, RPO de hasta 24 horas y RTO de hasta 4 horas. RPO es la pérdida máxima tolerada de cambios; RTO, el tiempo objetivo de recuperación. Deben aprobarse y medirse con un ensayo; no son prestaciones actuales garantizadas. Si no se tolera perder un día, aumentar frecuencia y revisar PITR y archivos.

La forma más sencilla de obtener consistencia inicial es una ventana breve de mantenimiento que impida escrituras mientras se toma el dump y la copia de archivos. El script incluido implementa esa ventana, reanudación y verificación del montaje; infraestructura debe instalarlo, configurar los avisos y comprobar su operación; una copia del volumen de PostgreSQL mientras está activo no equivale a un backup coherente.

Ensayo de desastre, siempre en un destino aislado:

1. Elegir un juego de copias completo y comprobar checksums, claves y acceso al destino externo.
2. Preparar un entorno nuevo con la misma versión PostgreSQL, roles y aplicación. Desactivar correo saliente y planificadores para no enviar avisos duplicados.
3. Usar `BackupRecoveryTool extract` mediante `ops/scripts/restore-isolated.sh` para una copia completa SIGAAFULL1. Para un dump antiguo SIGAABK1, usar `decrypt-db`, sabiendo que necesita los adjuntos de un respaldo independiente. La herramienta verifica el cifrado antes de publicar la extracción.
4. Restaurar en una BD vacía con el procedimiento PostgreSQL aprobado. No cargar encima el instalador `schema.sql`; el dump ya incluye estructura y datos. Restituir privilegios de runtime/backup porque los dumps se crean sin propietarios/ACL.
5. Restaurar el volumen de adjuntos del mismo juego y sus permisos 10001:10001. Arrancar la versión correspondiente con el nuevo entorno.
6. Comprobar login por rol y recuperar archivos de varios módulos; contrastar cantidades/checksums, numeración y fechas. Medir el tiempo total y documentar el resultado.
7. Solo después de aprobación operativa, aplicar el procedimiento al destino de producción y cambiar el acceso. Registrar las posibles pérdidas de datos desde la copia elegida.

El repositorio incluye utilidad offline de recuperación, copia completa programable, permisos SQL tras restauración y un Compose aislado sin salida de red. Se probaron archivos, cifrado y pg_dump/pg_restore reales en PostgreSQL temporal. Sigue pendiente el ensayo del proveedor con Docker, almacenamiento y documentos representativos.

## 10. Aceptación antes de abrir a los usuarios

No basta que todos los contenedores figuren activos. Registrar una prueba con Superadmin, Dirección, Subdirección, Secretaría, Jefe de Unidad y Miembro:

- Login/logout, token revocado tras logout, 2FA y recuperación por SMTP real; contraseña inicial sustituida antes de abrir el acceso.
- Un usuario solo consulta/modifica recursos permitidos; rechazo desde otras unidades y descarga privada sin token.
- Reunión → acta → acuerdo → tarea, cambios de estado, fechas y adjuntos; generación concurrente de números sin duplicados.
- Documentos compartidos/aprobaciones, notas/memos, reportes programados e inventario; errores visibles y reintentos.
- QR desde móvil en VPN y prueba de fechas cerca de medianoche de Panamá, además del vencimiento de sesión/QR.
- Web optimizada con CSP real y Android release en equipos físicos; archivos, PDF, impresión y permisos.
- Backup externo de BD y archivos, restauración completa y persistencia tras reiniciar; alertas de prueba recibidas y resueltas.
- Hasta 20 usuarios concurrentes con documentos representativos, midiendo latencia, RAM, disco, errores y conexiones. Ajustar recursos y límites a esa medición.
- Renovación TLS, reinicio del servidor y recuperación tras pérdida de montaje probados; gestión y BD inaccesibles desde redes no autorizadas.

No crear usuarios institucionales reales ni migrar datos definitivos hasta completar la aceptación y asignar los responsables.

## 11. Mantenimiento y responsables

| Frecuencia | Infraestructura | Mantenimiento de aplicación / responsable funcional |
| --- | --- | --- |
| Diario | Revisar disponibilidad, disco, montaje externo, copias, errores, correo y avisos. | Revisar fallos de procesos y atender incidentes de usuarios. |
| Semanal | Comprobar ensayo de restauración BD y receptor de alertas; crecimiento de disco/logs. | Revisar cuentas bloqueadas, reportes programados y errores de archivos/convertidor. |
| Mensual | Ventana de parches SO/Docker/imágenes, capacidad, TLS y VPN. | Auditar dependencias, permisos y bajas; probar actualización primero en preproducción y emitir APK si corresponde. |
| Trimestral y tras cambios grandes | Ensayo de restauración completa en otra máquina; medir RPO/RTO y revisar custodia de claves. | Validar flujos por rol y acceso a adjuntos después de restaurar. |
| Ante una baja/incidente | Retirar VPN/SSH cuando corresponda; conservar evidencia. | Desactivar cuenta, revocar sesiones y revisar auditoría. |

Prometheus/Alertmanager se activan con el perfil `observability`. Las reglas incluyen API, SMTP, disco, disponibilidad de uploads, dumps y copia completa. Configurar además un monitor externo para host, certificado y caída del propio monitor. Alojar todos los monitores en el mismo servidor no permite recibir su alerta cuando ese servidor se apaga. La regla de porcentaje HTTP ya fue corregida para no diluir errores a bajo tráfico.

Definir dos contactos como mínimo: infraestructura (SO, Docker, red, TLS, discos y copias) y aplicación (errores, versiones, migraciones, permisos y APK). Dirección decide quién puede acceder y valida los cambios funcionales. Acordar horario de soporte, prioridad de incidentes y sustitutos. No asumir mantenimiento automático por instalar Docker.

## 12. Actualización y retorno a la versión anterior

1. Preparar versión, pruebas, auditoría de dependencias, imágenes y SQL incremental en preproducción. Definir compatibilidad con la APK vigente.
2. Agendar ventana, informar a usuarios y guardar un juego coordinado de BD/adjuntos/configuración. Verificar que la versión anterior y sus imágenes siguen disponibles.
3. Impedir escrituras; registrar estado y versión. Aplicar únicamente el SQL aprobado si cambia el esquema.
4. Cambiar a la versión exacta aprobada; no usar `git pull` de una rama móvil como procedimiento de liberación.
5. Ejecutar `build`/`up -d` con **el mismo `-p sigaa`, `.env` y los archivos Compose aprobados**. Si se construye en CI, usar el archivo de imágenes aprobado en lugar de recompilar en servidor.
6. Revisar health general, readiness, logs y pruebas cortas por rol; reabrir el acceso y vigilar errores.
7. Si falla: mantener la ventana, volver a imágenes anteriores solo si el esquema es compatible. Si no lo es, restaurar el juego coordinado según el plan aprobado. Documentar los cambios que se perderían; no sobrescribir datos recientes automáticamente.

No usar `docker compose down -v` como mantenimiento: elimina volúmenes. No ejecutar limpiezas de imágenes/volúmenes sin inventario; pueden borrar la versión necesaria para volver atrás.

## Referencias verificadas

- [Docker Engine para Ubuntu](https://docs.docker.com/engine/install/ubuntu/): instalación y comportamiento de puertos frente al firewall.
- [Caddy: encabezados y proxies confiables](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy#defaults).
- [Android: firma de aplicaciones](https://developer.android.com/studio/publish/app-signing).
- [Aviso oficial Capacitor GHSA-rvm3-566m-v7fv](https://github.com/ionic-team/capacitor/security/advisories/GHSA-rvm3-566m-v7fv).
- [PostgreSQL 18: respaldo y restauración](https://www.postgresql.org/docs/18/backup.html).
- [Node.js: calendario de soporte](https://github.com/nodejs/Release#release-schedule).

Las recomendaciones de dimensionamiento, RPO/RTO y responsabilidades son propuestas para el escenario de 20 personas; requieren acuerdo institucional y pruebas en su servidor.
