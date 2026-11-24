@echo off
REM StockBuddy launcher for Windows. Double-click it to open the app.
REM Options (from Command Prompt):  run.bat --demo   run.bat --widget   run.bat --console
cd /d "%~dp0"
where javac >nul 2>nul || (echo Java is not installed. Download the free "JDK 17 or newer" from https://adoptium.net and try again. & pause & exit /b 1)
if exist out rmdir /s /q out
mkdir out
javac -d out src\stockbuddy\*.java || (pause & exit /b 1)
jar --create --file StockBuddy.jar --main-class stockbuddy.Main -C out . || (pause & exit /b 1)
echo %* | find "--console" >nul && (java -jar StockBuddy.jar %* & pause & exit /b)
start "" javaw -jar StockBuddy.jar %*
