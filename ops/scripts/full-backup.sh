#!/usr/bin/env bash
# Copia coherente: detener escrituras, pg_dump + uploads cifrados y reanudar el estado previo.
set -Eeuo pipefail
umask 077
repo="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo"
project="${SIGAA_COMPOSE_PROJECT:-sigaa}"
[[ "$project" =~ ^[a-z0-9][a-z0-9_-]*$ ]] || { echo 'Nombre de proyecto inválido' >&2; exit 1; }
env_file="${SIGAA_ENV_FILE:-$repo/.env}"
compose=(docker compose -p "$project" --env-file "$env_file" -f "$repo/docker-compose.prod.yml")
if [[ -n "${SIGAA_COMPOSE_OVERRIDE:-}" ]]; then compose+=(-f "$SIGAA_COMPOSE_OVERRIDE"); fi
for command in docker python3 findmnt flock; do command -v "$command" >/dev/null || { echo "Falta $command" >&2; exit 1; }; done
exec 9>"${SIGAA_BACKUP_LOCK_DIR:-/run/lock}/${project}-full-backup.lock"
flock -n 9 || { echo 'Ya hay una copia completa en curso' >&2; exit 1; }
"${compose[@]}" config --quiet
# Compose resuelve rutas/variables; solo se imprime la ruta del montaje, nunca el entorno.
external="$("${compose[@]}" config --format json | python3 -c 'import json,sys; c=json.load(sys.stdin); print(next(v["source"] for v in c["services"]["api"]["volumes"] if v["target"]=="/var/lib/sigaa/backups-external"))')"
[[ -d "$external" ]] || { echo 'No existe el destino externo' >&2; exit 1; }
external="$(realpath -e -- "$external")"
backup_source="$(findmnt -n -T "$external" -o SOURCE)"
primary_source="$(findmnt -n -T "$repo" -o SOURCE)"
[[ -n "$backup_source" && "$backup_source" != "$primary_source" && "$(findmnt -n -T "$external" -o TARGET)" != / ]] || {
  echo 'El respaldo no corresponde a un montaje externo al disco de instalación' >&2; exit 1;
}
running="$("${compose[@]}" ps --services --status running)"
resume=()
for service in api web; do
  if printf '%s\n' "$running" | grep -qx "$service"; then resume+=("$service"); fi
done
if ! printf '%s\n' "$running" | grep -qx db; then echo 'PostgreSQL debe estar en ejecución' >&2; exit 1; fi
restore_state() {
  status=$?
  trap - EXIT
  if (( ${#resume[@]} )); then "${compose[@]}" start "${resume[@]}" || status=1; fi
  exit "$status"
}
trap restore_state EXIT
trap 'exit 143' TERM
trap 'exit 130' INT
if (( ${#resume[@]} )); then "${compose[@]}" stop -t 120 "${resume[@]}"; fi
# El helper conserva montajes/permisos de la API y no arranca Spring ni sus planificadores.
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
release="$(git rev-parse HEAD 2>/dev/null || printf 'unspecified')"
if [[ -n "$(git status --porcelain 2>/dev/null)" ]]; then release+="-dirty"; fi
"${compose[@]}" run --rm --no-deps -T -e "SIGAA_RELEASE_ID=$release" --entrypoint java api \
  -Dloader.main=com.sigaa.ops.BackupRecoveryTool -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  create "/var/lib/sigaa/backups-external/full/sigaa-full-${stamp}.enc"
[[ -s "$external/full/sigaa-full-${stamp}.enc" ]] || { echo 'No se publicó la copia completa' >&2; exit 1; }
# Retención únicamente de los archivos generados por este script; conservar al menos dos.
mapfile -t copies < <(find "$external/full" -maxdepth 1 -type f -name 'sigaa-full-*.enc' -printf '%f\n' | sort -r)
for ((index=2; index<${#copies[@]}; index++)); do
  name="${copies[index]}"
  [[ "$name" =~ ^sigaa-full-[0-9]{8}T[0-9]{6}Z\.enc$ ]] || continue
  if [[ -n "$(find "$external/full/$name" -maxdepth 0 -type f -mtime +30 -print)" ]]; then
    rm -- "$external/full/$name"
  fi
done
echo "Copia completa verificada: sigaa-full-${stamp}.enc"
