@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist "data\tmp" mkdir "data\tmp"
set "PROJECT_JAVA=java"
for /d %%J in ("%~dp0..\.tools\jdk17\*") do if exist "%%J\bin\java.exe" set "PROJECT_JAVA=%%J\bin\java.exe"
if not exist ".env" (
  "%PROJECT_JAVA%" -Djava.io.tmpdir="%~dp0data\tmp" -jar target\welcome.jar --setup --local
  if errorlevel 1 goto end
)
echo Open http://127.0.0.1:8080/
"%PROJECT_JAVA%" -Djava.io.tmpdir="%~dp0data\tmp" -jar target\welcome.jar
:end
pause
