@echo off
title PixelFTP - Server Laptop
color 0B

echo ================================================================
echo           PIXELFTP - SERVER BERBAGI FILE LAPTOP KE HP
echo ================================================================
echo.

:: Cek Administrator untuk izin Firewall jika diperlukan
net session >nul 2>&1
if %errorlevel% equ 0 (
    echo [OK] Mode Administrator terdeteksi. Mengatur izin Firewall...
    netsh advfirewall firewall delete rule name="python.exe" >nul 2>&1
    netsh advfirewall firewall delete rule name="PixelFTP Server" >nul 2>&1
    netsh advfirewall firewall add rule name="PixelFTP Server" dir=in action=allow protocol=TCP localport=8080 profile=any >nul 2>&1
    echo [OK] Port 8080 berhasil dibuka di Firewall Windows!
    echo.
) else (
    echo [Tips] Jika HP kesulitan terhubung lewat Wi-Fi, klik kanan file ini
    echo        lalu pilih "Run as administrator" satu kali saja.
    echo.
)

echo Menjalankan server...
python -u "%~dp0server\server.py"

pause
