@echo off
title MediCircle Web Server
cd /d "%~dp0"
echo ==================================================
echo   Starting MediCircle Web Server (Java + DBMS)
echo   Accessible at: http://localhost:8080
echo ==================================================
start http://localhost:8080
java -cp "out;lib/*" medicircle.WebServer
pause
