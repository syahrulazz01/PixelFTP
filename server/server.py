#!/usr/bin/env python3
import argparse
import getpass
import hmac
import html
import json
import os
import re
import secrets
import shutil
import socket
import string
import subprocess
import sys
import threading
import time
import urllib.parse
from http.cookies import SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = 8080
UDP_BEACON_PORT = 8889

# Runtime security state (only enforced when REQUIRED_PIN is set)
REQUIRED_PIN = None
VALID_SESSIONS = set()
SESSION_LOCK = threading.Lock()
FAILED_ATTEMPTS = {}  # ip -> {"count": int, "locked_until": float}


def get_local_ip():
    """Get preferred local Wi-Fi or LAN IPv4 address."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.settimeout(0.5)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def get_available_drives():
    drives = []
    for letter in string.ascii_uppercase:
        drive_path = f"{letter}:\\"
        if os.path.exists(drive_path):
            name = f"Local Disk ({letter}:)"
            if letter == "C":
                name = "Windows (C:)"
            drives.append({"name": name, "path": drive_path})
    return drives


def get_system_shortcuts():
    user_home = os.path.expanduser("~")
    candidates = [
        ("Downloads", os.path.join(user_home, "Downloads"), "download"),
        ("Desktop", os.path.join(user_home, "Desktop"), "desktop"),
        ("Documents", os.path.join(user_home, "Documents"), "document"),
        ("Pictures", os.path.join(user_home, "Pictures"), "image"),
        ("Videos", os.path.join(user_home, "Videos"), "video"),
        ("Music", os.path.join(user_home, "Music"), "music"),
        ("User Home", user_home, "home"),
    ]
    shortcuts = []
    for name, path, icon in candidates:
        if os.path.exists(path):
            shortcuts.append({"name": name, "path": path, "icon": icon})
    return shortcuts


def render_login_html(hostname):
    safe_host = html.escape(hostname)
    return f"""<!DOCTYPE html>
<html lang="id">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>PixelFTP Remote - Masuk</title>
  <style>
    :root {{ color-scheme: dark; }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0; font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
      background: #0b1120; color: #e2e8f0;
      min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 20px;
    }}
    .card {{
      width: 100%; max-width: 380px; background: #111827; border: 1px solid #1e293b;
      border-radius: 16px; padding: 28px; box-shadow: 0 20px 50px rgba(0,0,0,0.45);
      text-align: center;
    }}
    h1 {{ margin: 0 0 6px; font-size: 22px; color: #38bdf8; }}
    p {{ margin: 0 0 20px; font-size: 14px; color: #94a3b8; }}
    input {{
      width: 100%; padding: 14px; font-size: 20px; text-align: center; letter-spacing: 6px;
      border-radius: 10px; border: 1px solid #334155; background: #0f172a; color: #f8fafc;
      outline: none; margin-bottom: 14px;
    }}
    input:focus {{ border-color: #38bdf8; }}
    button {{
      width: 100%; padding: 13px; font-size: 15px; font-weight: 600; border: none;
      border-radius: 10px; background: #0284c7; color: #fff; cursor: pointer;
    }}
    button:hover {{ background: #0369a1; }}
    .err {{ color: #f87171; font-size: 13px; margin-top: 12px; min-height: 18px; }}
  </style>
</head>
<body>
  <div class="card">
    <h1>PixelFTP Remote</h1>
    <p>Laptop: <strong>{safe_host}</strong><br/>Masukkan PIN akses untuk membuka file.</p>
    <form id="loginForm">
      <input id="pin" type="password" inputmode="numeric" autocomplete="one-time-code" placeholder="PIN" required autofocus />
      <button type="submit">Buka Akses File</button>
    </form>
    <div id="err" class="err"></div>
  </div>
  <script>
    const form = document.getElementById('loginForm');
    const errEl = document.getElementById('err');
    form.addEventListener('submit', async (e) => {{
      e.preventDefault();
      errEl.textContent = '';
      const pin = document.getElementById('pin').value.trim();
      try {{
        const res = await fetch('/api/auth', {{
          method: 'POST',
          headers: {{ 'Content-Type': 'application/json' }},
          body: JSON.stringify({{ pin }})
        }});
        const data = await res.json();
        if (res.ok && data.ok) {{
          location.reload();
        }} else {{
          errEl.textContent = data.error || 'PIN salah';
        }}
      }} catch (err) {{
        errEl.textContent = 'Gagal terhubung ke server';
      }}
    }});
  </script>
</body>
</html>"""


def render_app_html(hostname, user, has_pin):
    safe_host = html.escape(hostname)
    safe_user = html.escape(user)
    logout_btn = '<button class="btn btn-danger" onclick="logout()">Kunci / Keluar</button>' if has_pin else ''
    return f"""<!DOCTYPE html>
<html lang="id">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>PixelFTP Remote - {safe_host}</title>
  <style>
    :root {{ color-scheme: dark; }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0; font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
      background: #0b1120; color: #e2e8f0;
    }}
    header {{
      background: #111827; border-bottom: 1px solid #1e293b; padding: 14px 20px;
      display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px;
      position: sticky; top: 0; z-index: 10;
    }}
    .brand {{ font-weight: 700; font-size: 18px; color: #38bdf8; }}
    .meta {{ font-size: 13px; color: #94a3b8; }}
    .container {{ max-width: 1100px; margin: 0 auto; padding: 16px; }}
    .chips {{ display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }}
    .chip {{
      background: #1e293b; color: #e2e8f0; border: 1px solid #334155; border-radius: 999px;
      padding: 7px 13px; font-size: 13px; cursor: pointer;
    }}
    .chip:hover {{ background: #334155; border-color: #38bdf8; }}
    .toolbar {{
      display: flex; flex-wrap: wrap; gap: 8px; align-items: center;
      background: #111827; border: 1px solid #1e293b; border-radius: 12px; padding: 10px 12px;
      margin-bottom: 14px;
    }}
    .path-box {{
      flex: 1 1 260px; font-family: ui-monospace, Consolas, monospace; font-size: 13px;
      color: #cbd5e1; word-break: break-all;
    }}
    .search {{
      padding: 8px 12px; border-radius: 8px; border: 1px solid #334155;
      background: #0f172a; color: #f8fafc; font-size: 13px; min-width: 170px;
    }}
    .btn {{
      padding: 8px 12px; border-radius: 8px; border: 1px solid #334155;
      background: #1e293b; color: #f8fafc; font-size: 13px; font-weight: 600; cursor: pointer;
      text-decoration: none; display: inline-flex; align-items: center; gap: 6px;
    }}
    .btn:hover {{ background: #334155; }}
    .btn:disabled {{ opacity: 0.45; cursor: not-allowed; }}
    .btn-primary {{ background: #0284c7; border-color: #0284c7; }}
    .btn-primary:hover {{ background: #0369a1; }}
    .btn-danger {{ background: #991b1b; border-color: #b91c1c; }}
    .btn-danger:hover {{ background: #b91c1c; }}
    .list {{
      background: #111827; border: 1px solid #1e293b; border-radius: 12px; overflow: hidden;
    }}
    .row {{
      display: flex; align-items: center; justify-content: space-between; gap: 12px;
      padding: 12px 14px; border-bottom: 1px solid #1e293b;
    }}
    .row:last-child {{ border-bottom: none; }}
    .row:hover {{ background: #162033; }}
    .row-main {{
      display: flex; align-items: center; gap: 10px; min-width: 0; flex: 1;
      cursor: pointer;
    }}
    .icon {{ font-size: 18px; flex-shrink: 0; }}
    .name {{ font-size: 14px; font-weight: 500; word-break: break-word; }}
    .info {{ font-size: 12px; color: #94a3b8; white-space: nowrap; }}
    .empty {{ padding: 32px; text-align: center; color: #94a3b8; font-size: 14px; }}
    @media (max-width: 600px) {{
      .info {{ display: none; }}
    }}
  </style>
</head>
<body>
  <header>
    <div>
      <div class="brand">PixelFTP Remote</div>
      <div class="meta">{safe_host} ({safe_user})</div>
    </div>
    <div style="display:flex;gap:8px;align-items:center;">
      <button class="btn" onclick="refreshCurrent()">Refresh</button>
      {logout_btn}
    </div>
  </header>

  <div class="container">
    <div id="shortcuts" class="chips"></div>
    <div class="toolbar">
      <button id="upBtn" class="btn" onclick="goUp()" disabled>⬆ Naik</button>
      <div id="currentPath" class="path-box">Memuat...</div>
      <input id="filterInput" class="search" type="search" placeholder="Cari di folder ini..." oninput="renderItems()" />
    </div>
    <div id="fileList" class="list">
      <div class="empty">Memuat daftar file...</div>
    </div>
  </div>

  <script>
    let state = {{ currentPath: '', parentPath: null, items: [] }};

    function formatBytes(bytes) {{
      if (!bytes) return '0 B';
      const units = ['B', 'KB', 'MB', 'GB', 'TB'];
      let i = 0;
      let val = bytes;
      while (val >= 1024 && i < units.length - 1) {{ val /= 1024; i++; }}
      return (i === 0 ? val : val.toFixed(1)) + ' ' + units[i];
    }}

    function formatDate(ms) {{
      if (!ms) return '';
      return new Date(ms).toLocaleString('id-ID', {{ dateStyle: 'short', timeStyle: 'short' }});
    }}

    async function loadShortcuts() {{
      const res = await fetch('/api/shortcuts');
      if (res.status === 401) {{ location.reload(); return; }}
      const data = await res.json();
      const box = document.getElementById('shortcuts');
      box.innerHTML = '';
      const all = [...(data.drives || []), ...(data.shortcuts || [])];
      all.forEach(item => {{
        const btn = document.createElement('button');
        btn.className = 'chip';
        btn.textContent = (item.icon ? '📁 ' : '💾 ') + item.name;
        btn.onclick = () => loadDir(item.path);
        box.appendChild(btn);
      }});
    }}

    async function loadDir(path = '') {{
      const listEl = document.getElementById('fileList');
      listEl.innerHTML = '<div class="empty">Memuat...</div>';
      const url = path ? '/api/list?path=' + encodeURIComponent(path) : '/api/list';
      const res = await fetch(url);
      if (res.status === 401) {{ location.reload(); return; }}
      const data = await res.json();
      if (!res.ok) {{
        listEl.innerHTML = `<div class="empty">${{data.error || 'Gagal membuka folder'}}</div>`;
        return;
      }}
      state.currentPath = data.currentPath;
      state.parentPath = data.parentPath;
      state.items = data.items || [];
      document.getElementById('currentPath').textContent = state.currentPath;
      document.getElementById('upBtn').disabled = !state.parentPath;
      document.getElementById('filterInput').value = '';
      renderItems();
    }}

    function renderItems() {{
      const q = document.getElementById('filterInput').value.trim().toLowerCase();
      const listEl = document.getElementById('fileList');
      const filtered = q ? state.items.filter(i => i.name.toLowerCase().includes(q)) : state.items;
      if (!filtered.length) {{
        listEl.innerHTML = '<div class="empty">Folder kosong atau tidak ada yang cocok.</div>';
        return;
      }}
      listEl.innerHTML = '';
      filtered.forEach(item => {{
        const row = document.createElement('div');
        row.className = 'row';

        const main = document.createElement('div');
        main.className = 'row-main';
        const icon = document.createElement('span');
        icon.className = 'icon';
        icon.textContent = item.isDir ? '📁' : '📄';
        const name = document.createElement('span');
        name.className = 'name';
        name.textContent = item.name;
        main.appendChild(icon);
        main.appendChild(name);

        if (item.isDir) {{
          main.onclick = () => loadDir(item.path);
        }} else {{
          main.onclick = () => {{ window.location.href = '/api/download?path=' + encodeURIComponent(item.path); }};
        }}

        const right = document.createElement('div');
        right.style.display = 'flex';
        right.style.alignItems = 'center';
        right.style.gap = '10px';

        const info = document.createElement('span');
        info.className = 'info';
        info.textContent = item.isDir ? formatDate(item.modified) : `${{formatBytes(item.size)}} • ${{formatDate(item.modified)}}`;
        right.appendChild(info);

        if (!item.isDir) {{
          const dl = document.createElement('a');
          dl.className = 'btn btn-primary';
          dl.href = '/api/download?path=' + encodeURIComponent(item.path);
          dl.textContent = 'Unduh';
          right.appendChild(dl);
        }}

        row.appendChild(main);
        row.appendChild(right);
        listEl.appendChild(row);
      }});
    }}

    function goUp() {{
      if (state.parentPath) loadDir(state.parentPath);
    }}

    function refreshCurrent() {{
      loadDir(state.currentPath);
    }}

    async function logout() {{
      await fetch('/api/logout', {{ method: 'POST' }});
      location.reload();
    }}

    loadShortcuts();
    loadDir();
  </script>
</body>
</html>"""


class PixelShareHandler(BaseHTTPRequestHandler):
    server_version = "PixelShare/2.0"

    def _client_ip(self):
        return self.headers.get("CF-Connecting-IP") or self.headers.get("X-Forwarded-For", "").split(",")[0].strip() or self.client_address[0]

    def _is_authenticated(self, query=None):
        if not REQUIRED_PIN:
            return True

        # 1. Header PIN
        header_pin = self.headers.get("X-PixelFTP-Pin")
        if header_pin and hmac.compare_digest(header_pin, REQUIRED_PIN):
            return True

        # 2. Query param PIN
        if query:
            q_pin = query.get("pin", [None])[0]
            if q_pin and hmac.compare_digest(q_pin, REQUIRED_PIN):
                return True

        # 3. Session cookie
        cookie_hdr = self.headers.get("Cookie", "")
        if cookie_hdr:
            cookie = SimpleCookie()
            try:
                cookie.load(cookie_hdr)
                if "pixelftp_session" in cookie:
                    token = cookie["pixelftp_session"].value
                    with SESSION_LOCK:
                        if token in VALID_SESSIONS:
                            return True
            except Exception:
                pass

        return False

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, X-PixelFTP-Pin")
        self.end_headers()

    def send_json(self, data, status=200, extra_headers=None):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        if extra_headers:
            for k, v in extra_headers.items():
                self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def send_html(self, content, status=200):
        body = content.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        if path == "/api/auth":
            if not REQUIRED_PIN:
                self.send_json({"ok": True})
                return

            ip = self._client_ip()
            now = time.time()
            with SESSION_LOCK:
                rec = FAILED_ATTEMPTS.get(ip, {"count": 0, "locked_until": 0})
                if rec["locked_until"] > now:
                    wait_s = int(rec["locked_until"] - now) + 1
                    self.send_json({"ok": False, "error": f"Terlalu banyak percobaan. Tunggu {wait_s} detik."}, status=429)
                    return

            try:
                length = int(self.headers.get("Content-Length", "0"))
                raw = self.rfile.read(min(length, 4096))
                payload = json.loads(raw.decode("utf-8"))
                pin = str(payload.get("pin", "")).strip()
            except Exception:
                self.send_json({"ok": False, "error": "Format request tidak valid"}, status=400)
                return

            if hmac.compare_digest(pin, REQUIRED_PIN):
                token = secrets.token_urlsafe(32)
                with SESSION_LOCK:
                    VALID_SESSIONS.add(token)
                    FAILED_ATTEMPTS.pop(ip, None)
                self.send_json(
                    {"ok": True},
                    status=200,
                    extra_headers={"Set-Cookie": f"pixelftp_session={token}; HttpOnly; SameSite=Lax; Path=/"}
                )
            else:
                with SESSION_LOCK:
                    rec = FAILED_ATTEMPTS.get(ip, {"count": 0, "locked_until": 0})
                    rec["count"] += 1
                    if rec["count"] >= 5:
                        rec["locked_until"] = now + 30
                        rec["count"] = 0
                    FAILED_ATTEMPTS[ip] = rec
                self.send_json({"ok": False, "error": "PIN salah"}, status=401)
            return

        if path == "/api/logout":
            cookie_hdr = self.headers.get("Cookie", "")
            if cookie_hdr:
                cookie = SimpleCookie()
                try:
                    cookie.load(cookie_hdr)
                    if "pixelftp_session" in cookie:
                        with SESSION_LOCK:
                            VALID_SESSIONS.discard(cookie["pixelftp_session"].value)
                except Exception:
                    pass
            self.send_json(
                {"ok": True},
                status=200,
                extra_headers={"Set-Cookie": "pixelftp_session=; HttpOnly; Max-Age=0; Path=/"}
            )
            return

        self.send_json({"error": "Endpoint tidak ditemukan"}, status=404)

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        query = urllib.parse.parse_qs(parsed.query)

        if path == "/" or path == "/index.html":
            hostname = socket.gethostname()
            if not self._is_authenticated(query):
                self.send_html(render_login_html(hostname))
            else:
                self.send_html(render_app_html(hostname, getpass.getuser(), bool(REQUIRED_PIN)))
            return

        if not self._is_authenticated(query):
            self.send_json({"error": "PIN diperlukan"}, status=401)
            return

        if path == "/api/status" or path == "/api/ping":
            self.send_json({
                "status": "ok",
                "hostname": socket.gethostname(),
                "user": getpass.getuser(),
                "os": "Windows",
                "pinProtected": bool(REQUIRED_PIN),
            })
            return

        if path == "/api/shortcuts":
            self.send_json({
                "shortcuts": get_system_shortcuts(),
                "drives": get_available_drives(),
            })
            return

        if path == "/api/list":
            target_path = query.get("path", [""])[0]
            if not target_path:
                target_path = os.path.expanduser("~")

            target_path = os.path.abspath(target_path)
            if not os.path.exists(target_path):
                self.send_json({"error": "Path tidak ditemukan"}, status=404)
                return

            try:
                folders = []
                files = []
                with os.scandir(target_path) as entries:
                    for entry in entries:
                        try:
                            if entry.name.startswith("$") or entry.name == "System Volume Information":
                                continue
                            stat = entry.stat(follow_symlinks=False)
                            is_dir = entry.is_dir(follow_symlinks=False)
                            item_info = {
                                "name": entry.name,
                                "path": entry.path,
                                "isDir": is_dir,
                                "size": stat.st_size if not is_dir else 0,
                                "modified": int(stat.st_mtime * 1000),
                            }
                            if is_dir:
                                folders.append(item_info)
                            else:
                                files.append(item_info)
                        except (PermissionError, OSError):
                            continue

                folders.sort(key=lambda x: x["name"].lower())
                files.sort(key=lambda x: x["name"].lower())

                parent = os.path.dirname(target_path)
                if parent == target_path:
                    parent = None

                self.send_json({
                    "currentPath": target_path,
                    "parentPath": parent,
                    "items": folders + files,
                })
            except PermissionError:
                self.send_json({"error": "Akses ditolak ke folder ini"}, status=403)
            except Exception as e:
                self.send_json({"error": str(e)}, status=500)
            return

        if path == "/api/download":
            target_path = query.get("path", [""])[0]
            if not target_path or not os.path.isfile(target_path):
                self.send_json({"error": "File tidak ditemukan"}, status=404)
                return

            try:
                file_size = os.path.getsize(target_path)
                filename = os.path.basename(target_path)
                quoted_filename = urllib.parse.quote(filename)

                self.send_response(200)
                self.send_header("Content-Type", "application/octet-stream")
                self.send_header("Content-Disposition", f'attachment; filename="{filename}"; filename*=UTF-8\'\'{quoted_filename}')
                self.send_header("Content-Length", str(file_size))
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()

                with open(target_path, "rb") as f:
                    while chunk := f.read(65536):
                        self.wfile.write(chunk)
            except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
                pass
            except Exception as e:
                print(f"[Error] Download failed for {target_path}: {e}")
            return

        self.send_json({"error": "Endpoint tidak ditemukan"}, status=404)

    def log_message(self, format, *args):
        print(f"[{time.strftime('%H:%M:%S')}] {args[0]} - {args[1]} - {args[2]}")


def start_udp_beacon(local_ip, port):
    """Broadcast UDP beacon every 2 seconds for zero-config LAN discovery."""
    def beacon_worker():
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

        msg = json.dumps({
            "service": "pixelftp_laptop",
            "hostname": socket.gethostname(),
            "ip": local_ip,
            "port": port,
            "url": f"http://{local_ip}:{port}",
        }).encode("utf-8")

        while True:
            try:
                sock.sendto(msg, ("255.255.255.255", UDP_BEACON_PORT))
            except Exception:
                pass
            time.sleep(2)

    t = threading.Thread(target=beacon_worker, daemon=True)
    t.start()


def find_cloudflared():
    exe = shutil.which("cloudflared")
    if exe:
        return exe
    candidates = [
        r"C:\Program Files (x86)\cloudflared\cloudflared.exe",
        r"C:\Program Files\cloudflared\cloudflared.exe",
        os.path.expandvars(r"%LOCALAPPDATA%\Microsoft\WinGet\Links\cloudflared.exe"),
    ]
    for c in candidates:
        if os.path.isfile(c):
            return c
    return None


def start_wan_tunnel(port, pin):
    """Start Cloudflare Quick Tunnel (or SSH fallback) and print a clear WAN card."""
    cf_exe = find_cloudflared()
    if cf_exe:
        cmd = [cf_exe, "tunnel", "--url", f"http://127.0.0.1:{port}", "--no-autoupdate"]
        url_pattern = re.compile(r"https://[a-zA-Z0-9-]+\.trycloudflare\.com")
        provider = "Cloudflare Quick Tunnel"
    else:
        ssh_exe = shutil.which("ssh")
        if not ssh_exe:
            print("[WAN Error] cloudflared maupun ssh tidak ditemukan di laptop ini.")
            return None
        cmd = [
            ssh_exe,
            "-T",
            "-o", "BatchMode=yes",
            "-o", "StrictHostKeyChecking=no",
            "-o", "UserKnownHostsFile=NUL",
            "-o", "ConnectTimeout=10",
            "-o", "ServerAliveInterval=30",
            "-R", f"80:127.0.0.1:{port}",
            "nokey@localhost.run",
        ]
        url_pattern = re.compile(r"https://[a-zA-Z0-9.-]+\.lhr\.life")
        provider = "localhost.run (SSH Fallback)"

    proc = subprocess.Popen(
        cmd,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
    )

    def monitor_tunnel():
        announced = False
        recent_lines = []
        for line in proc.stdout:
            clean = line.strip()
            if clean:
                recent_lines.append(clean)
                if len(recent_lines) > 10:
                    recent_lines.pop(0)
            match = url_pattern.search(line)
            if match and not announced:
                announced = True
                public_url = match.group(0)
                print("\n" + "=" * 64)
                print("  SIAP DIAKSES DARI JARAK JAUH (INTERNET)")
                print("=" * 64)
                print(f"  LINK     : {public_url}")
                print(f"  PIN      : {pin}")
                print("=" * 64)
                try:
                    import qrcode
                    qr = qrcode.QRCode(border=2)
                    qr.add_data(public_url)
                    qr.make(fit=True)
                    print("  SCAN QR CODE INI PAKAI KAMERA HP:")
                    qr.print_ascii(invert=True)
                    print("=" * 64)
                except Exception:
                    pass
                print("  PETUNJUK:")
                print("  1. JANGAN tutup jendela hitam ini selama file masih diakses.")
                print("  2. Foto layar ini (QR Code + PIN) dan kirim ke pemilik laptop.")
                print("=" * 64 + "\n")
                sys.stdout.flush()
        if not announced:
            print("\n[Gagal] Tidak bisa membuat link akses internet. Detail:")
            for l in recent_lines:
                print(f"  > {l}")
            print("[Tips] Pastikan laptop terhubung ke internet.\n")
            sys.stdout.flush()

    t = threading.Thread(target=monitor_tunnel, daemon=True)
    t.start()
    return proc


def main():
    global REQUIRED_PIN, PORT

    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="PixelShare / PixelFTP Laptop Server")
    parser.add_argument("--port", type=int, default=8080, help="Port server HTTP (default: 8080)")
    parser.add_argument("--pin", type=str, default=None, help="PIN akses (opsional untuk LAN, otomatis untuk WAN)")
    parser.add_argument("--wan", action="store_true", help="Aktifkan mode WAN (internet) + PIN otomatis")
    args = parser.parse_args()

    PORT = args.port
    if args.pin:
        REQUIRED_PIN = args.pin.strip()
    elif args.wan:
        REQUIRED_PIN = f"{secrets.randbelow(900000) + 100000}"

    local_ip = get_local_ip()
    httpd = ThreadingHTTPServer(("0.0.0.0", PORT), PixelShareHandler)

    if not args.wan:
        start_udp_beacon(local_ip, PORT)

    print("=" * 64)
    print("               PIXELFTP - AKSES FILE LAPTOP")
    print("=" * 64)
    print(f"  Laptop   : {socket.gethostname()} ({getpass.getuser()})")
    if not args.wan:
        print(f"  Link WiFi: http://{local_ip}:{PORT}")
    if REQUIRED_PIN:
        print(f"  PIN Akses: {REQUIRED_PIN}")
    print("=" * 64)

    tunnel_proc = None
    if args.wan:
        print("  Sedang menyiapkan link akses internet... Mohon tunggu sebentar...\n")
        tunnel_proc = start_wan_tunnel(PORT, REQUIRED_PIN)
    else:
        print("  Status   : AKTIF (Siap diakses lewat Wi-Fi yang sama)")
        print("  Tutup jendela ini jika sudah selesai.\n")

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n[Server dihentikan oleh pengguna]")
    finally:
        if tunnel_proc and tunnel_proc.poll() is None:
            tunnel_proc.terminate()
        httpd.server_close()


if __name__ == "__main__":
    main()
