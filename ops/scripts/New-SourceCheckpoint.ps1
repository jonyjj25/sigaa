[CmdletBinding()]
param(
    [string]$OutputDirectory = ".phase0/checkpoints"
)

$ErrorActionPreference = "Stop"

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$outputRoot = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $OutputDirectory))

if (-not $outputRoot.StartsWith($repositoryRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "El directorio de salida debe permanecer dentro del repositorio."
}

$gitSafePath = $repositoryRoot.Replace("\", "/")
$branch = (& git -c "safe.directory=$gitSafePath" -C $repositoryRoot branch --show-current).Trim()
$commit = (& git -c "safe.directory=$gitSafePath" -C $repositoryRoot rev-parse HEAD).Trim()

if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($commit)) {
    throw "No se pudo determinar el commit de referencia."
}

$tracked = @(& git -c "safe.directory=$gitSafePath" -C $repositoryRoot ls-files)
$untracked = @(& git -c "safe.directory=$gitSafePath" -C $repositoryRoot ls-files --others --exclude-standard)

$excludedPath = [regex]'(?i)(^|/)(\.git|\.phase0|data|certs|node_modules|target|coverage|dist|www|\.angular|\.gradle|build|uploads|backups?)(/|$)'
$excludedName = [regex]'(?i)(^|/)(\.env($|\.)|.*\.(pem|key|p12|pfx|jks|keystore|dump)|id_rsa|credentials|secrets?)(/|$)'

$relativeFiles = @($tracked + $untracked) |
    ForEach-Object { $_.Replace("\", "/").Trim() } |
    Where-Object { $_ -and -not $excludedPath.IsMatch($_) -and -not $excludedName.IsMatch($_) } |
    Sort-Object -Unique

if ($relativeFiles.Count -eq 0) {
    throw "No se encontraron archivos seguros para el checkpoint."
}

New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$archivePath = Join-Path $outputRoot "sigaa-source-$timestamp.zip"
$manifestPath = "$archivePath.manifest.sha256"
$checksumPath = "$archivePath.sha256"
$metadataPath = "$archivePath.metadata.txt"

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$archiveStream = [System.IO.File]::Open(
    $archivePath,
    [System.IO.FileMode]::CreateNew,
    [System.IO.FileAccess]::ReadWrite,
    [System.IO.FileShare]::None
)

try {
    $archive = [System.IO.Compression.ZipArchive]::new(
        $archiveStream,
        [System.IO.Compression.ZipArchiveMode]::Create,
        $false
    )
    try {
        foreach ($relativePath in $relativeFiles) {
            $absolutePath = Join-Path $repositoryRoot $relativePath
            if (-not (Test-Path -LiteralPath $absolutePath -PathType Leaf)) {
                continue
            }

            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $archive,
                $absolutePath,
                $relativePath,
                [System.IO.Compression.CompressionLevel]::Optimal
            ) | Out-Null
        }
    }
    finally {
        $archive.Dispose()
    }
}
finally {
    $archiveStream.Dispose()
}

$manifestLines = foreach ($relativePath in $relativeFiles) {
    $absolutePath = Join-Path $repositoryRoot $relativePath
    if (Test-Path -LiteralPath $absolutePath -PathType Leaf) {
        $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $absolutePath).Hash.ToLowerInvariant()
        "$hash  $relativePath"
    }
}

[System.IO.File]::WriteAllLines($manifestPath, $manifestLines, [System.Text.UTF8Encoding]::new($false))
$archiveHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $archivePath).Hash.ToLowerInvariant()
[System.IO.File]::WriteAllText($checksumPath, "$archiveHash  $([IO.Path]::GetFileName($archivePath))`n", [System.Text.UTF8Encoding]::new($false))

$metadata = @(
    "created_at=$((Get-Date).ToString('o'))"
    "branch=$branch"
    "base_commit=$commit"
    "included_files=$($manifestLines.Count)"
    "database_included=false"
    "uploads_included=false"
    "environment_files_included=false"
    "certificates_included=false"
)
[System.IO.File]::WriteAllLines($metadataPath, $metadata, [System.Text.UTF8Encoding]::new($false))

Write-Output "Checkpoint: $archivePath"
Write-Output "SHA256: $archiveHash"
Write-Output "Archivos incluidos: $($manifestLines.Count)"
Write-Output "Datos, usuarios, .env, certificados y cargas: EXCLUIDOS"
