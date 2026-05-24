$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) {
  $sdkCandidates = @(
    (Join-Path $env:LOCALAPPDATA "Android\Sdk"),
    (Join-Path $env:USERPROFILE "AppData\Local\Android\Sdk"),
    "C:\Android\Sdk",
    "D:\Android\Sdk",
    "D:\AndroidSDK"
  )
  $sdk = $sdkCandidates |
    Where-Object { $_ -and (Test-Path (Join-Path $_ "platforms")) -and (Test-Path (Join-Path $_ "build-tools")) } |
    Select-Object -First 1
}
if (-not $sdk) { throw "ANDROID_HOME or ANDROID_SDK_ROOT is not set." }

$platformsRoot = Join-Path $sdk "platforms"
$platform = Get-ChildItem -Path $platformsRoot -Directory -ErrorAction SilentlyContinue |
  Where-Object { Test-Path (Join-Path $_.FullName "android.jar") } |
  Sort-Object Name -Descending |
  Select-Object -First 1 |
  ForEach-Object { Join-Path $_.FullName "android.jar" }
if (-not $platform -or -not (Test-Path $platform)) { throw "No android.jar found under $platformsRoot." }

$buildToolsRoot = Join-Path $sdk "build-tools"
$bt = Get-ChildItem -Path $buildToolsRoot -Directory -ErrorAction SilentlyContinue |
  Where-Object { (Test-Path (Join-Path $_.FullName "aapt2.exe")) -or (Test-Path (Join-Path $_.FullName "aapt2")) } |
  Sort-Object Name -Descending |
  Select-Object -First 1 |
  ForEach-Object { $_.FullName }
if (-not $bt) { throw "No Android build-tools found under $buildToolsRoot." }
$aapt2 = Join-Path $bt "aapt2.exe"
$d8 = Join-Path $bt "d8.bat"
$zipalign = Join-Path $bt "zipalign.exe"
$apksigner = Join-Path $bt "apksigner.bat"
if (-not (Test-Path $aapt2)) { $aapt2 = Join-Path $bt "aapt2" }
if (-not (Test-Path $d8)) { $d8 = Join-Path $bt "d8" }
if (-not (Test-Path $zipalign)) { $zipalign = Join-Path $bt "zipalign" }
if (-not (Test-Path $apksigner)) { $apksigner = Join-Path $bt "apksigner" }
foreach ($tool in @($aapt2, $d8, $zipalign, $apksigner)) {
  if (-not (Test-Path $tool)) { throw "Missing Android build tool: $tool" }
}

$workRoot = Join-Path $env:TEMP "gouxiong-sleep-android-build"
if (Test-Path $workRoot) {
  $resolvedWork = (Resolve-Path $workRoot).Path
  if (-not $resolvedWork.StartsWith((Resolve-Path $env:TEMP).Path)) { throw "Refusing to delete temp dir outside temp: $resolvedWork" }
  Remove-Item -LiteralPath $resolvedWork -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $workRoot | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot "src") -Destination (Join-Path $workRoot "src") -Recurse

$build = Join-Path $workRoot "build"
$projectBuild = Join-Path $projectRoot "build"
$resolvedProject = (Resolve-Path $projectRoot).Path
if (Test-Path $projectBuild) {
  $resolvedProjectBuild = (Resolve-Path $projectBuild).Path
  if (-not $resolvedProjectBuild.StartsWith($resolvedProject)) { throw "Refusing to delete project build dir outside project: $resolvedProjectBuild" }
  Remove-Item -LiteralPath $resolvedProjectBuild -Recurse -Force
}

$gen = Join-Path $build "generated"
$classes = Join-Path $build "classes"
$dex = Join-Path $build "dex"
$out = Join-Path $build "outputs\apk"
New-Item -ItemType Directory -Force -Path $gen,$classes,$dex,$out | Out-Null

$manifest = Join-Path $workRoot "src\main\AndroidManifest.xml"
$res = Join-Path $workRoot "src\main\res"
$compiled = Join-Path $build "compiled-res.zip"
$unsigned = Join-Path $build "unsigned.apk"
$withDex = Join-Path $build "with-dex.apk"
$aligned = Join-Path $build "aligned.apk"
$signed = Join-Path $out "gouxiong-sleep-debug.apk"
$finalOut = Join-Path $projectBuild "outputs\apk"
$finalApk = Join-Path $finalOut "gouxiong-sleep-debug.apk"

& $aapt2 compile --dir $res -o $compiled
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }
& $aapt2 link -o $unsigned -I $platform --manifest $manifest --java $gen --min-sdk-version 26 --target-sdk-version 35 --auto-add-overlay $compiled
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

$sources = Join-Path $build "sources.txt"
$sourceFiles = Get-ChildItem -Path (Join-Path $workRoot "src\main\java"),$gen -Recurse -Filter *.java | ForEach-Object { $_.FullName }
[System.IO.File]::WriteAllLines($sources, $sourceFiles, [System.Text.Encoding]::ASCII)
& javac --release 8 -encoding UTF-8 -classpath $platform -d $classes "@$sources"
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

$classesJar = Join-Path $build "classes.jar"
& jar cf $classesJar -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }
& $d8 --lib $platform --min-api 26 --output $dex $classesJar
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Copy-Item -LiteralPath $unsigned -Destination $withDex
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

function CompressionForZipEntry($path) {
  $extension = [System.IO.Path]::GetExtension($path).ToLowerInvariant()
  if ($extension -in @(".png", ".moc3", ".json", ".motion3", ".cdi3", ".physics3", ".pose3", ".userdata3")) {
    return [System.IO.Compression.CompressionLevel]::NoCompression
  }
  return [System.IO.Compression.CompressionLevel]::Optimal
}

$zip = [System.IO.Compression.ZipFile]::Open($withDex, [System.IO.Compression.ZipArchiveMode]::Update)
try {
  $existing = $zip.GetEntry("classes.dex")
  if ($existing) { $existing.Delete() }
  [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $dex "classes.dex"), "classes.dex") | Out-Null

  $assetsRoot = Join-Path $workRoot "src\main\assets"
  if (Test-Path $assetsRoot) {
    $resolvedAssets = (Resolve-Path $assetsRoot).Path
    foreach ($asset in Get-ChildItem -Path $assetsRoot -Recurse -File) {
      $relative = $asset.FullName.Substring($resolvedAssets.Length).TrimStart('\', '/')
      $entryName = ("assets/" + $relative).Replace('\', '/')
      $existingAsset = $zip.GetEntry($entryName)
      if ($existingAsset) { $existingAsset.Delete() }
      $compression = CompressionForZipEntry $asset.FullName
      [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $asset.FullName, $entryName, $compression) | Out-Null
    }
  }
} finally {
  $zip.Dispose()
}

& $zipalign -f 4 $withDex $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

$keystoreDir = Join-Path $projectRoot "keystore"
New-Item -ItemType Directory -Force -Path $keystoreDir | Out-Null
$keystore = Join-Path $keystoreDir "debug.keystore"
if (-not (Test-Path $keystore)) {
  & keytool -genkeypair -v -keystore $keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
  if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}

& $apksigner sign --ks $keystore --ks-pass pass:android --key-pass pass:android --out $signed $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner sign failed" }
& $apksigner verify --verbose $signed
if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed" }

New-Item -ItemType Directory -Force -Path $finalOut | Out-Null
Copy-Item -LiteralPath $signed -Destination $finalApk -Force
Write-Host "APK built: $finalApk"
