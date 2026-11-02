#!/usr/bin/env bash
set -Eeuo pipefail
base="${SIGAA_WEB_URL:-http://127.0.0.1:8088}"
curl --fail --silent --show-error "$base/api/config" | python3 -c 'import json,sys; assert "qrBaseUrl" in json.load(sys.stdin)'
status="$(curl --silent --output /dev/null --write-out '%{http_code}' "$base/api/usuarios")"
[[ "$status" == 401 ]] || { echo "API privada devolvió $status en lugar de 401" >&2; exit 1; }
status="$(curl --silent --output /dev/null --write-out '%{http_code}' "$base/uploads/documentos/fixture.pdf")"
[[ "$status" == 401 || "$status" == 403 ]] || { echo 'Se perdió el control de acceso a archivos' >&2; exit 1; }
curl --fail --silent --show-error "$base/dashboard" | python3 -c 'import sys; assert "<app-root>" in sys.stdin.read()'
echo 'API JSON, recursos privados y fallback Angular correctos a través del contenedor web.'
