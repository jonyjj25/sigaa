# Pasos para desplegar SIGAA en un servidor privado

> **Actualización del 1/11/2026:** no usar este procedimiento anterior como una
> lista completa de requisitos. La guía vigente, adaptada a 20 personas por
> LAN/VPN, está en [Servidor, instalación y mantenimiento](docs/GUIA_SERVIDOR_Y_MANTENIMIENTO_2026-11-01.md).
> Consulte primero los bloqueos del [informe de revisión](docs/REVISION_PRODUCCION_2026-11-01.md).
> Su estado posterior figura en [Correcciones y validaciones](docs/CORRECCIONES_PRODUCCION_2026-11-01.md).
> Entre las omisiones de este documento están las fuentes obligatorias, la copia
> automatizada de adjuntos, la restauración completa, la hora JVM/BD y la confianza
> entre proxies. El texto anterior se conserva debajo como referencia histórica.

Esta configuracion no publica SIGAA en Internet. PostgreSQL y la API solo se
comunican en la red interna de Docker. La interfaz web queda por defecto en
`127.0.0.1:8088` del servidor; se accede mediante VPN, red institucional o un
proxy inverso institucional.

## 1. Requisitos que debe proporcionar infraestructura

- Servidor Linux (Ubuntu 22.04+ o Debian 12+ recomendado), acceso SSH de un
  administrador y espacio para base de datos, archivos y respaldos.
- Docker Engine y el plugin Docker Compose.
- Direccion o nombre DNS interno para SIGAA, si la institucion usa DNS privado.
- Acceso VPN o reglas de firewall que permitan la red institucional autorizada.
- Certificado TLS institucional o un proxy institucional que entregue HTTPS.
- Credenciales SMTP institucionales: host, puerto, cifrado, usuario, clave y
  remitente autorizado.
- Politica de respaldos: destino externo, frecuencia, retencion y responsable.

No se deben solicitar ni abrir puertos publicos para PostgreSQL (5432), API
(8080) ni para la aplicacion web. SSH debe limitarse a administradores o VPN.

## 2. Preparar el servidor

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-plugin git
sudo usermod -aG docker "$USER"
# Cerrar sesion y volver a entrar antes de usar Docker sin sudo.

git clone <URL_DEL_REPOSITORIO_PRIVADO> sigaa
cd sigaa
cp .env.example .env
chmod 600 .env
```

## 3. Configurar las variables del servidor

Edite `.env` exclusivamente en el servidor. Nunca lo suba a Git.

```properties
# Acceso privado: localhost para proxy local; una IP LAN concreta solo si el
# firewall ya permite unicamente la red institucional.
WEB_BIND_ADDRESS=127.0.0.1
WEB_PORT=8088

POSTGRES_DB=sigaa
POSTGRES_USER=sigaa_app
POSTGRES_PASSWORD=<clave-larga-aleatoria>
JWT_SECRET=<secreto-aleatorio-de-48-o-mas-caracteres>
AUTH_COOKIE_SECURE=true
AUTH_COOKIE_SAME_SITE=Strict

# Debe ser la URL interna o VPN que usaran los usuarios para abrir SIGAA.
QR_PUBLIC_URL=https://sigaa.interno.institucion
# No incluya localhost ni IPs de desarrollo. Separe varios orígenes con coma.
CORS_ALLOWED_ORIGINS=https://sigaa.interno.institucion

MAIL_HOST=<smtp.institucion>
MAIL_PORT=587
MAIL_USERNAME=<usuario-smtp>
MAIL_PASSWORD=<clave-smtp>
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true

# Genere esta clave fuera del servidor y custódiela por separado.
BACKUP_ENCRYPTION_KEY_BASE64=<32-bytes-aleatorios-en-base64>
# Ruta absoluta de otro disco o de un montaje NFS/SMB.
BACKUP_EXTERNAL_DIR_HOST=/mnt/respaldo-institucional/sigaa

# Archivo local ignorado por Git con correo o webhook institucional.
ALERTMANAGER_CONFIG_FILE=./ops/monitoring/alertmanager.local.yml
```

Genere contrasenas y secretos con `openssl rand -base64 48`. Mantenga tambien
los valores de respaldo y LibreOffice incluidos en `.env.example`.

Genere la clave de respaldos en una estación administrativa con
`openssl rand -base64 32` o con
`ops/scripts/New-BackupEncryptionKey.ps1`. Guarde la clave en el gestor
institucional de secretos y en una custodia offline; no deje la única copia en
el servidor. Monte y pruebe el disco/NFS/SMB externo antes de iniciar SIGAA.

Copie `ops/monitoring/alertmanager.email.example.yml` o
`alertmanager.webhook.example.yml` como `alertmanager.local.yml`, complete los
valores institucionales y limite su lectura al administrador.

## 4. Validar y levantar la aplicacion

```bash
docker compose --env-file .env -f docker-compose.prod.yml config --quiet
docker compose -f docker-compose.prod.yml up -d --build
docker compose --profile observability -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f api web
```

La primera inicializacion crea la base y PostgreSQL ejecuta una sola vez el
archivo canonico `db/schema.sql`. Espere que la API supere la validacion de
Hibernate antes de probar el sistema.

## 5. Dar acceso solo a la red privada

El contenedor web escucha en `127.0.0.1:8088`; elija una sola alternativa:

1. **VPN (recomendada):** configure el VPN institucional y un proxy inverso
   local que entregue HTTPS hacia `http://127.0.0.1:8088`.
2. **Proxy institucional:** configure Nginx, Apache, balanceador o firewall de
   aplicaciones para publicar HTTPS internamente y reenviar a
   `http://127.0.0.1:8088`.
3. **Solo LAN controlada:** cambie `WEB_BIND_ADDRESS` por la IP privada del
   servidor y permita ese puerto unicamente desde las subredes institucionales.
   Para este caso siga usando HTTPS con certificado institucional.

El proxy debe reenviar `/`, `/api` y `/uploads` al mismo puerto web. No debe
enviar trafico directamente a `api:8080` desde fuera de Docker.

## 6. Pruebas de aceptacion

Con una cuenta de prueba, valide:

- Inicio y cierre de sesion, roles y permisos.
- Carga/descarga de documentos y fotografia de perfil.
- Creacion de reunion, asistencia y QR con la URL interna correcta.
- Envio de recuperacion de contrasena y notificaciones por correo.
- Creacion de un respaldo y recuperacion de un archivo de prueba.
- Recepción y resolución de una alerta de prueba por el canal institucional.
- Pérdida simulada del disco principal, confirmando acceso a la copia externa.

Revise que `docker compose ... ps` muestre los tres servicios activos y que no
haya errores de esquema, SMTP ni permisos de archivos en los logs.

En una estación Windows también puede ejecutar
`ops/scripts/Test-ProductionReadiness.ps1 -EnvFile .env`. La validación falla si
detecta secretos de ejemplo, una clave de tamaño incorrecto, una ruta externa
relativa, Alertmanager sin receptor o un Compose inválido.

## 7. Operacion y actualizaciones

Antes de actualizar, copie fuera del servidor un respaldo PostgreSQL y los
volumenes de archivos. Despues:

```bash
git pull
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml logs -f api
```

No sustituya el esquema de una base existente con el instalador inicial. Cualquier
cambio de estructura requiere una ventana de mantenimiento y un plan de restauracion
probado. Mantenga Docker, sistema operativo, Java y dependencias actualizados.
