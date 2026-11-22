using System;
using System.Drawing;
using System.Windows.Forms;

namespace AccountVault
{
    internal class IdentityForm : Form
    {
        public Identity Result { get; private set; }
        private readonly Identity _src;
        private readonly TextBox _value, _label, _notes;
        private readonly ComboBox _type;

        public IdentityForm(Identity src)
        {
            _src = src;
            Text = src == null ? "新增身份" : "编辑身份";
            Size = new Size(480, 380);
            StartPosition = FormStartPosition.CenterParent;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false; MinimizeBox = false;
            BackColor = Color.FromArgb(28, 28, 32);
            ForeColor = Color.FromArgb(235, 235, 240);
            Font = new Font("Microsoft YaHei UI", 10F);

            _type = new ComboBox { Location = new Point(24, 30), Size = new Size(410, 30), DropDownStyle = ComboBoxStyle.DropDownList, BackColor = Color.FromArgb(44, 44, 48), ForeColor = Color.White, FlatStyle = FlatStyle.Flat };
            _type.Items.AddRange(new object[] { "email", "phone" });
            _type.SelectedIndex = src?.type == "phone" ? 1 : 0;

            _value = Field("邮箱地址 / 手机号", 88, src?.value ?? "");
            _label = Field("备注名称（如：主力邮箱）", 150, src?.label ?? "");
            _notes = new TextBox
            {
                Location = new Point(24, 212), Size = new Size(410, 60), Multiline = true,
                BackColor = Color.FromArgb(44, 44, 48), ForeColor = Color.White, BorderStyle = BorderStyle.FixedSingle,
                Text = src?.notes ?? "",
            };

            var ok = new Button { Text = "保存", Location = new Point(254, 295), Size = new Size(90, 34), DialogResult = DialogResult.OK, BackColor = Color.FromArgb(10, 132, 255), ForeColor = Color.White, FlatStyle = FlatStyle.Flat };
            ok.FlatAppearance.BorderSize = 0;
            var cancel = new Button { Text = "取消", Location = new Point(354, 295), Size = new Size(80, 34), DialogResult = DialogResult.Cancel, BackColor = Color.FromArgb(60, 60, 66), ForeColor = Color.White, FlatStyle = FlatStyle.Flat };
            cancel.FlatAppearance.BorderSize = 0;
            ok.Click += Ok_Click;
            Controls.Add(cancel); Controls.Add(ok); Controls.Add(_notes); Controls.Add(_value); Controls.Add(_label); Controls.Add(_type);
            AcceptButton = ok; CancelButton = cancel;
        }

        private TextBox Field(string label, int y, string text)
        {
            var lab = new Label { Text = label, Location = new Point(24, y - 20), Size = new Size(410, 18), ForeColor = Color.FromArgb(160, 160, 170) };
            var t = new TextBox { Location = new Point(24, y), Size = new Size(410, 32), Text = text, BackColor = Color.FromArgb(44, 44, 48), ForeColor = Color.White, BorderStyle = BorderStyle.FixedSingle };
            Controls.Add(lab);
            return t;
        }

        private void Ok_Click(object sender, EventArgs e)
        {
            if (string.IsNullOrWhiteSpace(_value.Text)) { MessageBox.Show("请填写邮箱或手机号。"); DialogResult = DialogResult.None; return; }
            var id = _src ?? new Identity();
            id.type = _type.SelectedIndex == 1 ? "phone" : "email";
            id.value = _value.Text.Trim();
            id.label = _label.Text.Trim();
            id.notes = _notes.Text;
            Result = id;
        }
    }
}
