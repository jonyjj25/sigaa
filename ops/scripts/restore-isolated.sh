#!/usr/bin/env bash
# Restaura solamente en un proyecto de recuperación nuevo, sin red de salida.
set -Eeuo pipefail
umask 077
repo="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo"
[[ $# == 2 ]] || { echo 'Uso: restore-isolated.sh /ruta/.env.recuperacion sigaa-full-FECHA.enc' >&2; exit 1; }
env_file="$(realpath -e -- "$1")"
name="$2"
[[ "$name" =~ ^sigaa-full-[0-9]{8}T[0-9]{6}Z\.enc$ ]] || { echo 'Nombre de copia inválido' >&2; exit 1; }
project="${SIGAA_RECOVERY_PROJECT:-sigaa-recuperacion}"
[[ "$project" =~ ^sigaa-recuperacion(-[a-z0-9]+)?$ ]] || { echo 'Solo se admiten proyectos de recuperación' >&2; exit 1; }
compose=(docker compose -p "$project" --env-file "$env_file" -f "$repo/ops/docker-compose.recovery.yml")
"${compose[@]}" config --quiet
"${compose[@]}" build api web
"${compose[@]}" up -d --wait db
count="$("${compose[@]}" exec -T db sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -X -qAt -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT count(*) FROM pg_tables WHERE schemaname = '\''public'\''"')"
[[ "$count" == 0 ]] || { echo 'La BD no está vacía; no se sobrescribe una recuperación anterior' >&2; exit 1; }
"${compose[@]}" run --rm --no-deps -T --entrypoint sh api -c 'test -z "$(find /app/uploads -mindepth 1 -print -quit)"' || {
  echo 'El volumen de adjuntos no está vacío; no se sobrescribe' >&2; exit 1;
}
target="/var/lib/sigaa/backups/restore-$(date -u +%Y%m%dT%H%M%SZ)"
"${compose[@]}" run --rm --no-deps -T --entrypoint java api \
  -Dloader.main=com.sigaa.ops.BackupRecoveryTool -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  extract "/var/lib/sigaa/backups-external/full/$name" "$target"
"${compose[@]}" run --rm --no-deps -T --entrypoint cat api "$target/database.dump" | \
  "${compose[@]}" exec -T db sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --exit-on-error --no-owner --no-privileges'
"${compose[@]}" exec -T db sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -X -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /opt/sigaa/grant-application-access.sql'
"${compose[@]}" run --rm --no-deps -T --entrypoint sh api -c 'test -z "$(find /app/uploads -mindepth 1 -print -quit)" && cp -a "$1/uploads/." /app/uploads/' sh "$target"
"${compose[@]}" up -d --wait api web
curl --fail --silent --show-error http://127.0.0.1:8089/api/config
echo 'Recuperación aislada lista en loopback:8089. SMTP externo está bloqueado. Valide roles y archivos antes de cualquier promoción.'
