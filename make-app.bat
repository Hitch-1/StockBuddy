@echo off
REM Builds a stand-alone StockBuddy.exe in dist\StockBuddy (the people who use it do not need Java installed).
cd /d "%~dp0"
if exist out rmdir /s /q out
if exist dist rmdir /s /q dist
if exist build rmdir /s /q build
mkdir out & mkdir build
javac -d out src\stockbuddy\*.java || (pause & exit /b 1)
jar --create --file build\StockBuddy.jar --main-class stockbuddy.Main -C out . || (pause & exit /b 1)
jpackage --type app-image --name StockBuddy --input build --main-jar StockBuddy.jar --dest dist --description "Beginner-friendly daily stock helper" || (pause & exit /b 1)
echo.
echo Done! Open dist\StockBuddy and double-click StockBuddy.exe.
echo Tip: right-click StockBuddy.exe - Send to - Desktop (create shortcut) to put it on your desktop.
pause
