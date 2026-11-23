# PixelFTP

Akses dan transfer file laptop **jarak jauh lewat internet** (cukup pakai browser HP/laptop lain, **tanpa perlu install aplikasi di HP**) maupun transfer cepat **di satu jaringan Wi-Fi** (termasuk dengan aplikasi Android PixelShare / PixelFTP).

## Untuk Pengguna Umum (Cara Paling Mudah)

1. Download **`PixelFTP_Installer.zip`** dari halaman [Releases](https://github.com/syahrulazz01/PixelFTP/releases/latest).
2. Ekstrak file ZIP tersebut, lalu klik 2x **`Setup_PixelFTP.exe`**.
3. Setelah selesai, aplikasi **`PixelFTP`** akan muncul di **Desktop** dan **Start Menu**.
4. Klik 2x **`PixelFTP`**, lalu pilih:
   - **`[1]` Ambil File Jarak Jauh (Lewat Internet)** -> Muncul **QR Code**, **Link**, dan **PIN 6 digit**. Tinggal scan atau buka link di browser HP dari mana saja!
   - **`[2]` Kirim File di Satu Wi-Fi** -> Untuk transfer cepat saat HP dan laptop berada di Wi-Fi yang sama.

> **Keamanan:** Mode jarak jauh otomatis dilindungi **PIN 6 digit acak** setiap kali dijalankan dan dibatasi maksimal 5x salah PIN.

## Struktur Project (Untuk Developer)

```text
PixelFTP/
├── PixelFTP.exe            # Launcher utama berikon (menu Bahasa Indonesia sederhana)
├── dist/
│   └── PixelFTP_Installer.zip  # Paket installer siap bagikan ke pengguna
├── installer/              # Source pembuat installer (Setup_PixelFTP.exe & Install_PixelFTP.bat)
├── server/                 # Source server Python (server.py), launcher C#, & ikon (.ico)
└── android/                # Source aplikasi Android (opsional untuk mode satu Wi-Fi)
```

## Menjalankan Langsung dari Source

Pastikan Python 3.10+ sudah terpasang (`pip install qrcode`), lalu klik 2x **`PixelFTP.exe`** atau jalankan:

```bash
python server/server.py --wan --port 8088   # Akses jarak jauh (internet + PIN + QR Code)
python server/server.py --port 8080         # Akses satu Wi-Fi
```
