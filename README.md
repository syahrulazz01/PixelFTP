# PixelFTP

> **Akses & transfer file laptop ke HP atau perangkat mana saja — mendukung mode LAN (Wi-Fi lokal) dan WAN (akses jarak jauh lewat internet dengan PIN & QR Code).**

![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Android-0284c7)
![Python](https://img.shields.io/badge/Python-3.10%2B-3776ab)
![Mode](https://img.shields.io/badge/Mode-LAN%20%26%20WAN%20Tunnel-16a34a)

---

## Fitur Utama

* **Mode WAN (Akses Jarak Jauh / Internet):**
  * **1-Klik Launcher:** Cukup jalankan `Klik_Untuk_Akses_WAN.bat` (atau shortcut Desktop `PixelFTP`).
  * **Keamanan PIN 6 Digit Acak:** Setiap sesi menghasilkan PIN baru, dilengkapi cookie sesi `HttpOnly` dan proteksi *rate-limit* (jeda 30 detik setelah 5x salah PIN).
  * **QR Code Langsung di Terminal:** Bisa langsung difoto dan di-scan dari kamera HP / Google Lens.
  * **Web File Manager:** Jelajahi seluruh drive (`C:\`, `D:\`, `Downloads`, `Desktop`, dll), cari file di folder aktif, dan unduh langsung dari browser mana saja.
  * **Zero Router Config:** Otomatis menggunakan `cloudflared` (jika terpasang) atau fallback ke OpenSSH (`localhost.run`) bawaan Windows.
* **Mode LAN (Wi-Fi Lokal):**
  * Kompatibel dengan aplikasi Android **PixelFTP** lewat UDP Beacon (`port 8889`) dan HTTP server (`port 8080`).
* **Installer 2-Klik (`PixelFTP_Installer.zip`):**
  * Otomatis memeriksa/memasang Python 3.12 (via `winget`), `qrcode`, menyalin aplikasi ke `%LOCALAPPDATA%\PixelFTP`, serta membuat shortcut **PixelFTP** berikon khusus di Desktop & Start Menu.

---

## Cara Pakai Cepat

### Opsi A — Pasang dengan Installer 2-Klik (Direkomendasikan untuk Pengguna Awam)
1. Unduh **`PixelFTP_Installer.zip`** (tersedia di repo atau halaman *Releases*).
2. Ekstrak file ZIP tersebut.
3. Klik ganda **`Install_PixelFTP.bat`** dan tunggu hingga selesai.
4. Klik ganda ikon **`PixelFTP`** yang muncul di **Desktop**.
5. Foto **QR Code + PIN** yang tampil di layar, lalu scan & masukkan PIN dari browser HP/laptop lain.

### Opsi B — Jalankan Langsung dari Source
* **Mode WAN (Internet + PIN + QR Code):**
  ```bat
  Klik_Untuk_Akses_WAN.bat
  ```
  atau via terminal:
  ```bash
  python server/server.py --wan --port 8088
  ```
* **Mode LAN (Wi-Fi Lokal untuk Aplikasi Android):**
  ```bat
  Bagi_File_ke_HP.bat
  ```
  atau via terminal:
  ```bash
  python server/server.py
  ```

---

## Struktur Proyek

```text
PixelFTP/
├── android/                    # Proyek aplikasi Android (Kotlin / Gradle)
├── server/
│   ├── server.py               # Server HTTP multi-thread (LAN + WAN + Web UI + PIN + QR)
│   └── pixelftp.ico            # Ikon aplikasi PixelFTP
├── installer/
│   ├── Install_PixelFTP.bat    # Installer otomatis (Python, qrcode, shortcut Desktop)
│   ├── server.py               # Salinan server untuk paket installer
│   ├── pixelftp.ico            # Ikon shortcut Desktop
│   └── BACA_SAYA.txt           # Panduan singkat
├── dist/
│   └── PixelFTP_Installer.zip  # Paket installer siap kirim
├── Bagi_File_ke_HP.bat         # Launcher cepat mode LAN
├── Klik_Untuk_Akses_WAN.bat    # Launcher cepat mode WAN (1-Klik)
└── README.md
```

---

## Kebutuhan Sistem (Windows)
* Windows 10 / 11 (OpenSSH `ssh.exe` sudah terpasang secara default).
* Python 3.10+ (otomatis dipasang oleh `Install_PixelFTP.bat` jika belum ada).
* Koneksi internet aktif untuk mode WAN.

---

## Catatan Keamanan
* **Link & PIN selalu baru** setiap kali server WAN dijalankan ulang.
* Jangan bagikan Link & PIN ke pihak yang tidak berkepentingan.
* Tutup jendela terminal `PixelFTP` segera setelah selesai mengunduh file untuk memutus akses jarak jauh.
