[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$OutputPath,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)

if ((Test-Path -LiteralPath $resolvedOutput) -and -not $Force) {
    throw "El archivo ya existe. Use otra ruta o -Force: $resolvedOutput"
}

$parent = Split-Path -Parent $resolvedOutput
if (-not $parent) {
    throw 'Indique una ruta con directorio, preferiblemente en una custodia externa.'
}

New-Item -ItemType Directory -Path $parent -Force | Out-Null
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $rng.GetBytes($bytes)
} finally {
    $rng.Dispose()
}

$base64 = [Convert]::ToBase64String($bytes)
[System.IO.File]::WriteAllText(
    $resolvedOutput,
    $base64 + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)

if ($IsWindows -or $env:OS -eq 'Windows_NT') {
    & icacls.exe $resolvedOutput /inheritance:r /grant:r "$($env:USERNAME):(R,W)" | Out-Null
} else {
    & chmod 600 $resolvedOutput
}

$sha = [System.Security.Cryptography.SHA256]::Create()
try {
    $fingerprint = ([Convert]::ToHexString($sha.ComputeHash($bytes))).Substring(0, 16)
} finally {
    $sha.Dispose()
    [Array]::Clear($bytes, 0, $bytes.Length)
}

Write-Host "Clave AES-256 generada en: $resolvedOutput"
Write-Host "Huella para inventario (no secreta): $fingerprint"
Write-Host 'Custodie el archivo fuera del servidor y copie su contenido al gestor institucional de secretos.'
