using System;
using System.Windows.Forms;

namespace AccountVault
{
    internal static class Program
    {
        [STAThread]
        static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.SetHighDpiMode(HighDpiMode.SystemAware);

            if (!Vault.IsSetup)
            {
                using var setup = new PasswordDialog(true);
                if (setup.ShowDialog() != DialogResult.OK) return;
                Vault.Create(setup.Password);
            }
            else
            {
                using var unlock = new PasswordDialog(false);
                if (unlock.ShowDialog() != DialogResult.OK) return;
                if (!Vault.Unlock(unlock.Password))
                {
                    MessageBox.Show("主密码错误，无法解锁。", "账号仓库", MessageBoxButtons.OK, MessageBoxIcon.Error);
                    return;
                }
            }

            Application.Run(new MainForm());
            Vault.Lock();
        }
    }
}
