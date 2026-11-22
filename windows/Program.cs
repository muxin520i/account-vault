using System;
using System.Windows;

namespace AccountVault
{
    internal static class Program
    {
        [STAThread]
        static void Main()
        {
            var app = new Application();
            app.ShutdownMode = ShutdownMode.OnExplicitShutdown;

            // 首次：设置主密码；之后：解锁。成功后数据已解密存入 gate.Data
            var gate = new UnlockWindow();
            if (gate.ShowDialog() != true) { app.Shutdown(); return; }

            var main = new MainWindow(gate.Data);
            app.MainWindow = main;
            main.Closed += (s, e) => { Vault.Lock(); app.Shutdown(); };
            main.Show();
            app.Run();
        }
    }
}
