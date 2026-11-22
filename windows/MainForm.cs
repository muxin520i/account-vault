using System;
using System.Collections.Generic;
using System.Drawing;
using System.Linq;
using System.Threading;
using System.Windows.Forms;

namespace AccountVault
{
    internal class MainForm : Form
    {
        private VaultData _data;
        private readonly TextBox _search;
        private readonly ListBox _identities;
        private readonly DataGridView _grid;
        private readonly StatusStrip _status;
        private readonly ToolStripStatusLabel _statusLabel;
        private bool _privacy;
        private System.Windows.Forms.Timer _clipTimer;

        public MainForm()
        {
            Text = "账号仓库";
            Size = new Size(1060, 720);
            StartPosition = FormStartPosition.CenterScreen;
            MinimumSize = new Size(760, 520);
            BackColor = Color.FromArgb(245, 245, 248);
            Font = new Font("Microsoft YaHei UI", 10F);

            _data = Vault.ReadData();

            // ---- 顶部栏 ----
            var top = new Panel { Dock = DockStyle.Top, Height = 56, BackColor = Color.FromArgb(28, 28, 32) };
            var brand = new Label
            {
                Text = "  账号仓库", Dock = DockStyle.Left, Width = 150,
                ForeColor = Color.White, Font = new Font("Microsoft YaHei UI", 15F, FontStyle.Bold),
                TextAlign = ContentAlignment.MiddleLeft,
            };
            _search = new TextBox
            {
                Location = new Point(166, 14), Size = new Size(360, 30),
                PlaceholderText = "搜索网站 / 账号 / 备注…", BorderStyle = BorderStyle.FixedSingle,
                Font = new Font("Microsoft YaHei UI", 10F),
            };
            _search.TextChanged += (s, e) => RefreshGrid();
            var menu = new Button
            {
                Text = "设置 ▾", Location = new Point(top.Width - 96, 13), Size = new Size(84, 30),
                FlatStyle = FlatStyle.Flat, ForeColor = Color.White, BackColor = Color.FromArgb(48, 48, 54),
            };
            menu.FlatAppearance.BorderSize = 0;
            var ctx = new ContextMenuStrip();
            ctx.Items.Add("新增网站账号", null, (s, e) => EditAccount(null));
            ctx.Items.Add("管理邮箱 / 手机号", null, (s, e) => ManageIdentities());
            ctx.Items.Add("-");
            ctx.Items.Add("显示 / 隐藏号码", null, (s, e) => { _privacy = !_privacy; RefreshGrid(); });
            ctx.Items.Add("修改主密码", null, (s, e) => ChangePassword());
            ctx.Items.Add("导出加密备份", null, (s, e) => ExportBackup());
            ctx.Items.Add("导入加密备份", null, (s, e) => ImportBackup());
            ctx.Items.Add("-");
            ctx.Items.Add("立即锁定", null, (s, e) => { Vault.Lock(); DialogResult = DialogResult.OK; Close(); });
            menu.Click += (s, e) => ctx.Show(menu, new Point(0, menu.Height));
            top.Controls.Add(menu);
            top.Controls.Add(_search);
            top.Controls.Add(brand);

            // ---- 左侧身份列表 ----
            var left = new Panel { Dock = DockStyle.Left, Width = 250, BackColor = Color.White, Padding = new Padding(10) };
            var leftTitle = new Label
            {
                Text = "身份（邮箱 / 手机号）", Dock = DockStyle.Top, Height = 34,
                Font = new Font("Microsoft YaHei UI", 11F, FontStyle.Bold), ForeColor = Color.FromArgb(60, 60, 70),
            };
            var addId = new Button
            {
                Text = "+ 新增身份", Dock = DockStyle.Bottom, Height = 36,
                FlatStyle = FlatStyle.Flat, BackColor = Color.FromArgb(240, 245, 255), ForeColor = Color.FromArgb(10, 110, 230),
            };
            addId.FlatAppearance.BorderSize = 0;
            addId.Click += (s, e) => EditIdentity(null);
            _identities = new ListBox
            {
                Dock = DockStyle.Fill, BorderStyle = BorderStyle.None,
                Font = new Font("Microsoft YaHei UI", 10F), BackColor = Color.White,
            };
            _identities.DoubleClick += (s, e) => { if (_identities.SelectedItem is Identity id) EditIdentity(id); };
            left.Controls.Add(_identities);
            left.Controls.Add(leftTitle);
            left.Controls.Add(addId);

            // ---- 右侧账号表格 ----
            var right = new Panel { Dock = DockStyle.Fill, Padding = new Padding(12), BackColor = Color.FromArgb(245, 245, 248) };
            var gridTitle = new Label
            {
                Text = "网站账号（双击编辑，选中行按 Delete 删除）", Dock = DockStyle.Top, Height = 32,
                Font = new Font("Microsoft YaHei UI", 11F, FontStyle.Bold), ForeColor = Color.FromArgb(60, 60, 70),
            };
            _grid = new DataGridView
            {
                Dock = DockStyle.Fill, AllowUserToAddRows = false, ReadOnly = true,
                AutoSizeColumnsMode = DataGridViewAutoSizeColumnsMode.Fill,
                SelectionMode = DataGridViewSelectionMode.FullRowSelect,
                BackgroundColor = Color.White, BorderStyle = BorderStyle.None,
                RowHeadersVisible = false, Font = new Font("Microsoft YaHei UI", 10F),
            };
            _grid.Columns.Add(new DataGridViewTextBoxColumn { Name = "name", HeaderText = "网站 / 服务" });
            _grid.Columns.Add(new DataGridViewTextBoxColumn { Name = "id", HeaderText = "关联身份" });
            _grid.Columns.Add(new DataGridViewTextBoxColumn { Name = "cat", HeaderText = "分类", FillWeight = 60 });
            _grid.Columns.Add(new DataGridViewTextBoxColumn { Name = "domain", HeaderText = "域名" });
            _grid.CellDoubleClick += (s, e) => { if (e.RowIndex >= 0) EditAccount(_data.accounts[e.RowIndex]); };
            _grid.KeyDown += (s, e) =>
            {
                if (e.KeyCode == Keys.Delete && _grid.CurrentRow != null)
                {
                    var acc = _data.accounts[_grid.CurrentRow.Index];
                    if (MessageBox.Show($"删除「{acc.name}」？", "确认", MessageBoxButtons.YesNo, MessageBoxIcon.Question) == DialogResult.Yes)
                    { _data.accounts.Remove(acc); Save(); }
                }
            };
            var addAcc = new Button
            {
                Text = "+ 新增网站账号", Dock = DockStyle.Bottom, Height = 40,
                FlatStyle = FlatStyle.Flat, BackColor = Color.FromArgb(10, 132, 255), ForeColor = Color.White,
                Font = new Font("Microsoft YaHei UI", 11F, FontStyle.Bold),
            };
            addAcc.FlatAppearance.BorderSize = 0;
            addAcc.Click += (s, e) => EditAccount(null);
            right.Controls.Add(_grid);
            right.Controls.Add(gridTitle);
            right.Controls.Add(addAcc);

            _status = new StatusStrip();
            _statusLabel = new ToolStripStatusLabel { Text = "已加密保存" };
            _status.Items.Add(_statusLabel);
            Controls.Add(_status);
            Controls.Add(right);
            Controls.Add(left);
            Controls.Add(top);

            _clipTimer = new System.Windows.Forms.Timer { Interval = 30_000 };
            _clipTimer.Tick += (s, e) => { Clipboard.Clear(); _clipTimer.Stop(); _statusLabel.Text = "剪贴板已自动清空"; };

            FormClosing += (s, e) => { Vault.Lock(); };
            RefreshAll();
        }

        private void Save()
        {
            Vault.Save(_data);
            _statusLabel.Text = "已加密保存 · " + DateTime.Now.ToString("HH:mm:ss");
        }

        private string DisplayId(Identity id)
        {
            var v = id?.value ?? "";
            if (!_privacy) return v;
            if (id?.type == "phone")
            {
                var d = new string(v.Where(char.IsDigit).ToArray());
                return d.Length > 4 ? "••••" + d.Substring(d.Length - 4) : "••••";
            }
            var at = v.IndexOf('@');
            return at > 0 ? (v.Substring(0, Math.Min(2, at)) + "•••@" + v.Substring(at + 1)) : "••••";
        }

        private void RefreshAll() { RefreshIdentities(); RefreshGrid(); }

        private void RefreshIdentities()
        {
            _identities.Items.Clear();
            foreach (var id in _data.identities) _identities.Items.Add(id);
            // 自定义显示
            _identities.DrawMode = DrawMode.OwnerDrawFixed;
            _identities.DrawItem += (s, e) =>
            {
                if (e.Index < 0) return;
                var id = (Identity)_identities.Items[e.Index];
                e.DrawBackground();
                using var br = new SolidBrush(e.State.HasFlag(DrawItemState.Selected) ? Color.White : Color.FromArgb(40, 40, 48));
                e.Graphics.DrawString($"{DisplayId(id)}   {id.label}", e.Font, br, e.Bounds.X + 4, e.Bounds.Y + 2);
            };
        }

        private void RefreshGrid()
        {
            _grid.Rows.Clear();
            var q = _search.Text.Trim().ToLowerInvariant();
            foreach (var acc in _data.accounts)
            {
                if (q.Length > 0 &&
                    !(acc.name ?? "").ToLowerInvariant().Contains(q) &&
                    !(acc.domain ?? "").ToLowerInvariant().Contains(q) &&
                    !(acc.notes ?? "").ToLowerInvariant().Contains(q)) continue;
                var ids = string.Join("、", acc.identityIds
                    .Select(iid => _data.identities.FirstOrDefault(x => x.id == iid))
                    .Where(x => x != null).Select(DisplayId));
                _grid.Rows.Add(acc.name, ids, acc.category, acc.domain);
            }
        }

        private void EditIdentity(Identity existing)
        {
            using var f = new IdentityForm(existing);
            if (f.ShowDialog() != DialogResult.OK) return;
            if (existing == null) _data.identities.Add(f.Result);
            else { var i = _data.identities.IndexOf(existing); _data.identities[i] = f.Result; }
            Save(); RefreshAll();
        }

        private void ManageIdentities()
        {
            MessageBox.Show("在左侧列表中双击身份可编辑；点「新增身份」添加。", "管理身份", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }

        private void EditAccount(Account existing)
        {
            using var f = new AccountForm(existing, _data.identities);
            if (f.ShowDialog() != DialogResult.OK) return;
            if (existing == null) _data.accounts.Add(f.Result);
            else { var i = _data.accounts.IndexOf(existing); _data.accounts[i] = f.Result; }
            Save(); RefreshGrid();
        }

        private void ChangePassword()
        {
            using var f = new ChangePasswordForm();
            if (f.ShowDialog() == DialogResult.OK)MessageBox.Show("主密码已修改。", "账号仓库", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }

        private void ExportBackup()
        {
            var sfd = new SaveFileDialog { FileName = "账号仓库-加密备份.avbackup", Filter = "加密备份 (*.avbackup)|*.avbackup" };
            if (sfd.ShowDialog() != DialogResult.OK) return;
            var text = Vault.ExportEncrypted();
            if (text != null) { System.IO.File.WriteAllText(sfd.FileName, text); MessageBox.Show("加密备份已导出。", "账号仓库"); }
        }

        private void ImportBackup()
        {
            var ofd = new OpenFileDialog { Filter = "加密备份 (*.avbackup)|*.avbackup|所有文件 (*.*)|*.*" };
            if (ofd.ShowDialog() != DialogResult.OK) return;
            try
            {
                if (Vault.ImportEncrypted(System.IO.File.ReadAllText(ofd.FileName)))
                { _data = Vault.ReadData(); RefreshAll(); MessageBox.Show("加密备份已导入。", "账号仓库"); }
                else MessageBox.Show("这不是有效的账号仓库加密备份，或主密码不匹配。", "导入失败", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
            catch (Exception ex) { MessageBox.Show("导入失败：" + ex.Message, "错误", MessageBoxButtons.OK, MessageBoxIcon.Error); }
        }

        private void CopyToClipboard(string text)
        {
            if (string.IsNullOrEmpty(text)) return;
            Clipboard.SetText(text);
            _clipTimer.Stop(); _clipTimer.Start();
            _statusLabel.Text = "已复制 · 30 秒后自动清空剪贴板";
        }
    }
}
