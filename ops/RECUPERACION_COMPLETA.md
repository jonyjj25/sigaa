# Copias completas y recuperación de SIGAA

El respaldo en la interfaz es una copia de **solo PostgreSQL** en formato SIGAABK1. Se conserva por compatibilidad. La copia de desastre completa se ejecuta con `ops/scripts/full-backup.sh`: detiene API/web, genera el dump, incluye `uploads`, cifra y autentica el conjunto, y reanuda únicamente los servicios que estaban activos. No permite copiar a una ruta que aparente ser externa pero pertenezca al disco de instalación.

El archivo `sigaa-full-<UTC>.enc` contiene `database.dump`, `uploads/` y `manifest.properties` con checksums SHA-256, fecha y versión. Su formato SIGAAFULL1 usa AES-256-GCM en bloques de 1 MiB, con contador autenticado y bloque final obligatorio; no requiere cargar el respaldo entero en memoria. La clave se recibe por `BACKUP_ENCRYPTION_KEY_BASE64`, nunca en argumentos. Custodiarla en el gestor institucional y en una copia offline independiente. Una copia cifrada sin su clave no se puede recuperar.

## Programar y verificar la copia

1. Probar en preproducción el montaje externo y sus permisos UID/GID 10001. Completar `.env` y `SIGAA_TRUSTED_PROXIES`. La copia detendrá escrituras durante unos minutos; acordar esa ventana nocturna.
2. Ajustar las rutas de las unidades de `ops/systemd`, especialmente `RequiresMountsFor`, instalación, nombre de proyecto y `.env`. Instalar y habilitar también `sigaa.service`: al arrancar el host hace `up` y recupera un servicio que hubiera quedado detenido por una copia interrumpida. Coordinar el montaje NFS/SMB **antes del arranque de Docker y sus contenedores con autoreinicio**, no solo antes del timer. Si se usa un override Compose, añadir `Environment=SIGAA_COMPOSE_OVERRIDE=/ruta/al/override.yml` al job y los mismos `-f` a `sigaa.service`.
3. Copiar las unidades al directorio de systemd, ejecutar `systemctl daemon-reload` y probar `systemctl start sigaa-full-backup.service`.
4. Comprobar `journalctl -u sigaa-full-backup.service`, que API/web recuperaron su estado, que existe la copia externa y que `full/last-success` contiene un timestamp reciente. Si falla la copia, el trap intenta reanudar los servicios y systemd marca fallo. Configurar el recolector institucional de logs para que el evento de `sigaa-operation-alert@.service` llegue al responsable; `logger` por sí solo no envía correo.
5. Solo tras un ensayo completo, habilitar `systemctl enable --now sigaa-full-backup.timer`. Ejecuta a las 02:10 de Panamá, también tras una ejecución perdida. Mantiene al menos dos copias y elimina solo copias propias con más de 30 días.
6. Prometheus alerta por copia completa atrasada, disco bajo, uploads y SMTP. Configurar un monitor **fuera del servidor** para caída del host, TLS y fallo del job; conservar además copia inmutable/offline. Un montaje distinto del disco local no demuestra por sí solo otra ubicación física: infraestructura debe acreditarlo.

## Extraer una copia completa en un entorno nuevo

No ejecutar estos pasos contra la BD/volúmenes habituales para probar. Se incluye `ops/docker-compose.recovery.yml`: inicializa solo roles, sin instalar el esquema; crea volúmenes distintos, una red sin salida externa y web en loopback:8089. SMTP externo y respaldos programados están deshabilitados. Usar exactamente la versión de aplicación y PostgreSQL registrada en la copia.

Prepare un `.env.recuperacion` protegido, fuera del repositorio, con `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `APP_DB_PASSWORD`, `BACKUP_DB_PASSWORD` y `JWT_SECRET` **nuevos**, `BACKUP_EXTERNAL_DIR_HOST` apuntando a una copia de los archivos cifrados, `BACKUP_ENCRYPTION_KEY_BASE64` con la clave de esa copia, y `SIGAA_FONTS_DIR_HOST` con sus fuentes autorizadas. El secreto de cifrado debe coincidir para descifrar; las credenciales de ejecución se renuevan.

La ruta automatizada con guardas de BD/adjuntos vacíos es:

```bash
bash ops/scripts/restore-isolated.sh /ruta/segura/.env.recuperacion sigaa-full-20261101T071000Z.enc
```

Sustituya el nombre de ejemplo por una copia existente. El script admite solo nombres de proyecto `sigaa-recuperacion` o `sigaa-recuperacion-<sufijo>`, comprueba que no haya tablas ni adjuntos previos y se detiene ante errores. Si falla tras una restauración parcial, no lo reejecute sobre la misma BD: conserve el diagnóstico y prepare otro proyecto de recuperación vacío. No borre volúmenes institucionales.

El procedimiento manual equivalente para administradores es:

1. Aprovisionar únicamente roles PostgreSQL con `ops/postgres/provision-roles.sql`, según `ops/A08-DESPLIEGUE.md`. Para restaurar un dump con esquema **no instalar antes `schema.sql`**. Preparar una BD vacía dedicada en el destino aislado. El administrador debe separar la inicialización/restauración de la de un primer arranque normal de Compose.
2. Montar una copia del respaldo externo y proporcionar la clave por el entorno seguro del helper, sin ponerla en una orden de shell.
3. Con API/web detenidos, ejecutar el helper de la misma imagen. Ejemplo conceptual usando el Compose aislado y sus archivos aprobados:

```bash
docker compose -p sigaa-recuperacion --env-file /ruta/segura/.env.recuperacion -f ops/docker-compose.recovery.yml run --rm --no-deps -T --entrypoint java api \
  -Dloader.main=com.sigaa.ops.BackupRecoveryTool -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  extract /var/lib/sigaa/backups-external/full/<COPIA>.enc /var/lib/sigaa/backups/recuperacion-nueva
```

El directorio de salida debe ser nuevo. Se valida el tag de todos los bloques, la terminación del archivo, las rutas y los checksums antes de publicar el directorio extraído. Una clave incorrecta o un archivo truncado/corrupto fallan sin sobrescribir el destino.

4. Restaurar `recuperacion-nueva/database.dump` con `pg_restore --exit-on-error --no-owner --no-privileges` en la BD vacía, con la credencial administrativa por mecanismo seguro. Ejecutar `ops/postgres/grant-application-access.sql` como propietario restaurador para devolver DML/lectura a runtime/backup sin otorgar DDL ni funciones SECURITY DEFINER. El dump no contiene los propietarios ni las contraseñas de los roles.
5. Copiar el contenido de `recuperacion-nueva/uploads/` al **volumen de adjuntos nuevo y vacío** y comprobar UID/GID 10001. No mezclar juegos de copias ni sobrescribir adjuntos actuales sin una decisión de recuperación aprobada.
6. Arrancar la aplicación en esa red aislada. Comprobar health, esquema, login por roles, numeración, fechas y apertura de adjuntos en varios módulos, incluidos archivos grandes. Registrar cantidades y checksums del manifiesto y medir tiempo total.
7. Registrar el ensayo y el RPO/RTO conseguido. La promoción del destino recuperado y cualquier sustitución de datos institucionales requieren la decisión del responsable del incidente.

Para un respaldo antiguo SIGAABK1, la misma herramienta acepta `decrypt-db <archivo.enc> <dump-nuevo>`. Ese archivo **no contiene adjuntos**; hace falta su copia coordinada independiente.

## Custodia y pruebas

Las pruebas automatizadas incluyen archivos binarios de varios bloques, recuperación exacta, clave errónea, corrupción, truncado, rutas maliciosas y rechazo de sobrescritura. No sustituyen el ensayo con `pg_dump`/`pg_restore`, el disco externo, la imagen real y documentos representativos del servidor. No publicar producción hasta documentar ese ensayo y un canal de avisos funcional.
