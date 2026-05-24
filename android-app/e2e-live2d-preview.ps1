param(
  [int]$TimeoutSec = 130,
  [switch]$SkipInstall,
  [switch]$AutoLoad
)

$ErrorActionPreference = "Stop"
if (Get-Variable PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
  $PSNativeCommandUseErrorActionPreference = $false
}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $root
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { throw "ANDROID_HOME or ANDROID_SDK_ROOT is not set." }

$adb = Join-Path $sdk "platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb not found: $adb" }

$apk = Join-Path $root "build\outputs\apk\gouxiong-sleep-debug.apk"
if (-not (Test-Path $apk)) { throw "APK missing. Run android-app\test.ps1 first: $apk" }

function FirstDevice() {
  $lines = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" }
  if (-not $lines -or $lines.Count -lt 1) { throw "No online Android device/emulator found." }
  return (($lines | Select-Object -First 1) -split "\s+")[0]
}

function PullScreenshot($device, $remote, $local) {
  & $adb -s $device shell screencap -p $remote | Out-Null
  & $adb -s $device pull $remote $local | Out-Null
  if (-not (Test-Path $local)) { throw "Screenshot pull failed: $local" }
}

function Test-ModelPixels($path) {
  Add-Type -AssemblyName System.Drawing
  $bitmap = [System.Drawing.Bitmap]::FromFile($path)
  try {
    $x1 = [Math]::Max(0, [int]($bitmap.Width * 0.22))
    $x2 = [Math]::Min($bitmap.Width - 1, [int]($bitmap.Width * 0.78))
    $y1 = [Math]::Max(0, [int]($bitmap.Height * 0.22))
    $y2 = [Math]::Min($bitmap.Height - 1, [int]($bitmap.Height * 0.78))
    $dark = 0
    $colored = 0
    $sampled = 0
    for ($y = $y1; $y -le $y2; $y += 3) {
      for ($x = $x1; $x -le $x2; $x += 3) {
        $pixel = $bitmap.GetPixel($x, $y)
        $max = [Math]::Max($pixel.R, [Math]::Max($pixel.G, $pixel.B))
        $min = [Math]::Min($pixel.R, [Math]::Min($pixel.G, $pixel.B))
        $brightness = ($pixel.R + $pixel.G + $pixel.B) / 3
        if ($brightness -lt 218) { $dark++ }
        if (($max - $min) -gt 18 -and $brightness -lt 244) { $colored++ }
        $sampled++
      }
    }
    return [pscustomobject]@{
      DarkPixels = $dark
      ColoredPixels = $colored
      SampledPixels = $sampled
      LooksRendered = ($dark -gt 1600 -and $colored -gt 1200)
    }
  } finally {
    $bitmap.Dispose()
  }
}

function ReadLive2DLog($device) {
  return (& $adb -s $device logcat -d -t 3000 | Select-String -Pattern "Live2DPreview|Cubism|ANR|FATAL|crash|timeout|failed|AndroidRuntime") -join "`n"
}

function DumpFinalWindowXml($device, $local) {
  $remote = "/sdcard/live2d_preview_final_window.xml"
  $oldPreference = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    & $adb -s $device shell uiautomator dump $remote | Out-Null
    & $adb -s $device pull $remote $local | Out-Null
    if (Test-Path $local) {
      return [System.IO.File]::ReadAllText((Resolve-Path $local), [System.Text.Encoding]::UTF8)
    }
  } finally {
    $ErrorActionPreference = $oldPreference
  }
  return ""
}

function LastBridgeStatus($log) {
  if (-not $log) { return "" }
  $matches = [regex]::Matches($log, "bridge status after \d+s: ([^\r\n]+)")
  if ($matches.Count -gt 0) {
    return $matches[$matches.Count - 1].Groups[1].Value
  }
  if ($log -match "bridge ready after \d+s") { return $Matches[0] }
  if ($log -match "bridge error after \d+s: ([^\r\n]+)") { return $Matches[0] }
  return ""
}

$device = FirstDevice
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outDir = Join-Path $projectRoot "artifacts\debug-ui\live2d-e2e-$stamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if (-not $SkipInstall) {
  & $adb -s $device install -r $apk | Write-Output
  if ($LASTEXITCODE -ne 0) { throw "adb install failed" }
}

& $adb -s $device shell am force-stop com.gouxiong.sleep | Out-Null
& $adb -s $device logcat -c | Out-Null
if ($AutoLoad) {
  & $adb -s $device shell am start -n com.gouxiong.sleep/.MainActivity -a com.gouxiong.sleep.action.DEBUG_LIVE2D_PREVIEW --ez auto_load true | Write-Output
} else {
  & $adb -s $device shell am start -n com.gouxiong.sleep/.MainActivity -a com.gouxiong.sleep.action.DEBUG_LIVE2D_PREVIEW | Write-Output
  Start-Sleep -Seconds 10
  & $adb -s $device shell input tap 205 2078 | Out-Null
}

$deadline = (Get-Date).AddSeconds($TimeoutSec)
$loaded = $false
$failed = $false
$lastStatus = ""
$lastLog = ""

while ((Get-Date) -lt $deadline) {
  Start-Sleep -Seconds 10
  $log = ReadLive2DLog $device
  if ($log) { $lastLog = $log }
  $status = LastBridgeStatus $log
  if ($status) {
    $lastStatus = $status
    Write-Output "Live2D status: $status"
  }
  if ($log -match "bridge ready after \d+s") {
    $loaded = $true
    break
  }
  if ($log -match "bridge error after|Validation timeout|ANR in com\.gouxiong\.sleep:live2d|FATAL EXCEPTION") {
    $failed = $true
    break
  }
}

Start-Sleep -Seconds 3
$screenshot = Join-Path $outDir "final.png"
PullScreenshot $device "/sdcard/live2d_preview_final.png" $screenshot
$windowXmlPath = Join-Path $outDir "final-window.xml"
$windowXml = DumpFinalWindowXml $device $windowXmlPath

$logPath = Join-Path $outDir "logcat.txt"
$lastLog = ReadLive2DLog $device
$lastLog | Set-Content -Encoding UTF8 $logPath

$pixels = Test-ModelPixels $screenshot
$hasAnr = $lastLog -match "ANR in com\.gouxiong\.sleep:live2d|FATAL EXCEPTION|AndroidRuntime"
$hasAnrDialog = $windowXml -match "isn.?t responding|aerr_close|aerr_wait|Close app"
$summary = [pscustomobject]@{
  Device = $device
  LoadedStatus = $loaded
  FailedStatus = $failed
  HasAnrOrFatal = $hasAnr
  HasAnrDialog = $hasAnrDialog
  LastStatus = $lastStatus
  PixelCheck = $pixels.LooksRendered
  DarkPixels = $pixels.DarkPixels
  ColoredPixels = $pixels.ColoredPixels
  SampledPixels = $pixels.SampledPixels
  Screenshot = $screenshot
  Logcat = $logPath
}
$summary | ConvertTo-Json -Depth 4 | Tee-Object -FilePath (Join-Path $outDir "summary.json")

if ($hasAnr) {
  throw "Live2D preview triggered ANR/FATAL. See logcat: $logPath"
}
if ($hasAnrDialog) {
  throw "Live2D preview displayed an Android not-responding dialog. See screenshot: $screenshot"
}
if (-not $loaded -and -not $pixels.LooksRendered) {
  throw "Live2D preview did not report loaded or render within ${TimeoutSec}s. Last status: $lastStatus"
}
if (-not $pixels.LooksRendered) {
  throw "Live2D preview loaded but screenshot did not pass pixel render check. Dark=$($pixels.DarkPixels), Colored=$($pixels.ColoredPixels)"
}
Write-Output "Live2D preview E2E passed: $screenshot"
