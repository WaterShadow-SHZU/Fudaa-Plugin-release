# Build the Canal plugin, patch PIV launch, install into appli\app\canal
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
function Resolve-FudaaRoot([string]$pluginDir) {
  if ($env:FUDAA_CANAL_ROOT -and (Test-Path (Join-Path $env:FUDAA_CANAL_ROOT "appli\app\fudaa-lspiv.jar"))) {
    return (Resolve-Path $env:FUDAA_CANAL_ROOT).Path
  }
  $parent = (Resolve-Path (Join-Path $pluginDir "..")).Path
  if (Test-Path (Join-Path $parent "appli\app\fudaa-lspiv.jar")) { return $parent }
  $sibling = Join-Path $parent "Fudaa-LSPIV-Canal"
  if (Test-Path (Join-Path $sibling "appli\app\fudaa-lspiv.jar")) { return (Resolve-Path $sibling).Path }
  throw "No Fudaa host found. Put this repo as <host>\canal-plugin, or set FUDAA_CANAL_ROOT to Fudaa-LSPIV-Canal."
}
$root = Resolve-FudaaRoot $here
Write-Host "Fudaa host: $root"
$javac = Join-Path $root "appli\runtime\bin\javac.exe"
$jar = Join-Path $root "appli\runtime\bin\jar.exe"
$java = Join-Path $root "appli\runtime\bin\java.exe"
$src = Join-Path $here "src"
$buildSrc = Join-Path $here "build-src"
$classes = Join-Path $here "build\classes"
$buildClasses = Join-Path $here "build\build-classes"
$pluginJar = Join-Path $here "build\canal-plugin.jar"
$fixJar = Join-Path $here "build\piv-core-fix.jar"
$asm = Join-Path $here "lib\asm-9.7.1.jar"
$hostJar = Join-Path $root "appli\app\fudaa-lspiv.jar"
$dest = Join-Path $root "appli\app\canal"

if (-not (Test-Path $javac)) { throw "javac missing: $javac" }
if (-not (Test-Path $asm)) { throw "ASM missing: $asm" }

$buildDir = Join-Path $here "build"
if (Test-Path $buildDir) { Remove-Item -Recurse -Force $buildDir }
New-Item -ItemType Directory -Force -Path $classes, $buildClasses, $dest, (Join-Path $dest "ext") | Out-Null

$patchSrc = @(Get-ChildItem -Recurse $buildSrc -Filter *.java | ForEach-Object { $_.FullName })
& $javac --release 11 -encoding UTF-8 -cp $asm -d $buildClasses @patchSrc
if ($LASTEXITCODE -ne 0) { throw "patcher compile failed" }
$icons = Join-Path $here "icons"
foreach ($name in @("Canal.png", "icone-lspiv.png", "canal-icon.png", "canal.ico")) {
  if (-not (Test-Path (Join-Path $icons $name))) { throw "missing icon $name" }
}

$sources = @(Get-ChildItem -Recurse $src -Filter *.java | ForEach-Object { $_.FullName })
& $javac --release 11 -encoding UTF-8 -cp $hostJar -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw "plugin compile failed" }
New-Item -ItemType Directory -Force -Path (Join-Path $classes "canal\lspiv"), (Join-Path $classes "org\fudaa\fudaa\piv") | Out-Null
Copy-Item -Force (Join-Path $icons "canal-icon.png") (Join-Path $classes "canal\lspiv\canal-icon.png")
Copy-Item -Force (Join-Path $icons "icone-lspiv.png") (Join-Path $classes "org\fudaa\fudaa\piv\icone-lspiv.png")
$i18n = Join-Path $here "build\i18n"
python (Join-Path $here "tools\build_zh_resources.py") $hostJar $i18n
if ($LASTEXITCODE -ne 0) { throw "zh resources failed" }
Copy-Item -Recurse -Force (Join-Path $i18n "*") $classes

Push-Location $classes
& $jar --create --file $pluginJar -C $classes .
Pop-Location
if ($LASTEXITCODE -ne 0) { throw "jar plugin failed" }

$cp = $buildClasses + ";" + $asm
& $java -cp $cp canal.build.PatchPivLauncher $hostJar $fixJar
if ($LASTEXITCODE -ne 0) { throw "patch PivExeLauncher failed" }

Copy-Item -Force $pluginJar (Join-Path $dest "canal-plugin.jar")
Copy-Item -Force $fixJar (Join-Path $dest "piv-core-fix.jar")
$extReadme = Join-Path $dest "ext\README.txt"
Set-Content -Encoding ascii -Path $extReadme -Value "Drop a jar that implements canal.lspiv.ext.CanalAssist here. Register it in META-INF/services/canal.lspiv.ext.CanalAssist. Ids: autogrid, auto-ia, sa-assist. Menu stays disabled until isAvailable() is true."

$cfg = Join-Path $root "appli\app\appli.cfg"
$cfgBak = Join-Path $root "appli\app\appli.cfg.upstream"
if (-not (Test-Path $cfgBak)) { Copy-Item $cfg $cfgBak }
$cfgText = @"
[Application]
app.classpath=`$APPDIR\canal\piv-core-fix.jar
app.classpath=`$APPDIR\canal\canal-plugin.jar
app.classpath=`$APPDIR\fudaa-lspiv.jar
app.mainclass=canal.lspiv.CanalBootstrap

[JavaOptions]
java-options=-Djpackage.app-version=1.11.3-Canal-1.1
"@
Set-Content -Encoding ascii -Path $cfg -Value $cfgText

Copy-Item -Force (Join-Path $icons "canal.ico") (Join-Path $root "icons\canal.ico")
Copy-Item -Force (Join-Path $icons "canal-icon.png") (Join-Path $root "icons\canal-icon.png")
Copy-Item -Force (Join-Path $icons "Canal.png") (Join-Path $root "icons\Canal.png")
$oldIcon = Join-Path $root "icons\New.png"
if (Test-Path $oldIcon) { Remove-Item -Force $oldIcon }
$bat = Join-Path $root "fudaa-lspiv-canal.bat"
if (-not (Test-Path $bat)) {
  Set-Content -Encoding ascii -Path $bat -Value "@echo off`r`nset APP_PATH=%~dp0`r`necho Fudaa-LSPIV 1.11.3 Canal 1.1`r`n`"%APP_PATH%appli\appli.exe`" --exepath `"%APP_PATH%exes`" --docpath `"%APP_PATH%doc`" --templatepath `"%APP_PATH%templates`"`r`n"
}
$shell = New-Object -ComObject WScript.Shell
$lnk = $shell.CreateShortcut((Join-Path $root "Fudaa-LSPIV-Canal.lnk"))
$lnk.TargetPath = $bat
$lnk.WorkingDirectory = $root
$lnk.IconLocation = Join-Path $root "icons\canal.ico"
$lnk.Description = "Fudaa-LSPIV 1.11.3 Canal 1.1"
$lnk.Save()

$kit = Join-Path $here "dist\Fudaa-LSPIV-Canal-inject"
$kitPayload = Join-Path $kit "payload"
if (Test-Path (Join-Path $here "dist")) { Remove-Item -Recurse -Force (Join-Path $here "dist") }
New-Item -ItemType Directory -Force -Path $kitPayload, (Join-Path $kitPayload "patcher") | Out-Null
Copy-Item -Force $pluginJar (Join-Path $kitPayload "canal-plugin.jar")
Copy-Item -Force $asm (Join-Path $kitPayload "asm-9.7.1.jar")
Copy-Item -Force (Join-Path $here "lib\ASM-LICENSE") (Join-Path $kitPayload "ASM-LICENSE")
Copy-Item -Recurse -Force (Join-Path $buildClasses "canal") (Join-Path $kitPayload "patcher\canal")
Copy-Item -Force (Join-Path $icons "canal.ico") (Join-Path $kitPayload "canal.ico")
Copy-Item -Force (Join-Path $icons "canal-icon.png") (Join-Path $kitPayload "canal-icon.png")
Copy-Item -Force (Join-Path $icons "icone-lspiv.png") (Join-Path $kitPayload "icone-lspiv.png")
Copy-Item -Force (Join-Path $icons "Canal.png") (Join-Path $kitPayload "Canal.png")
Set-Content -Encoding ascii -Path (Join-Path $kitPayload "ext-README.txt") -Value "Drop a jar that implements canal.lspiv.ext.CanalAssist here."
Copy-Item -Force (Join-Path $here "inject\inject.ps1") (Join-Path $kit "inject.ps1")
Copy-Item -Force (Join-Path $here "inject\inject.bat") (Join-Path $kit "inject.bat")
$readmeSrc = Join-Path $here "inject\README.txt"
$utf8bom = New-Object System.Text.UTF8Encoding $true
$readmeText = [System.IO.File]::ReadAllText($readmeSrc)
[System.IO.File]::WriteAllText((Join-Path $kit "README.txt"), $readmeText, $utf8bom)

$zip = Join-Path $here "dist\Fudaa-LSPIV-Canal-inject.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
Compress-Archive -Path (Join-Path $kit "*") -DestinationPath $zip
Write-Host "installed $dest"
Write-Host "inject kit $kit"
Write-Host "zip $zip"
