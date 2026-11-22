using System;
using System.Collections.Generic;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Threading;

namespace AccountVault
{
    /// <summary>主界面：深色三栏布局（身份 / 账号卡片 / 详情），对齐原版视觉</summary>
    internal class MainWindow : Window
    {
        private VaultData _data;
        private bool _privacy;
        private readonly TextBox _search;
        private readonly StackPanel _identityList;
        private readonly WrapPanel _accountGrid;
        private readonly TextBlock _status;
        private DispatcherTimer _clipTimer;

        public MainWindow(VaultData data)
        {
            _data = data ?? new VaultData();
            Title = "账号仓库";
            Width = 1120; Height = 760;
            MinWidth = 820; MinHeight = 520;
            WindowStartupLocation = WindowStartupLocation.CenterScreen;
            Background = Theme.Bg;
            Foreground = Theme.Label;
            FontFamily = Theme.Font;
            WindowStyle = WindowStyle.None;
            AllowsTransparency = true;

            var shadow = new System.Windows.Media.Effects.DropShadowEffect
            { BlurRadius = 30, Opacity = 0.5, ShadowDepth = 6, Color = Colors.Black };

            // ===== 根布局：标题栏 + 内容 + 状态栏 =====
            var root = new Border
            {
                Background = Theme.Bg, CornerRadius = new CornerRadius(16),
                BorderBrush = new SolidColorBrush(Color.FromArgb(30, 255, 255, 255)),
                BorderThickness = new Thickness(1), Effect = shadow,
            };
            var rootGrid = new Grid();
            rootGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(58) });
            rootGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto }); // sep
            rootGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            rootGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(34) });
            root.Child = rootGrid;

            // ---- 标题栏 ----
            var titleBar = new Grid { Background = new SolidColorBrush(Color.FromRgb(16, 16, 18)) };
            titleBar.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(230) });
            titleBar.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            titleBar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            var brand = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(20, 0, 0, 0) };
            brand.Children.Add(new TextBlock { Text = "🔐", FontSize = 18, Margin = new Thickness(0, 0, 8, 0), VerticalAlignment = VerticalAlignment.Center });
            brand.Children.Add(Theme.Text("账号仓库", 16, Theme.C_Label, true));
            Grid.SetColumn(brand, 0);
            titleBar.Children.Add(brand);

            _search = Theme.Input("搜索网站 / 账号 / 备注…");
            _search.Height = 34;
            _search.Width = 380;
            _search.HorizontalAlignment = HorizontalAlignment.Left;
            _search.Margin = new Thickness(8, 0, 0, 0);
            _search.TextChanged += (s, e) => RefreshAccounts();
            Grid.SetColumn(_search, 1);
            titleBar.Children.Add(_search);

            var topBtns = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Right, Margin = new Thickness(0, 0, 16, 0) };
            var settingsBtn = Theme.GhostButton("设置 ▾", 32);
            settingsBtn.Click += Settings_Click;
            topBtns.Children.Add(settingsBtn);
            var minBtn = MakeIconButton("─");
            minBtn.Click += (s, e) => WindowState = WindowState.Minimized;
            topBtns.Children.Add(minBtn);
            var closeBtn = MakeIconButton("✕");
            closeBtn.Click += (s, e) => Close();
            topBtns.Children.Add(closeBtn);
            Grid.SetColumn(topBtns, 2);
            titleBar.Children.Add(topBtns);
            Grid.SetRow(titleBar, 0);
            rootGrid.Children.Add(titleBar);

            var sep = new Border { Height = 1, Background = new SolidColorBrush(Color.FromArgb(26, 255, 255, 255)) };
            Grid.SetRow(sep, 1);
            rootGrid.Children.Add(sep);

            // ---- 内容三栏 ----
            var content = new Grid();
            content.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(264) });
            content.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            Grid.SetRow(content, 2);
            rootGrid.Children.Add(content);

            // 左栏：身份
            var left = new Grid { Background = new SolidColorBrush(Color.FromRgb(12, 12, 14)) };
            left.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            left.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            left.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            var leftTitle = Theme.Text("身份（邮箱 / 手机号）", 13, Theme.C_Secondary, true);
            leftTitle.Margin = new Thickness(18, 16, 0, 10);
            Grid.SetRow(leftTitle, 0);
            left.Children.Add(leftTitle);

            var idScroll = new ScrollViewer { VerticalScrollBarVisibility = ScrollBarVisibility.Auto, HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled };
            _identityList = new StackPanel { Margin = new Thickness(12, 0, 12, 0) };
            idScroll.Content = _identityList;
            Grid.SetRow(idScroll, 1);
            left.Children.Add(idScroll);

            var addId = Theme.Pill("＋ 新增身份", Theme.C_Card2, Theme.C_Label, 38, 13);
            addId.Margin = new Thickness(12, 10, 12, 14);
            addId.Click += (s, e) => EditIdentity(null);
            Grid.SetRow(addId, 2);
            left.Children.Add(addId);
            Grid.SetColumn(left, 0);
            content.Children.Add(left);

            // 右栏：账号卡片网格
            var right = new Grid { Background = Theme.Bg };
            right.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            right.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            right.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            var rightHead = new Grid { Margin = new Thickness(22, 16, 22, 6) };
            rightHead.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            rightHead.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            var accTitle = Theme.Text("网站账号", 15, Theme.C_Label, true);
            Grid.SetColumn(accTitle, 0);
            rightHead.Children.Add(accTitle);
            var addAcc = Theme.Pill("＋ 新增账号", Theme.C_Accent, Colors.White, 34, 12.5);
            addAcc.Click += (s, e) => EditAccount(null);
            Grid.SetColumn(addAcc, 1);
            rightHead.Children.Add(addAcc);
            Grid.SetRow(rightHead, 0);
            right.Children.Add(rightHead);

            var accScroll = new ScrollViewer { VerticalScrollBarVisibility = ScrollBarVisibility.Auto, HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled, Padding = new Thickness(14, 6, 14, 10) };
            _accountGrid = new WrapPanel { ItemWidth = 232, ItemHeight = 128 };
            accScroll.Content = _accountGrid;
            Grid.SetRow(accScroll, 1);
            right.Children.Add(accScroll);

            var hint = Theme.Text("双击卡片编辑 · 选中后按 Delete 删除", 11.5, Theme.C_Tertiary, false, TextAlignment.Center);
            hint.Margin = new Thickness(0, 4, 0, 10);
            Grid.SetRow(hint, 2);
            right.Children.Add(hint);
            Grid.SetColumn(right, 1);
            content.Children.Add(right);

            // ---- 状态栏 ----
            var statusBar = new Border { Background = new SolidColorBrush(Color.FromRgb(16, 16, 18)), Padding = new Thickness(18, 0, 0, 0) };
            _status = Theme.Text("🔒 已加密保存", 11.5, Theme.C_Secondary);
            statusBar.Child = _status;
            Grid.SetRow(statusBar, 3);
            rootGrid.Children.Add(statusBar);

            Content = root;
            MouseLeftButtonDown += (s, e) => { if (e.ButtonState == MouseButtonState.Pressed) DragMove(); };
            KeyDown += MainWindow_KeyDown;

            _clipTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(30) };
            _clipTimer.Tick += (s, e) => { Clipboard.Clear(); _clipTimer.Stop(); _status.Text = "🧹 剪贴板已自动清空"; };

            RefreshAll();
        }

        private static Button MakeIconButton(string glyph)
        {
            var b = new Button
            {
                Content = glyph, Width = 30, Height = 30, FontSize = 14,
                Foreground = Theme.Secondary, Background = Brushes.Transparent,
                BorderThickness = new Thickness(0), Cursor = Cursors.Hand,
                Margin = new Thickness(2, 0, 0, 0),
            };
            return b;
        }

        private void MainWindow_KeyDown(object sender, KeyEventArgs e)
        {
            if (e.Key == Key.Delete && Keyboard.FocusedElement is DependencyObject obj)
            {
                // 由卡片自身处理删除；此处占位
            }
        }

        private void Settings_Click(object sender, RoutedEventArgs e)
        {
            var menu = new ContextMenu { Background = new SolidColorBrush(Color.FromRgb(44, 44, 46)), Foreground = Theme.Label, FontFamily = Theme.Font };
            void Add(string text, Action act, bool danger = false)
            {
                var mi = new MenuItem { Header = text, Foreground = danger ? Theme.Red : Theme.Label, FontSize = 13 };
                mi.Click += (s2, e2) => act();
                menu.Items.Add(mi);
            }
            Add("显示 / 隐藏 号码", () => { _privacy = !_privacy; RefreshAll(); });
            Add("修改主密码", ChangePassword);
            Add("导出加密备份", ExportBackup);
            Add("导入加密备份", ImportBackup);
            menu.Items.Add(new Separator());
            Add("立即锁定并退出", () => { Vault.Lock(); Close(); }, danger: true);
            menu.PlacementTarget = sender as UIElement;
            menu.IsOpen = true;
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

        private void RefreshAll() { RefreshIdentities(); RefreshAccounts(); }

        private void RefreshIdentities()
        {
            _identityList.Children.Clear();
            foreach (var id in _data.identities)
            {
                var card = new Border
                {
                    Background = new SolidColorBrush(Color.FromRgb(28, 28, 30)),
                    CornerRadius = new CornerRadius(12),
                    Padding = new Thickness(12, 10, 12, 10),
                    Margin = new Thickness(0, 0, 0, 8),
                    Cursor = Cursors.Hand,
                };
                var gp = new Grid();
                gp.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
                gp.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                var dotColor = Theme.Parse(id.color, Theme.C_Accent);
                var dot = new Border { Width = 34, Height = 34, CornerRadius = new CornerRadius(10), Background = new SolidColorBrush(dotColor), Margin = new Thickness(0, 0, 10, 0) };
                dot.Child = new TextBlock { Text = (id.type == "phone" ? "📱" : "✉"), FontSize = 15, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
                Grid.SetColumn(dot, 0);
                gp.Children.Add(dot);
                var txt = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
                txt.Children.Add(Theme.Text(DisplayId(id), 13, Theme.C_Label, true));
                if (!string.IsNullOrEmpty(id.label)) txt.Children.Add(Theme.Text(id.label, 11, Theme.C_Secondary));
                Grid.SetColumn(txt, 1);
                gp.Children.Add(txt);
                card.Child = gp;
                card.MouseLeftButtonUp += (s, e) => EditIdentity(id);
                _identityList.Children.Add(card);
            }
            if (_data.identities.Count == 0)
            {
                var emptyTip = Theme.Text("还没有身份\n点下方按钮添加邮箱或手机号", 12.5, Theme.C_Tertiary, false, TextAlignment.Center);
                emptyTip.Margin = new Thickness(0, 30, 0, 0);
                _identityList.Children.Add(emptyTip);
            }
        }

        private void RefreshAccounts()
        {
            _accountGrid.Children.Clear();
            var q = (_search.Text ?? "").Trim();
            if (q == (string)_search.Tag) q = "";
            q = q.ToLowerInvariant();
            foreach (var acc in _data.accounts)
            {
                if (q.Length > 0 &&
                    !(acc.name ?? "").ToLowerInvariant().Contains(q) &&
                    !(acc.domain ?? "").ToLowerInvariant().Contains(q) &&
                    !(acc.notes ?? "").ToLowerInvariant().Contains(q) &&
                    !(acc.category ?? "").ToLowerInvariant().Contains(q)) continue;

                _accountGrid.Children.Add(BuildAccountCard(acc));
            }
            if (_accountGrid.Children.Count == 0)
            {
                var empty = new Border { Width = 460, Height = 120, HorizontalAlignment = HorizontalAlignment.Center };
                empty.Child = Theme.Text(q.Length > 0 ? "没有匹配的账号" : "还没有账号\n点右上角「新增账号」开始", 13, Theme.C_Tertiary, false, TextAlignment.Center);
                _accountGrid.Children.Add(empty);
            }
        }

        private Border BuildAccountCard(Account acc)
        {
            var card = new Border
            {
                Background = new SolidColorBrush(Color.FromRgb(28, 28, 30)),
                CornerRadius = new CornerRadius(14),
                Margin = new Thickness(6),
                Padding = new Thickness(14),
                Cursor = Cursors.Hand,
            };
            var g = new Grid();
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            g.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

            // 图标字母块
            var initial = string.IsNullOrEmpty(acc.name) ? "?" : acc.name.Trim().Substring(0, 1).ToUpperInvariant();
            var hue = Math.Abs((acc.name ?? "").GetHashCode()) % 360;
            var iconBg = new SolidColorBrush(HslToRgb(hue, 0.55, 0.5));
            var icon = new Border { Width = 40, Height = 40, CornerRadius = new CornerRadius(11), Background = iconBg, HorizontalAlignment = HorizontalAlignment.Left };
            icon.Child = new TextBlock { Text = initial, FontSize = 19, FontWeight = FontWeights.Bold, Foreground = Brushes.White, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
            Grid.SetRow(icon, 0);
            g.Children.Add(icon);

            var mid = new StackPanel { Margin = new Thickness(0, 10, 0, 0), VerticalAlignment = VerticalAlignment.Center };
            mid.Children.Add(Theme.Text(acc.name ?? "(未命名)", 14, Theme.C_Label, true));
            var domainTxt = Theme.Text(acc.domain ?? "", 11.5, Theme.C_Secondary);
            domainTxt.Margin = new Thickness(0, 2, 0, 0);
            mid.Children.Add(domainTxt);
            var ids = string.Join("、", acc.identityIds
                .Select(iid => _data.identities.FirstOrDefault(x => x.id == iid))
                .Where(x => x != null).Select(DisplayId));
            if (!string.IsNullOrEmpty(ids)) { var idTxt = Theme.Text(ids, 10.5, Theme.C_Tertiary); idTxt.Margin = new Thickness(0, 2, 0, 0); mid.Children.Add(idTxt); }
            Grid.SetRow(mid, 1);
            g.Children.Add(mid);

            var tag = new Border
            {
                Background = new SolidColorBrush(Color.FromArgb(40, 10, 132, 255)),
                CornerRadius = new CornerRadius(6), Padding = new Thickness(7, 3, 7, 3),
                HorizontalAlignment = HorizontalAlignment.Left,
            };
            tag.Child = Theme.Text(acc.category ?? "其他", 10.5, Theme.C_Accent);
            Grid.SetRow(tag, 2);
            g.Children.Add(tag);

            card.Child = g;
            card.MouseLeftButtonUp += (s, e) => EditAccount(acc);
            card.ContextMenu = new ContextMenu();
            var del = new MenuItem { Header = "删除", Foreground = Theme.Red };
            del.Click += (s, e) =>
            {
                if (MessageBox.Show(this, $"删除「{acc.name}」？", "确认", MessageBoxButton.YesNo, MessageBoxImage.Question) == MessageBoxResult.Yes)
                { _data.accounts.Remove(acc); Save(); }
            };
            card.ContextMenu.Items.Add(del);
            return card;
        }

        private static Color HslToRgb(double h, double s, double l)
        {
            double c = (1 - Math.Abs(2 * l - 1)) * s;
            double x = c * (1 - Math.Abs((h / 60.0) % 2 - 1));
            double m = l - c / 2;
            double r = 0, g = 0, b = 0;
            if (h < 60) { r = c; g = x; }
            else if (h < 120) { r = x; g = c; }
            else if (h < 180) { g = c; b = x; }
            else if (h < 240) { g = x; b = c; }
            else if (h < 300) { r = x; b = c; }
            else { r = c; b = x; }
            return Color.FromRgb((byte)((r + m) * 255), (byte)((g + m) * 255), (byte)((b + m) * 255));
        }

        private void Save()
        {
            Vault.Save(_data);
            _status.Text = "🔒 已加密保存 · " + DateTime.Now.ToString("HH:mm:ss");
        }

        private void EditIdentity(Identity existing)
        {
            var f = new IdentityDialog(existing);
            if (f.ShowDialog() != true) return;
            if (existing == null) _data.identities.Add(f.Result);
            else { int i = _data.identities.IndexOf(existing); if (i >= 0) _data.identities[i] = f.Result; }
            Save(); RefreshAll();
        }

        private void EditAccount(Account existing)
        {
            var f = new AccountDialog(existing, _data.identities);
            if (f.ShowDialog() != true) return;
            if (existing == null) _data.accounts.Add(f.Result);
            else { int i = _data.accounts.IndexOf(existing); if (i >= 0) _data.accounts[i] = f.Result; }
            Save(); RefreshAccounts();
        }

        private void ChangePassword()
        {
            var f = new ChangePasswordDialog();
            if (f.ShowDialog() == true) MessageBox.Show(this, "主密码已修改。", "账号仓库", MessageBoxButton.OK, MessageBoxImage.Information);
        }

        private void ExportBackup()
        {
            var dlg = new Microsoft.Win32.SaveFileDialog { FileName = "账号仓库-加密备份.avbackup", Filter = "加密备份 (*.avbackup)|*.avbackup" };
            if (dlg.ShowDialog() != true) return;
            var text = Vault.ExportEncrypted();
            if (text != null) { System.IO.File.WriteAllText(dlg.FileName, text); MessageBox.Show(this, "加密备份已导出。", "账号仓库"); }
        }

        private void ImportBackup()
        {
            var dlg = new Microsoft.Win32.OpenFileDialog { Filter = "加密备份 (*.avbackup)|*.avbackup|所有文件 (*.*)|*.*" };
            if (dlg.ShowDialog() != true) return;
            try
            {
                if (Vault.ImportEncrypted(System.IO.File.ReadAllText(dlg.FileName)))
                { _data = Vault.ReadData(); RefreshAll(); MessageBox.Show(this, "加密备份已导入。", "账号仓库"); }
                else MessageBox.Show(this, "这不是有效的账号仓库加密备份，或主密码不匹配。", "导入失败", MessageBoxButton.OK, MessageBoxImage.Error);
            }
            catch (Exception ex) { MessageBox.Show(this, "导入失败：" + ex.Message, "错误", MessageBoxButton.OK, MessageBoxImage.Error); }
        }
    }
}
