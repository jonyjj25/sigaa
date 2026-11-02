# A08: preparación de mínimo privilegio

Estos cambios preparan el despliegue; no migran el servidor ni los volúmenes actuales.
No ejecutar los procedimientos de una base existente sin inventario, ventana de mantenimiento y respaldo verificado.
Nunca publicar credenciales ni pasar contraseñas como argumentos de comandos.

## PostgreSQL

La API usa `sigaa_runtime`: CONNECT, USAGE del schema, SELECT/INSERT/UPDATE/DELETE de tablas de negocio,
USAGE/SELECT de secuencias y EXECUTE de funciones de negocio SECURITY INVOKER. No se conceden funciones SECURITY DEFINER automáticamente.
No posee objetos ni puede ejecutar DDL.
No tiene SUPERUSER, CREATEDB, CREATEROLE, BYPASSRLS, membresías, CREATE de schemas ni TEMP.

`sigaa_backup` lee tablas y secuencias para `pg_dump`. Tiene CREATEDB porque el servicio existente
crea una base temporal para verificar el respaldo. No puede escribir en SIGAA ni eliminar su base.
Cada base temporal restringe CONNECT de PUBLIC y se elimina al terminar. CREATEDB permite crear otras bases;
restringir esa capacidad a un nombre concreto exige otro diseño administrativo y queda documentado como riesgo residual.

El inicializador PostgreSQL mantiene una credencial administrativa que nunca se inyecta en la API.
Los dos roles técnicos se crean mediante `ops/postgres/010-sigaa-roles.sh` solo en un volumen nuevo.
Los roles existentes no cambian su contraseña; si tienen flags/membresías incompatibles, el script falla.
El archivo `backend/sigaa-api/src/main/resources/db/schema.sql` instala después el
esquema completo y concede los privilegios finales a esos roles.

Para una base existente:

1. Recoger metadatos con `inspect-privileges.sql` para la conexión actual, sin consultar registros personales.
2. Revisar propietarios, extensiones y otras aplicaciones que compartan `public`. El script requiere una base dedicada.
3. Con administrador autorizado y las dos credenciales técnicas en el entorno seguro, ejecutar `provision-roles.sql` en la base SIGAA.
4. Configurar APP_DB_PASSWORD y BACKUP_DB_PASSWORD distintas de la administrativa.
5. En una base vacía, ejecutar una sola vez `db/schema.sql`; en una base existente use un procedimiento de mantenimiento específico.
6. Arrancar la API con perfil prod. La guardia de arranque rechaza un runtime administrativo o propietario.
7. Repetir el inventario como cada rol y comprobar la aplicación, dump, copia cifrada y restauración en una base desechable.
8. Retirar el acceso habitual a la conexión anterior solo tras verificar todas las dependencias; no cambiar credenciales automáticamente.

## Contenedores y archivos

API y web usan UID/GID 10001, filesystem raíz de solo lectura, `cap_drop: ALL` y `no-new-privileges`.
La API escribe en `/app/uploads`, `/var/lib/sigaa/backups`, `/var/lib/sigaa/backups-external` y `/tmp`.
Los primeros tres deben pertenecer a 10001:10001 con permisos 0750 (archivos privados preferiblemente 0640).
Los volúmenes nuevos heredan los directorios preparados en la imagen. Los existentes y el montaje externo requieren
preparación explícita del operador: revisar rutas/propietarios antes de cambiar permisos; no usar chmod 777 ni cambios masivos.

`/tmp` es tmpfs privado del UID/GID 10001, modo 0700, limitado a 512 MB y nosuid/nodev.
LibreOffice usa perfiles temporales del mismo usuario. Su espacio y la conversión deben comprobarse con documentos representativos.
Web escucha 8080 dentro del contenedor y conserva el puerto externo 8088 en loopback.
Caddy tiene administración deshabilitada y tmpfs privados 0750 en `/data` y `/config`.

Montar `SIGAA_FONTS_DIR_HOST` como ruta absoluta con Verdana legalmente disponible, de solo lectura.
No se incluyen ni se descargan fuentes licenciadas. La presencia de un directorio no acredita que Verdana esté instalada.

## Validación reproducible

La suite aislada necesita JDK 21 y binarios locales PostgreSQL. Compila en `target/security-build`,
separado de `target/classes` observado por DevTools. La cobertura queda en `target/security-build/site/jacoco`.
Ejecutar Maven con:
`-Dsigaa.security.validation=true -Djunit.jupiter.extensions.autodetection.enabled=true`
`-Dsigaa.test.postgres.bin=<bin> -Dsigaa.test.workdir=<ruta absoluta target/security-workspace>`.
Crear previamente ese directorio. La extensión crea un cluster, instala el esquema
canónico en una base desechable y prueba los roles restringidos. No carga `.env` ni
se conecta a bases existentes.

En un Compose aislado con volúmenes desechables y fuentes autorizadas, ejecutar el script
`Test-ProductionContainer.sh` dentro de la API mediante `docker compose exec -T api sh -s`.
El operador debe suministrar el contenido del script por entrada estándar. Verifica UID, capabilities, escrituras, PDF real, Verdana y probes.
Reiniciar API/web y confirmar que un archivo ficticio de prueba conservado explícitamente en uploads sigue disponible
solo por el endpoint autorizado. Esta comprobación de reinicio/persistencia no se realiza en datos reales.

`Test-ProductionReadiness.ps1` valida configuración, no acredita TLS, permisos efectivos ni funcionamiento.
La copia local `.env` y secretos reales permanecen fuera de la revisión automatizada.
El directorio de fuentes, permisos existentes, TLS institucional, SMTP y receptores de alertas requieren evidencia del despliegue.
SMTP pendiente mantiene health global DOWN/503 y probes UP; no desactivar el indicador.

## Condiciones previas a producción

El esquema canónico contiene una única cuenta Superadmin para la puesta en marcha.
Cambiar su contraseña por un canal autorizado antes de publicar el sistema.
Hasta completar estas comprobaciones, A08 queda implementado y probado en PostgreSQL aislado, pero no cerrado en el despliegue real.
