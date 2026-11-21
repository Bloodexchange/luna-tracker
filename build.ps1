param([string]$ToolsRoot = "$env:LOCALAPPDATA\LunaBuildTools")
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$jdk = Get-ChildItem -LiteralPath $ToolsRoot -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') } | Select-Object -First 1
if (-not $jdk) { throw 'JDK 17 is missing from ToolsRoot' }
$java = Join-Path $jdk.FullName 'bin\java.exe'
$javac = Join-Path $jdk.FullName 'bin\javac.exe'
$jar = Join-Path $jdk.FullName 'bin\jar.exe'
$keytool = Join-Path $jdk.FullName 'bin\keytool.exe'
$aapt = Get-ChildItem -LiteralPath $ToolsRoot -Filter aapt2.exe -Recurse | Select-Object -First 1
$android = Get-ChildItem -LiteralPath $ToolsRoot -Filter android.jar -Recurse | Select-Object -First 1
if (-not $aapt -or -not $android) { throw 'Android platform and build tools are missing' }
$bt = $aapt.Directory.FullName
$build = Join-Path $projectRoot 'build'
$compiled = Join-Path $build ('compile-' + [guid]::NewGuid().ToString('N'))
$out = Join-Path $projectRoot 'artifacts'
$sign = Join-Path $ToolsRoot 'signing'
foreach ($path in @($build,$out,$sign,$compiled,"$build\generated","$build\dex","$build\tests")) { New-Item -ItemType Directory -Force -Path $path | Out-Null }
Copy-Item -LiteralPath $android.FullName -Destination "$build\android.jar" -Force
function Check([string]$step) { if ($LASTEXITCODE -ne 0) { throw "$step failed ($LASTEXITCODE)" } }
& $aapt.FullName compile --dir res -o build/resources.zip
Check 'Resource compilation'
& $aapt.FullName link -o build/unsigned.apk --manifest AndroidManifest.xml -I build/android.jar --java build/generated build/resources.zip
Check 'Resource linking'
$sources = @(Get-ChildItem "$projectRoot\src","$build\generated" -Filter *.java -Recurse | ForEach-Object { $_.FullName })
& $javac -encoding UTF-8 -source 8 -target 8 -classpath $android.FullName -d $compiled @sources
Check 'Java compilation'
& $javac -encoding UTF-8 -d "$build\tests" "$projectRoot\src\app\luna\cycle\Core.java" "$projectRoot\src\app\luna\cycle\ReturnWindow.java" "$projectRoot\tests\CoreTest.java" "$projectRoot\tests\ReturnWindowTest.java"
Check 'Test compilation'
& $java -cp "$build\tests" app.luna.cycle.CoreTest
Check 'Core tests'
& $java -cp "$build\tests" app.luna.cycle.ReturnWindowTest
Check 'Return window tests'
& $jar cf "$build\classes.jar" -C $compiled .
Check 'Class archive'
& $java -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --release --min-api 29 --lib $android.FullName --output "$build\dex" "$build\classes.jar"
Check 'Dex compilation'
& $jar uf "$build\unsigned.apk" -C "$build\dex" classes.dex
Check 'Dex packaging'
& "$bt\zipalign.exe" -f -p 4 build/unsigned.apk build/aligned.apk
Check 'Zip alignment'
$passwordFile = Join-Path $sign 'luna.pass'
$keystore = Join-Path $sign 'luna.p12'
if (-not (Test-Path -LiteralPath $keystore)) {
    $env:LUNA_SIGN_PASSWORD = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    [IO.File]::WriteAllText($passwordFile,$env:LUNA_SIGN_PASSWORD)
    & $keytool -genkeypair -keystore $keystore -storetype PKCS12 -storepass:env LUNA_SIGN_PASSWORD -alias luna -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Luna Local, OU=Personal, O=Luna, C=UZ'
    Check 'Signing key generation'
} else { $env:LUNA_SIGN_PASSWORD = [IO.File]::ReadAllText($passwordFile) }
try {
    & $java -jar "$bt\lib\apksigner.jar" sign --ks $keystore --ks-key-alias luna --ks-pass env:LUNA_SIGN_PASSWORD --out "$out\Luna-0.3.1.apk" "$build\aligned.apk"
    Check 'APK signing'
} finally { Remove-Item Env:LUNA_SIGN_PASSWORD -ErrorAction SilentlyContinue }
& $java -jar "$bt\lib\apksigner.jar" verify --verbose "$out\Luna-0.3.1.apk"
Check 'APK signature verification'
& "$bt\zipalign.exe" -c -p 4 artifacts/Luna-0.3.1.apk
Check 'Alignment verification'
$hash = (Get-FileHash "$out\Luna-0.3.1.apk" -Algorithm SHA256).Hash.ToLower()
[IO.File]::WriteAllText("$out\SHA256.txt", "$hash  Luna-0.3.1.apk`n")
Write-Output "APK ready: $out\Luna-0.3.1.apk"
Write-Output "SHA-256: $hash"
