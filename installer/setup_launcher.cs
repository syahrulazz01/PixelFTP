using System;
using System.Diagnostics;
using System.IO;

class SetupLauncher
{
    static int Main()
    {
        string baseDir = AppDomain.CurrentDomain.BaseDirectory;
        string batPath = Path.Combine(baseDir, "Install_PixelFTP.bat");
        if (!File.Exists(batPath))
        {
            batPath = Path.Combine(baseDir, "data", "Install_PixelFTP.bat");
        }
        if (!File.Exists(batPath))
        {
            Console.WriteLine("[Error] File Install_PixelFTP.bat tidak ditemukan.");
            Console.WriteLine("Tekan Enter untuk keluar...");
            Console.ReadLine();
            return 1;
        }

        var psi = new ProcessStartInfo("cmd.exe", "/c \"" + batPath + "\"")
        {
            UseShellExecute = false,
            WorkingDirectory = Path.GetDirectoryName(batPath)
        };
        using (var p = Process.Start(psi))
        {
            p.WaitForExit();
            return p.ExitCode;
        }
    }
}
