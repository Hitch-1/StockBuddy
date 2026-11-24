@echo off
title Installing StockBuddy
echo.
echo   Installing StockBuddy... (a blue PowerShell window may appear - that is normal)
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1"
echo.
pause
