$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { throw "ANDROID_HOME or ANDROID_SDK_ROOT is not set." }

$local = Join-Path $root "local.deepseek.properties"
if (-not (Test-Path $local)) {
  throw "Missing local test config: $local"
}

$props = @{}
Get-Content -Encoding UTF8 $local | ForEach-Object {
  $line = $_.Trim()
  if ($line.Length -eq 0 -or $line.StartsWith("#")) { return }
  $i = $line.IndexOf("=")
  if ($i -gt 0) {
    $props[$line.Substring(0, $i).Trim()] = $line.Substring($i + 1).Trim()
  }
}

$key = $props["deepseek.apiKey"]
if (-not $key -or -not $key.StartsWith("sk-")) {
  throw "deepseek.apiKey is missing or invalid in local.deepseek.properties"
}
$model = $props["deepseek.model"]
if (-not $model) { $model = "deepseek-v4-flash" }

$adb = Join-Path $sdk "platform-tools\adb.exe"
& $adb devices -l
& $adb shell am start -n com.gouxiong.sleep/.MainActivity --es debug_deepseek_key "$key" --es debug_deepseek_model "$model" --ez open_deepseek_chat true
if ($LASTEXITCODE -ne 0) { throw "Failed to inject DeepSeek test key into the debug app" }

Write-Host "DeepSeek test key injected into the debug app. The key was read from local.deepseek.properties and is not packaged into the APK."
