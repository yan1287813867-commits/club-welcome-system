@echo off
chcp 65001 >nul
cd /d "%~dp0"
set "PROJECT_JAVA=java"
for /d %%J in ("%~dp0..\.tools\jdk17\*") do if exist "%%J\bin\java.exe" set "PROJECT_JAVA=%%J\bin\java.exe"
if not exist ".env" (
  echo Please initialize the website with start-local.cmd first.
  pause
  exit /b 1
)
if not exist "data\tmp" mkdir "data\tmp"
"%PROJECT_JAVA%" -Djava.io.tmpdir="%~dp0data\tmp" -Dorg.sqlite.tmpdir="%~dp0data\tmp" -jar target\welcome.jar --reset-password
if errorlevel 1 goto end
echo Password updated. Stop and restart the website to apply it.
:end
pause
