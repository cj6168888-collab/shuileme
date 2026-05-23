param(
  [Parameter(Mandatory = $true)]
  [string]$BackupPath,
  [string]$TargetPath = ""
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path

function Import-EnvFile($path) {
  if (-not (Test-Path $path)) { return }
  Get-Content -Encoding utf8 $path | ForEach-Object {
    $line = $_.Trim()
    if ($line.Length -eq 0 -or $line.StartsWith("#")) { return }
    $parts = $line.Split("=", 2)
    if ($parts.Count -eq 2 -and $parts[0].Trim().Length -gt 0) {
      $name = $parts[0].Trim()
      $value = $parts[1].Trim().Trim('"').Trim("'")
      if (-not [Environment]::GetEnvironmentVariable($name, "Process")) {
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
      }
    }
  }
}

Import-EnvFile (Join-Path $root ".env")
Import-EnvFile (Join-Path (Split-Path -Parent $root) ".env")

if (-not (Test-Path -LiteralPath $BackupPath)) {
  throw "Backup file not found: $BackupPath"
}

$backup = (Resolve-Path -LiteralPath $BackupPath).Path
if (-not $TargetPath) {
  $TargetPath = if ($env:GOUXIONG_DB_PATH) { $env:GOUXIONG_DB_PATH } else { Join-Path $root "data\gouxiong.sqlite3" }
}

$targetFull = [System.IO.Path]::GetFullPath($TargetPath)
$targetDir = Split-Path -Parent $targetFull
if (-not (Test-Path -LiteralPath $targetDir)) {
  New-Item -ItemType Directory -Path $targetDir | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMddTHHmmss"
if (Test-Path -LiteralPath $targetFull) {
  $safetyCopy = "$targetFull.before-restore-$timestamp"
  Copy-Item -LiteralPath $targetFull -Destination $safetyCopy -Force
  Write-Host "Safety copy created: $safetyCopy"
}

Copy-Item -LiteralPath $backup -Destination $targetFull -Force
Write-Host "Database restored from $backup to $targetFull"
Write-Host "Restart the GouXiong Sleep server after restore."
