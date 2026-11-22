using System;
using System.Drawing;
using System.Windows.Forms;

namespace AccountVault
{
    /// <summary>解锁 / 首次设置主密码对话框</summary>
    internal class PasswordDialog : Form
    {
        public string Password { get; private set; }
        private readonly bool _setup;
        private readonly TextBox _pw;
        private readonly TextBox _pw2;
        private readonly Label _err;

        public PasswordDialog(bool setup)
        {
            _setup = setup;
            Text = setup ? "设置主密码 · 账号仓库" : "解锁账号仓库";
            Size = new Size(420, setup ? 300 : 230);
            StartPosition = FormStartPosition.CenterScreen;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false; MinimizeBox = false;
            BackColor = Color.FromArgb(18, 18, 20);
            ForeColor = Color.FromArgb(235, 235, 240);
            Font = new Font("Microsoft YaHei UI", 10F);

            var title = new Label
            {
                Text = setup ? "设置主密码" : "解锁账号仓库",
                Dock = DockStyle.Top, Height = 46, TextAlign = ContentAlignment.MiddleCenter,
                Font = new Font("Microsoft YaHei UI", 16F, FontStyle.Bold),
                ForeColor = Color.White,
            };
            var sub = new Label
            {
                Text = setup ? "这个密码用来加密本机数据，忘了无法找回（至少 4 位）" : "输入主密码以解密你的数据",
                Dock = DockStyle.Top, Height = 34, TextAlign = ContentAlignment.MiddleCenter,
                ForeColor = Color.FromArgb(150, 150, 160),
            };

            _pw = MakePass("主密码", 60);
            var ok = new Button
            {
                Text = setup ? "创建保险库" : "解锁",
                Size = new Size(180, 40), DialogResult = DialogResult.None,
                BackColor = Color.FromArgb(10, 132, 255), ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat, Font = new Font("Microsoft YaHei UI", 11F, FontStyle.Bold),
            };
            ok.FlatAppearance.BorderSize = 0;
            ok.Location = new Point((ClientSize.Width - 180) / 2, setup ? 196 : 150);
            ok.Click += Ok_Click;

            _err = new Label
            {
                Text = "", ForeColor = Color.FromArgb(255, 69, 58),
                TextAlign = ContentAlignment.MiddleCenter, Bounds = new Rectangle(20, setup ? 240 : 190, 380, 24),
            };

            Controls.Add(_err);
            Controls.Add(ok);
            if (setup)
            {
                _pw2 = MakePass("再次输入主密码", 108);
                Controls.Add(_pw2);
            }
            Controls.Add(_pw);
            Controls.Add(sub);
            Controls.Add(title);
            AcceptButton = ok;
        }

        private TextBox MakePass(string placeholder, int y)
        {
            var t = new TextBox
            {
                Bounds = new Rectangle(50, y, 320, 38), UseSystemPasswordChar = true,
                BackColor = Color.FromArgb(44, 44, 48), ForeColor = Color.White,
                BorderStyle = BorderStyle.FixedSingle, Font = new Font("Microsoft YaHei UI", 11F),
                Text = "",
            };
            return t;
        }

        private void Ok_Click(object sender, EventArgs e)
        {
            var p = _pw.Text;
            if (string.IsNullOrEmpty(p)) { _err.Text = "请输入主密码"; return; }
            if (p.Length < 4) { _err.Text = "主密码至少 4 位"; return; }
            if (_setup)
            {
                if (p != _pw2.Text) { _err.Text = "两次输入不一致"; return; }
            }
            Password = p;
            DialogResult = DialogResult.OK;
            Close();
        }
    }
}
