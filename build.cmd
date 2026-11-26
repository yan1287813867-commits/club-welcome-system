@echo off
chcp 65001 >nul
cd /d "%~dp0"
for /d %%J in ("%~dp0..\.tools\jdk17\*") do if exist "%%J\bin\javac.exe" set "JAVA_HOME=%%J"
if not exist "%~dp0..\.tools\tmp" mkdir "%~dp0..\.tools\tmp"
set "MAVEN_OPTS=-Djava.io.tmpdir=%~dp0..\.tools\tmp"
set "PROJECT_MAVEN=mvn"
for /d %%M in ("%~dp0..\.tools\maven\*") do if exist "%%M\bin\mvn.cmd" set "PROJECT_MAVEN=%%M\bin\mvn.cmd"
call "%PROJECT_MAVEN%" -Dmaven.repo.local="%~dp0..\.tools\m2" clean verify
if errorlevel 1 goto end
echo Build complete: target\welcome.jar
:end
pause
