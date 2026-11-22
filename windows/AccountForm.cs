using System;
using System.Collections.Generic;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;

namespace AccountVault
{
    internal class AccountForm : Form
    {
        public Account Result { get; private set; }
        private readonly Account _src;
        private readonly TextBox _name, _domain, _notes;
        private readonly ComboBox _cat;
        private readonly CheckedListBox _ids;
        private readonly List<Identity> _identities;
        private readonly Action<string> _copier;

        public AccountForm(Account src, List<Identity> identities)
        {
            _src = src;
            _identities = identities;
            Text = src == null ? "新增网站账号" : "编辑网站账号";
            Size = new Size(520, 560);
            StartPosition = FormStartPosition.CenterParent;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false; MinimizeBox = false;
            BackColor = Color.FromArgb(28, 28, 32);
            ForeColor = Color.FromArgb(235, 235, 240);
            Font = new Font("Microsoft YaHei UI", 10F);

            _name = Field("网站 / 服务名称", 20, src?.name ?? "");
            _domain = Field("域名（如 github.com）", 82, src?.domain ?? "");
            _cat = new ComboBox { Location = new Point(20, 152), Size = new Size(460, 30), DropDownStyle = ComboBoxStyle.DropDown, BackColor = Color.FromArgb(44, 44, 48), ForeColor = Color.White, FlatStyle = FlatStyle.Flat };
            _cat.Items.AddRange(Presets.Categories);
            _cat.Text = src?.category ?? "其他";

            var idLabel = new Label { Text = "关联身份（邮箱 / 手机号）", Location = new Point(20, 192), Size = new Size(460, 22), ForeColor = Color.FromArgb(160, 160, 170) };
            _ids = new CheckedListBox
            {
                Location = new Point(20, 218), Size = new Size(460, 120),
                BackColor = Color.FromArgb(44, 44, 48), ForeColor = Color.White,
                BorderStyle = BorderStyle.FixedSingle,
            };
            foreach (var id in _identities) _ids.Items.Add(id, src != null && src.identityIds.Contains(id.id));

            _notes = new TextBox
            {
                Location = new Point(20, 356), Size = new Size(460, 70), Multiline = true,
                BackColor = Color.FromArgb(44, 44, 48), ForeColor = Color.White,
                BorderStyle = BorderStyle.FixedSingle, ScrollBars = ScrollBars.Vertical,
                Text = src?.notes ?? "", PlaceholderText = "备注（可写账号提示、二次验证位置等，不要写明文密码）",
            };

            var ok = new Button { Text = "保存", Location = new Point(290, 470), Size = new Size(100, 36), DialogResult = DialogResult.OK, BackColor = Color.FromArgb(10, 132, 255), ForeColor = Color.White, FlatStyle = FlatStyle.Flat };
            ok.FlatAppearance.BorderSize = 0;
            var cancel = new Button { Text = "取消", Location = new Point(400, 470), Size = new Size(80, 36), DialogResult = DialogResult.Cancel, BackColor = Color.FromArgb(60, 60, 66), ForeColor = Color.White, FlatStyle = FlatStyle.Flat };
            cancel.FlatAppearance.BorderSize = 0;
            ok.Click += Ok_Click;

            Controls.Add(cancel); Controls.Add(ok); Controls.Add(_notes); Controls.Add(_ids); Controls.Add(idLabel);
            Controls.Add(_cat); Controls.Add(_domain); Controls.Add(_name);
            AcceptButton = ok; CancelButton = cancel;
        }

        private TextBox Field(string label, int y, string text)
        {
            var lab = new Label { Text = label, Location = new Point(20, y - 20), Size = new Size(460, 18), ForeColor = Color.FromArgb(160, 160, 170) };
            var t = new TextBox { Location = new Point(20, y), Size = new Size(460, 32), Text = text, BackColor = Color.FromArgb(44, 44, 48), ForeColor = Color.White, BorderStyle = BorderStyle.FixedSingle };
            Controls.Add(lab);
            return t;
        }

        private void Ok_Click(object sender, EventArgs e)
        {
            if (string.IsNullOrWhiteSpace(_name.Text)) { MessageBox.Show("请填写网站名称。"); DialogResult = DialogResult.None; return; }
            var selected = new List<string>();
            for (int i = 0; i < _ids.Items.Count; i++) if (_ids.GetItemChecked(i)) selected.Add(((Identity)_ids.Items[i]).id);
            var acc = _src ?? new Account();
            acc.name = _name.Text.Trim();
            acc.domain = _domain.Text.Trim();
            acc.category = string.IsNullOrWhiteSpace(_cat.Text) ? "其他" : _cat.Text.Trim();
            acc.notes = _notes.Text;
            acc.identityIds = selected;
            Result = acc;
        }
    }
}
