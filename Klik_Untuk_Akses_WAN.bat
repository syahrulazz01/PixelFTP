@echo off
chcp 65001 >nul
title PixelFTP WAN - Akses File Jarak Jauh (1-Klik)
color 0A

echo ================================================================
echo     PIXELFTP - AKSES FILE JARAK JAUH (MODE WAN / INTERNET)
echo ================================================================
echo.
echo   Mohon tunggu sebentar, sedang menyiapkan link akses...
echo   (Jangan tutup jendela ini selama file masih diakses)
echo.

python -u "%~dp0server_laptop\server.py" --wan --port 8088

echo.
echo   Server telah berhenti.
pause
