[CmdletBinding()]
param(
    [string]$EnvFile = '.env',
    [switch]$SkipDocker
)

$ErrorActionPreference = 'Stop'
$workspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$envPath = if ([System.IO.Path]::IsPathRooted($EnvFile)) {
    [System.IO.Path]::GetFullPath($EnvFile)
} else {
    [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $EnvFile))
}
$errors = [System.Collections.Generic.List[string]]::new()
$values = @{}

if (-not (Test-Path -LiteralPath $envPath -PathType Leaf)) {
    throw 'No se encontró el archivo de entorno solicitado'
}

foreach ($line in Get-Content -LiteralPath $envPath) {
    $trimmed = $line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith('#') -or -not $trimmed.Contains('=')) { continue }
    $name, $value = $trimmed.Split('=', 2)
    $values[$name.Trim()] = $value.Trim().Trim('"').Trim("'")
}

function Require-Value([string]$Name) {
    $value = $values[$Name]
    if (-not $value -or $value -match 'replace|example|your-domain|<.+>') {
        $script:errors.Add("$Name no tiene un valor de producción")
    }
    return $value
}

$jwt = Require-Value 'JWT_SECRET'
if ($jwt -and $jwt.Length -lt 32) { $errors.Add('JWT_SECRET debe tener al menos 32 caracteres') }
Require-Value 'POSTGRES_PASSWORD' | Out-Null
$runtimePassword = Require-Value 'APP_DB_PASSWORD'
$backupPassword = Require-Value 'BACKUP_DB_PASSWORD'
$databasePasswords = @($values['POSTGRES_PASSWORD'], $runtimePassword, $backupPassword)
if (($databasePasswords | Select-Object -Unique).Count -ne 3) {
    $errors.Add('Las tres conexiones PostgreSQL deben utilizar credenciales diferentes')
}
if ($values['POSTGRES_DB'] -in @('postgres', 'template0', 'template1')) {
    $errors.Add('POSTGRES_DB debe ser una base dedicada a SIGAA')
}
$fonts = Require-Value 'SIGAA_FONTS_DIR_HOST'
if ($fonts -and (-not [System.IO.Path]::IsPathRooted($fonts) -or -not (Test-Path -LiteralPath $fonts -PathType Container))) {
    $errors.Add('SIGAA_FONTS_DIR_HOST debe ser un directorio absoluto existente con las fuentes Verdana autorizadas')
}

$qrPublicUrl = Require-Value 'QR_PUBLIC_URL'
if ($qrPublicUrl -and $qrPublicUrl -notmatch '^https://') {
    $errors.Add('QR_PUBLIC_URL debe usar HTTPS en producción')
}

$corsOrigins = Require-Value 'CORS_ALLOWED_ORIGINS'
if ($corsOrigins) {
    foreach ($origin in $corsOrigins.Split(',').Trim()) {
        if ($origin -notmatch '^https://' -and $origin -ne 'capacitor://localhost') {
            $errors.Add('CORS_ALLOWED_ORIGINS contiene un origen no HTTPS')
        }
    }
}

$trustedProxies = Require-Value 'SIGAA_TRUSTED_PROXIES'
foreach ($proxy in ($trustedProxies -split '\s+' | Where-Object { $_ })) {
    $parts = $proxy.Split('/')
    $address = $null
    if ($parts.Count -gt 2 -or -not [Net.IPAddress]::TryParse($parts[0], [ref]$address)) {
        $errors.Add('SIGAA_TRUSTED_PROXIES debe contener IPs/CIDR concretos separados por espacios')
        continue
    }
    if ($parts.Count -eq 2) {
        $prefix = 0
        $max = if ($address.AddressFamily -eq [Net.Sockets.AddressFamily]::InterNetwork) { 32 } else { 128 }
        if (-not [int]::TryParse($parts[1], [ref]$prefix) -or $prefix -le 0 -or $prefix -gt $max) {
            $errors.Add('SIGAA_TRUSTED_PROXIES tiene un prefijo CIDR inválido o demasiado amplio')
        }
    }
}

$cookieSecure = Require-Value 'AUTH_COOKIE_SECURE'
if ($cookieSecure -and $cookieSecure.ToLowerInvariant() -ne 'true') {
    $errors.Add('AUTH_COOKIE_SECURE debe ser true en producción')
}
$sameSite = Require-Value 'AUTH_COOKIE_SAME_SITE'
if ($sameSite -and $sameSite -notmatch '^(?i:Strict|Lax)$') {
    $errors.Add('AUTH_COOKIE_SAME_SITE debe ser Strict o Lax')
}

Require-Value 'MAIL_HOST' | Out-Null
Require-Value 'MAIL_FROM' | Out-Null

$backupKey = Require-Value 'BACKUP_ENCRYPTION_KEY_BASE64'
if ($backupKey) {
    try {
        if ([Convert]::FromBase64String($backupKey).Length -ne 32) {
            $errors.Add('BACKUP_ENCRYPTION_KEY_BASE64 no representa exactamente 32 bytes')
        }
    } catch {
        $errors.Add('BACKUP_ENCRYPTION_KEY_BASE64 no es Base64 válido')
    }
}

$external = Require-Value 'BACKUP_EXTERNAL_DIR_HOST'
if ($external) {
    if (-not [System.IO.Path]::IsPathRooted($external)) {
        $errors.Add('BACKUP_EXTERNAL_DIR_HOST debe ser una ruta absoluta de otro disco o montaje NFS/SMB')
    } else {
        $resolvedExternal = [System.IO.Path]::GetFullPath($external)
        if ($resolvedExternal.StartsWith($workspace, [System.StringComparison]::OrdinalIgnoreCase)) {
            $errors.Add('BACKUP_EXTERNAL_DIR_HOST no puede estar dentro del repositorio')
        }
    }
}

$alertConfig = Require-Value 'ALERTMANAGER_CONFIG_FILE'
if ($alertConfig) {
    $alertPath = if ([System.IO.Path]::IsPathRooted($alertConfig)) {
        $alertConfig
    } else {
        Join-Path (Split-Path -Parent $envPath) $alertConfig
    }
    if (-not (Test-Path -LiteralPath $alertPath -PathType Leaf)) {
        $errors.Add('No existe ALERTMANAGER_CONFIG_FILE')
    } else {
        $alertText = Get-Content -LiteralPath $alertPath -Raw
        if ($alertText -notmatch '(email_configs|webhook_configs):') {
            $errors.Add('Alertmanager no tiene un receptor email_configs o webhook_configs')
        }
        if ($alertText -match '<[^>]+>|replace-with|example\.com') {
            $errors.Add('Alertmanager aún contiene valores de plantilla')
        }
    }
}

if (-not $SkipDocker) {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        $errors.Add('Docker no está instalado o no está disponible en PATH')
    } else {
        & docker compose --env-file $envPath -f (Join-Path $workspace 'docker-compose.prod.yml') config --quiet
        if ($LASTEXITCODE -ne 0) { $errors.Add('docker compose config no superó la validación') }
    }
}

if ($errors.Count -gt 0) {
    Write-Host 'SIGAA todavía no está listo para producción:' -ForegroundColor Red
    $errors | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
    exit 1
}

Write-Host 'Validación de configuración superada. Complete las comprobaciones operativas antes de producción.' -ForegroundColor Green
