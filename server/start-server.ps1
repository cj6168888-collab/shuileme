$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$envFile = Join-Path $root ".env"
function Import-EnvFile($path) {
  if (-not (Test-Path $path)) {
    return
  }
  Get-Content -Encoding utf8 $path | ForEach-Object {
    $line = $_.Trim()
    if ($line.Length -eq 0 -or $line.StartsWith("#")) {
      return
    }
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

function New-RandomSecret {
  $bytes = New-Object byte[] 24
  $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  try {
    $rng.GetBytes($bytes)
  } finally {
    $rng.Dispose()
  }
  return [Convert]::ToBase64String($bytes).TrimEnd("=").Replace("+", "-").Replace("/", "_")
}

Import-EnvFile $envFile
Import-EnvFile (Join-Path (Split-Path -Parent $root) ".env")
Import-EnvFile (Join-Path $env:USERPROFILE "Desktop\aliyun-sms.env")
Import-EnvFile (Join-Path $env:USERPROFILE "Desktop\guanlin-aliyun-ai.env")

if (-not $env:GOUXIONG_PORT) {
  $env:GOUXIONG_PORT = "8787"
}
if (-not $env:GOUXIONG_HOST) {
  $env:GOUXIONG_HOST = "0.0.0.0"
}
$dataDir = Join-Path $root "data"
if (-not (Test-Path $dataDir)) {
  New-Item -ItemType Directory -Path $dataDir | Out-Null
}
if (-not $env:GOUXIONG_SERVER_SECRET) {
  $serverSecretFile = Join-Path $dataDir ".server-secret"
  if (Test-Path $serverSecretFile) {
    $env:GOUXIONG_SERVER_SECRET = (Get-Content -Encoding utf8 $serverSecretFile -Raw).Trim()
  } else {
    $env:GOUXIONG_SERVER_SECRET = New-RandomSecret
    Set-Content -Encoding utf8 -Path $serverSecretFile -Value $env:GOUXIONG_SERVER_SECRET
  }
}
if (-not $env:GOUXIONG_ADMIN_TOKEN) {
  $adminTokenFile = Join-Path $dataDir "admin-token.txt"
  if (Test-Path $adminTokenFile) {
    $env:GOUXIONG_ADMIN_TOKEN = (Get-Content -Encoding utf8 $adminTokenFile -Raw).Trim()
  } else {
    $env:GOUXIONG_ADMIN_TOKEN = New-RandomSecret
    Set-Content -Encoding utf8 -Path $adminTokenFile -Value $env:GOUXIONG_ADMIN_TOKEN
  }
}
if (-not $env:GOUXIONG_ADMIN_BASIC_USER) {
  $env:GOUXIONG_ADMIN_BASIC_USER = "admin"
}
if (-not $env:GOUXIONG_ADMIN_BASIC_PASSWORD -and $env:GOUXIONG_HOST -ne "127.0.0.1" -and $env:GOUXIONG_HOST -ne "localhost") {
  $adminPassFile = Join-Path $dataDir "admin-page-password.txt"
  if (Test-Path $adminPassFile) {
    $env:GOUXIONG_ADMIN_BASIC_PASSWORD = (Get-Content -Encoding utf8 $adminPassFile -Raw).Trim()
  } else {
    $env:GOUXIONG_ADMIN_BASIC_PASSWORD = New-RandomSecret
    Set-Content -Encoding utf8 -Path $adminPassFile -Value $env:GOUXIONG_ADMIN_BASIC_PASSWORD
  }
}
if (-not $env:GOUXIONG_DEV_SMS) {
  if ($env:ALIYUN_SMS_ACCESS_KEY_ID -and $env:ALIYUN_SMS_ACCESS_KEY_SECRET -and $env:ALIYUN_SMS_SIGN_NAME -and $env:ALIYUN_SMS_TEMPLATE_CODE) {
    $env:GOUXIONG_DEV_SMS = "0"
  } else {
    $env:GOUXIONG_DEV_SMS = "1"
  }
}

Write-Host "Starting GouXiong Sleep server on http://$($env:GOUXIONG_HOST):$($env:GOUXIONG_PORT)"
Write-Host "Android emulator URL: http://10.0.2.2:$($env:GOUXIONG_PORT)"
if ($serverSecretFile) {
  Write-Host "Server secret file: $serverSecretFile"
}
if ($adminTokenFile) {
  Write-Host "Admin API token file: $adminTokenFile"
}
if ($env:GOUXIONG_ADMIN_BASIC_PASSWORD) {
  Write-Host "Admin page Basic Auth: enabled, user=$($env:GOUXIONG_ADMIN_BASIC_USER)"
  if ($adminPassFile) {
    Write-Host "Admin page password file: $adminPassFile"
  }
} else {
  Write-Host "Admin page Basic Auth: disabled; use only on localhost"
}
if ($env:DASHSCOPE_API_KEY -or $env:ALIYUN_MODEL_API_KEY -or $env:ALIYUN_BAILIAN_API_KEY) {
  Write-Host "Model proxy: Aliyun DashScope enabled"
} elseif ($env:DEEPSEEK_API_KEY) {
  Write-Host "Model proxy: DeepSeek fallback enabled"
} else {
  Write-Host "Model proxy: not configured, fallback replies will be used"
}
if ($env:ALIYUN_REALTIME_ENABLED -eq "1") {
  Write-Host "Realtime bridge: enabled, model=$($env:ALIYUN_REALTIME_MODEL)"
} else {
  Write-Host "Realtime bridge: disabled; set ALIYUN_REALTIME_ENABLED=1 to enable"
}
if ($env:GOUXIONG_DEV_SMS -eq "1") {
  Write-Host "SMS provider: dev mode"
} elseif ($env:ALIYUN_SMS_ACCESS_KEY_ID -and $env:ALIYUN_SMS_ACCESS_KEY_SECRET -and $env:ALIYUN_SMS_SIGN_NAME -and $env:ALIYUN_SMS_TEMPLATE_CODE) {
  Write-Host "SMS provider: Aliyun"
} else {
  Write-Host "SMS provider: not configured"
}
npm.cmd start
