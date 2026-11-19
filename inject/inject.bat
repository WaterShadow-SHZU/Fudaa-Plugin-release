@echo off
setlocal
set SCRIPT=%~dp0inject.ps1
if "%~1"=="" (
  echo Usage: inject.bat ^<stock-Fudaa-LSPIV-dir^> [destination]
  echo Example: inject.bat D:\Fudaa-LSPIV D:\Fudaa-LSPIV-Canal
  echo The stock directory is not modified. Destination defaults to a sibling named Fudaa-LSPIV-Canal.
  exit /b 2
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" -Source "%~1" -Destination "%~2" %3 %4
exit /b %ERRORLEVEL%
