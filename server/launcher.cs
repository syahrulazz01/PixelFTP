using System;
using System.Diagnostics;
using System.IO;
using System.Text;

class PixelFTPLauncher
{
    static string FindPython()
    {
        string[] candidates = new string[] {
            "python",
            "py",
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), @"Programs\Python\Python313\python.exe"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), @"Programs\Python\Python312\python.exe"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), @"Programs\Python\Python311\python.exe")
        };

        foreach (string cmd in candidates)
        {
            try
            {
                if (cmd.Contains(@"\") && !File.Exists(cmd)) continue;
                var psi = new ProcessStartInfo(cmd, cmd == "py" ? "-3 --version" : "--version")
                {
                    UseShellExecute = false,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    CreateNoWindow = true
                };
                using (var p = Process.Start(psi))
                {
                    p.WaitForExit(3000);
                    if (p.ExitCode == 0) return cmd;
                }
            }
            catch { }
        }
        return null;
    }

    static int Main(string[] args)
    {
        Console.OutputEncoding = Encoding.UTF8;
        Console.InputEncoding = Encoding.UTF8;
        Console.Title = "PixelFTP - Akses File Laptop";

        string baseDir = AppDomain.CurrentDomain.BaseDirectory;
        string serverPy = Path.Combine(baseDir, "server", "server.py");
        if (!File.Exists(serverPy))
        {
            Console.ForegroundColor = ConsoleColor.Red;
            Console.WriteLine("[Error] File server\\server.py tidak ditemukan di: " + baseDir);
            Console.ResetColor();
            Console.WriteLine("Tekan Enter untuk keluar...");
            Console.ReadLine();
            return 1;
        }

        string pythonCmd = FindPython();
        if (pythonCmd == null)
        {
            Console.ForegroundColor = ConsoleColor.Red;
            Console.WriteLine("[Error] Python belum terpasang di laptop ini.");
            Console.ResetColor();
            Console.WriteLine("Silakan jalankan Setup_PixelFTP.exe terlebih dahulu.");
            Console.WriteLine("Tekan Enter untuk keluar...");
            Console.ReadLine();
            return 1;
        }

        string mode = "";
        if (args.Length > 0)
        {
            string a = args[0].ToLowerInvariant();
            if (a == "--wifi" || a == "--lan") mode = "2";
            else if (a == "--internet" || a == "--wan") mode = "1";
        }

        if (string.IsNullOrEmpty(mode))
        {
            Console.Clear();
            Console.ForegroundColor = ConsoleColor.Green;
            Console.WriteLine("================================================================");
            Console.WriteLine("                   PIXELFTP - AKSES FILE LAPTOP                 ");
            Console.WriteLine("================================================================");
            Console.ResetColor();
            Console.WriteLine();
            Console.WriteLine("  Pilih cara akses yang ingin digunakan:");
            Console.WriteLine();
            Console.ForegroundColor = ConsoleColor.Cyan;
            Console.WriteLine("  [1] Ambil File Jarak Jauh (Lewat Internet)");
            Console.ResetColor();
            Console.WriteLine("      -> Untuk akses dari luar rumah / beda kota.");
            Console.WriteLine("      -> Muncul QR Code & PIN, buka lewat browser HP.");
            Console.WriteLine();
            Console.ForegroundColor = ConsoleColor.Yellow;
            Console.WriteLine("  [2] Kirim File di Satu Wi-Fi (HP & Laptop Satu Jaringan)");
            Console.ResetColor();
            Console.WriteLine("      -> Untuk transfer cepat lewat Wi-Fi rumah/kantor.");
            Console.WriteLine();
            Console.WriteLine("================================================================");
            Console.Write("  Ketik 1 atau 2 lalu tekan Enter (Default = 1): ");
            string input = (Console.ReadLine() ?? "").Trim();
            mode = (input == "2") ? "2" : "1";
            Console.Clear();
        }

        string pyPrefix = (pythonCmd == "py") ? "-3 -u " : "-u ";
        string serverArgs = (mode == "2")
            ? pyPrefix + "\"" + serverPy + "\" --port 8080"
            : pyPrefix + "\"" + serverPy + "\" --wan --port 8088";

        Console.Title = (mode == "2")
            ? "PixelFTP - Kirim File (Satu Wi-Fi)"
            : "PixelFTP - Akses File Jarak Jauh (Internet)";

        var runInfo = new ProcessStartInfo(pythonCmd, serverArgs)
        {
            UseShellExecute = false,
            WorkingDirectory = baseDir
        };

        using (var proc = Process.Start(runInfo))
        {
            proc.WaitForExit();
        }

        Console.WriteLine();
        Console.WriteLine("Server berhenti. Tekan Enter untuk menutup jendela ini...");
        Console.ReadLine();
        return 0;
    }
}
