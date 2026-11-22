using System;
using System.Drawing;
using System.Windows.Forms;

namespace AccountVault
{
    internal class ChangePasswordForm : Form
    {
        private readonly TextBox _old, _nw, _nw2;
        public ChangePasswordForm()
        {
            Text = "修改主密码";
            Size = new Size(420, 300);
            StartPosition = FormStartPosition.CenterParent;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false; MinimizeBox = false;
            BackColor = Color.FromArgb(28, 28, 32);
            ForeColor = Color.FromArgb(235, 235, 240);
            Font = new Font("Microsoft YaHei UI", 10F);

            _old = Pass("当前主密码", 30);
            _nw = Pass("新主密码（至少 4 位）", 96);
            _nw2 = Pass("再次输入新主密码", 162);
            var ok = new Button { Text = "确定", Bounds = new Point(120, 225), Size = new Size(100, 36), DialogResult = DialogResult.None, BackColor = Color.FromArgb(10, 132, 255), ForeColor = Color.White, FlatStyle = FlatStyle.Flat };
            ok.FlatAppearance.BorderSize = 0;
            var cancel = new Button { Text = "取消", Bounds = new Point(240, 225), Size = new Size(80, 36), DialogResult = DialogResult.Cancel, BackColor = Color.FromArgb(60, 60, 66), ForeColor = Color.White, FlatStyle = FlatStyle.Flat };
            cancel.FlatAppearance.BorderSize = 0;
            ok.Click += Ok_Click;
            Controls.Add(cancel); Controls.Add(ok); Controls.Add(_old); Controls.Add(_nw); Controls.Add(_nw2);
        }

        private TextBox Pass(string label, int y)
        {
            var lab = new Label { Text = label, Bounds = new Point(30, y - 20), Size = new Size(360, 18), ForeColor = Color.FromArgb(160, 160, 170) };
            var t = new TextBox { Bounds = new Point(30, y), Size = new Size(360, 34), UseSystemPasswordChar = true, BackColor = Color.FromArgb(44, 44, 48), ForeColor = Color.White, BorderStyle = BorderStyle.FixedSingle };
            Controls.Add(lab);
            return t;
        }

        private void Ok_Click(object sender, EventArgs e)
        {
            if (_nw.Text.Length < 4) { MessageBox.Show("新主密码至少 4 位。"); return; }
            if (_nw.Text != _nw2.Text) { MessageBox.Show("两次输入不一致。"); return; }
            if (!Vault.ChangePassword(_old.Text, _nw.Text)) { MessageBox.Show("当前主密码错误，修改失败。", "错误", MessageBoxButtons.OK, MessageBoxIcon.Error); return; }
            DialogResult = DialogResult.OK;
            Close();
        }
    }
}
