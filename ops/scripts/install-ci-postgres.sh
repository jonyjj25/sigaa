#!/usr/bin/env bash
# Solo en el runner desechable: la suite crea sus propios clústeres y puertos.
# Fuente: https://www.postgresql.org/download/linux/ubuntu/
set -Eeuo pipefail
[[ "${GITHUB_ACTIONS:-}" == true ]] || { echo 'Este instalador es exclusivo de GitHub Actions.' >&2; exit 1; }
pg_bin=/usr/lib/postgresql/18/bin
if [[ ! -x "$pg_bin/postgres" ]]; then
  . /etc/os-release
  [[ "$ID" == ubuntu && "$VERSION_CODENAME" == noble ]] || { echo 'Se requiere el runner Ubuntu 24.04.' >&2; exit 1; }
  sudo apt-get update
  sudo apt-get install --yes postgresql-common ca-certificates
  sudo /usr/share/postgresql-common/pgdg/apt.postgresql.org.sh -y
  sudo apt-get update
  sudo apt-get install --yes postgresql-18
fi
for tool in initdb postgres psql pg_dump pg_restore; do
  [[ -x "$pg_bin/$tool" ]] || { echo "No está disponible PostgreSQL 18: $tool" >&2; exit 1; }
done
"$pg_bin/postgres" --version | grep -E 'PostgreSQL\) 18\.'
