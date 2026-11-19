# Inject Canal into a stock Fudaa-LSPIV unzip. Does not modify -Source.
param(
  [Parameter(Mandatory = $true)][string]$Source,
  [string]$Destination,
  [switch]$Force
)
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$payload = Join-Path $here "payload"
$sourceRoot = (Resolve-Path $Source).Path
if (-not $Destination) {
  $Destination = Join-Path (Split-Path $sourceRoot -Parent) "Fudaa-LSPIV-Canal"
}
$destRoot = [System.IO.Path]::GetFullPath($Destination)

function Require($path, $what) {
  if (-not (Test-Path -LiteralPath $path)) { throw "Missing $what : $path" }
}
Require (Join-Path $sourceRoot "appli\app\fudaa-lspiv.jar") "stock jar"
Require (Join-Path $sourceRoot "appli\appli.exe") "stock launcher"
Require (Join-Path $sourceRoot "exes\PIV.exe") "PIV.exe"
Require (Join-Path $payload "canal-plugin.jar") "payload canal-plugin.jar"
Require (Join-Path $payload "asm-9.7.1.jar") "payload asm"
Require (Join-Path $payload "patcher") "payload patcher"

if ($sourceRoot.TrimEnd('\') -eq $destRoot.TrimEnd('\')) {
  throw "Destination must differ from source. The stock unzip is left unchanged."
}
if (Test-Path -LiteralPath $destRoot) {
  if (-not $Force) { throw "Destination exists: $destRoot  (pass -Force to replace it)" }
  Remove-Item -LiteralPath $destRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $destRoot | Out-Null

Write-Host "copy $sourceRoot -> $destRoot"
& robocopy $sourceRoot $destRoot /E /NFL /NDL /NJH /NJS /nc /ns /np | Out-Null
if ($LASTEXITCODE -ge 8) { throw "robocopy failed: $LASTEXITCODE" }

$java = Join-Path $destRoot "appli\runtime\bin\java.exe"
Require $java "bundled java"
$app = Join-Path $destRoot "appli\app"
$canal = Join-Path $app "canal"
New-Item -ItemType Directory -Force -Path $canal, (Join-Path $canal "ext") | Out-Null
Copy-Item -Force (Join-Path $payload "canal-plugin.jar") (Join-Path $canal "canal-plugin.jar")
Copy-Item -Force (Join-Path $payload "ext-README.txt") (Join-Path $canal "ext\README.txt")

$fixJar = Join-Path $canal "piv-core-fix.jar"
$cp = (Join-Path $payload "patcher") + ";" + (Join-Path $payload "asm-9.7.1.jar")
& $java -cp $cp canal.build.PatchPivLauncher (Join-Path $app "fudaa-lspiv.jar") $fixJar
if ($LASTEXITCODE -ne 0) { throw "patch failed" }

$cfg = Join-Path $app "appli.cfg"
if (-not (Test-Path (Join-Path $app "appli.cfg.upstream"))) {
  Copy-Item $cfg (Join-Path $app "appli.cfg.upstream")
}
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

$ico = Join-Path $destRoot "icons\canal.ico"
New-Item -ItemType Directory -Force -Path (Split-Path $ico) | Out-Null
Copy-Item -Force (Join-Path $payload "canal.ico") $ico
Copy-Item -Force (Join-Path $payload "canal-icon.png") (Join-Path $destRoot "icons\canal-icon.png")
Copy-Item -Force (Join-Path $payload "Canal.png") (Join-Path $destRoot "icons\Canal.png")
Copy-Item -Force (Join-Path $payload "canal.ico") (Join-Path $destRoot "appli\appli.ico")

$bat = Join-Path $destRoot "fudaa-lspiv-canal.bat"
@"
@echo off
set APP_PATH=%~dp0
echo Fudaa-LSPIV 1.11.3 Canal 1.1
"%APP_PATH%appli\appli.exe" --exepath "%APP_PATH%exes" --docpath "%APP_PATH%doc" --templatepath "%APP_PATH%templates"
"@ | Set-Content -Encoding ascii -Path $bat

$shell = New-Object -ComObject WScript.Shell
$lnk = $shell.CreateShortcut((Join-Path $destRoot "Fudaa-LSPIV-Canal.lnk"))
$lnk.TargetPath = $bat
$lnk.WorkingDirectory = [string]$destRoot
$lnk.IconLocation = $ico
$lnk.Description = "Fudaa-LSPIV 1.11.3 Canal 1.1"
$lnk.Save()

Set-Content -Encoding ascii -Path (Join-Path $destRoot "CANAL-VERSION.txt") -Value "Fudaa-LSPIV 1.11.3 Canal 1.1"
Write-Host "ready $destRoot"
Write-Host "start $bat"
