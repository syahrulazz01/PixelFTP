@echo off
chcp 65001 >nul
title Installer PixelFTP - Setup Otomatis
color 0B

echo ================================================================
echo              INSTALLER PIXELFTP (AKSES FILE WAN)
echo ================================================================
echo.

set "INSTALL_DIR=%LOCALAPPDATA%\PixelFTP"
set "PY_EXE=python"

:: 1. Cek Python (pastikan bukan stub Microsoft Store)
%PY_EXE% --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [1/4] Python belum terpasang. Menginstall Python 3.12 otomatis...
    winget install --id Python.Python.3.12 -e --silent --accept-package-agreements --accept-source-agreements
    if exist "%LOCALAPPDATA%\Programs\Python\Python312\python.exe" (
        set "PY_EXE=%LOCALAPPDATA%\Programs\Python\Python312\python.exe"
    ) else if exist "%ProgramFiles%\Python312\python.exe" (
        set "PY_EXE=%ProgramFiles%\Python312\python.exe"
    )
) else (
    echo [1/4] Python sudah terpasang.
)

"%PY_EXE%" --version >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo [GAGAL] Python belum berhasil dipasang otomatis.
    echo Silakan install Python dari https://www.python.org/downloads/
    echo lalu centang "Add Python to PATH", kemudian jalankan ulang installer ini.
    pause
    exit /b 1
)

:: 2. Pasang library qrcode untuk QR Code di terminal
echo [2/4] Menyiapkan komponen QR Code...
"%PY_EXE%" -m pip install --quiet --disable-pip-version-check qrcode >nul 2>&1

:: 3. Salin file aplikasi ke %LOCALAPPDATA%\PixelFTP
echo [3/4] Memasang file aplikasi ke %INSTALL_DIR%...
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
copy /Y "%~dp0server.py" "%INSTALL_DIR%\server.py" >nul
copy /Y "%~dp0pixelftp.ico" "%INSTALL_DIR%\pixelftp.ico" >nul

> "%INSTALL_DIR%\Run_PixelFTP.bat" (
    echo @echo off
    echo chcp 65001 ^>nul
    echo title PixelFTP - Akses File Jarak Jauh
    echo color 0A
    echo echo ================================================================
    echo echo       PIXELFTP - AKSES FILE JARAK JAUH ^(MODE WAN / INTERNET^)
    echo echo ================================================================
    echo echo.
    echo echo   Mohon tunggu sebentar, sedang menyiapkan QR Code ^& Link...
    echo echo   ^(Jangan tutup jendela ini selama file masih diakses^)
    echo echo.
    echo "%PY_EXE%" -u "%%~dp0server.py" --wan --port 8088
    echo echo.
    echo echo   Server telah berhenti.
    echo pause
)

:: 4. Buat Shortcut berikon PixelFTP di Desktop & Start Menu
echo [4/4] Membuat ikon aplikasi PixelFTP di Desktop ^& Start Menu...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ws = New-Object -ComObject WScript.Shell; " ^
  "$desktop = [Environment]::GetFolderPath('Desktop'); " ^
  "$startMenu = Join-Path ([Environment]::GetFolderPath('Programs')) 'PixelFTP.lnk'; " ^
  "foreach ($lnkPath in @((Join-Path $desktop 'PixelFTP.lnk'), $startMenu)) { " ^
  "  $s = $ws.CreateShortcut($lnkPath); " ^
  "  $s.TargetPath = '%INSTALL_DIR%\Run_PixelFTP.bat'; " ^
  "  $s.WorkingDirectory = '%INSTALL_DIR%'; " ^
  "  $s.IconLocation = '%INSTALL_DIR%\pixelftp.ico,0'; " ^
  "  $s.Description = 'PixelFTP - Akses File Laptop Jarak Jauh'; " ^
  "  $s.Save(); " ^
  "}"

echo.
echo ================================================================
echo   INSTALASI SELESAI!
echo   Aplikasi "PixelFTP" sudah muncul di Desktop ^& Start Menu.
echo   Tinggal klik 2x ikon PixelFTP kapan saja untuk mulai berbagi.
echo ================================================================
echo.
pause
