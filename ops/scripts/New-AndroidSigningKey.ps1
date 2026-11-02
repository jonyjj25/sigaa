[CmdletBinding()]
param([Parameter(Mandatory)][string]$OutputPath, [string]$Alias = 'sigaa-institucional')
$ErrorActionPreference = 'Stop'
$destination = [IO.Path]::GetFullPath($OutputPath)
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if ($destination.StartsWith($workspace + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Guarde el keystore fuera del repositorio, en custodia institucional.'
}
if (Test-Path -LiteralPath $destination) { throw 'No se sobrescribe un keystore existente.' }
if (-not $env:SIGAA_ANDROID_STORE_PASSWORD -or $env:SIGAA_ANDROID_STORE_PASSWORD.Length -lt 16) {
    throw 'Configure SIGAA_ANDROID_STORE_PASSWORD con una clave de al menos 16 caracteres desde el gestor institucional.'
}
# keytool obtiene la clave del entorno, no de argumentos visibles en procesos.
& keytool -genkeypair -keystore $destination -storetype PKCS12 -alias $Alias -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=SIGAA Institutional Android Signing' -storepass:env SIGAA_ANDROID_STORE_PASSWORD -keypass:env SIGAA_ANDROID_STORE_PASSWORD
if ($LASTEXITCODE -ne 0) { throw 'No se pudo generar la clave de firma.' }
Write-Output 'Clave generada. Custodie keystore, alias y contraseña fuera del servidor y del repositorio; conserve una segunda copia offline.'
