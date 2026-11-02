#!/bin/sh
# Se ejecuta únicamente al inicializar un volumen PostgreSQL nuevo.
# Para una base existente se requiere el procedimiento explícito del runbook.
set -eu
umask 077
: "${POSTGRES_USER:?}" "${POSTGRES_DB:?}"
: "${APP_DB_PASSWORD:?}" "${BACKUP_DB_PASSWORD:?}"
# No pasar las credenciales por argumentos ni imprimir SQL que pueda contenerlas.
if ! PGOPTIONS="-c log_statement=none -c log_min_error_statement=panic" \
    psql --no-password --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    --set=ON_ERROR_STOP=1 --file=/opt/sigaa/provision-roles.sql >/dev/null 2>/dev/null; then
    echo 'No se completó el provisionamiento de roles de SIGAA; revise variables y privilegios de inicialización.' >&2
    exit 1
fi
