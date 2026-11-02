#!/bin/sh
# Ejecutar dentro de la API, en un despliegue de prueba con volúmenes desechables.
# No lee documentos ni datos existentes y no contacta SMTP.
set -eu
[ "$(id -u)" = 10001 ] || { echo 'FAIL: usuario efectivo incorrecto'; exit 1; }
grep -q 'CapEff:[[:space:]]*0000000000000000' /proc/self/status
grep -q 'NoNewPrivs:[[:space:]]*1' /proc/self/status
if touch /app/.sigaa-readonly-check 2>/dev/null; then
    rm -f /app/.sigaa-readonly-check
    echo 'FAIL: filesystem de aplicación escribible'; exit 1
fi
upload=$(mktemp /app/uploads/.sigaa-test.XXXXXX)
backup=$(mktemp /var/lib/sigaa/backups/.sigaa-test.XXXXXX)
external=$(mktemp /var/lib/sigaa/backups-external/.sigaa-test.XXXXXX)
trap 'rm -f "$upload" "$backup" "$external"' EXIT HUP INT TERM
printf 'TEST_ONLY\n' > "$upload"
cp "$upload" "$backup"
cp "$backup" "$external"
cmp "$upload" "$external"
work=$(mktemp -d /tmp/sigaa-document-test.XXXXXX)
printf '<html><body style="font-family:Verdana">Documento ficticio SIGAA</body></html>' > "$work/fixture.html"
fc-match --format='%{family}' Verdana | grep -qi Verdana || { echo 'FAIL: Verdana no disponible'; exit 1; }
timeout -k 5s 60s soffice --headless "-env:UserInstallation=file://$work/profile" \
    --convert-to pdf --outdir "$work" "$work/fixture.html" >/dev/null 2>&1
test -s "$work/fixture.pdf"
head -c 5 "$work/fixture.pdf" | grep -q '%PDF-'
pg_dump --version >/dev/null
pg_restore --version >/dev/null
curl --fail --silent http://127.0.0.1:8081/actuator/health/liveness | grep -q '"UP"'
curl --fail --silent http://127.0.0.1:8081/actuator/health/readiness | grep -q '"UP"'
echo 'PASS: UID, capabilities, filesystem, escrituras, Verdana, PDF y probes'
# El perfil y documento ficticios están únicamente en tmpfs y desaparecen al reiniciar.
