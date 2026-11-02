@echo off
title MediCircle Console App
cd /d "%~dp0"
echo ==================================================
echo   Starting MediCircle Console App (Command Shell)
echo ==================================================
java -cp "out;lib/*" medicircle.ConsoleApp
pause
