using System;
using System.Collections.Generic;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;

namespace AccountVault
{
    /// <summary>深色对话框基类：圆角 + 阴影 + 拖动 + 确定/取消</summary>
    internal class DarkDialog : Window
    {
        protected readonly StackPanel Body = new StackPanel();
        protected readonly Button OkBtn;
        protected readonly Button CancelBtn;

        public DarkDialog(string title, double width = 460, double height = 420)
        {
            Title = title;
            Width = width; Height = height;
            SizeToContent = SizeToContent.Height;
            ResizeMode = ResizeMode.NoResize;
            WindowStartupLocation = WindowStartupLocation.CenterOwner;
            Background = Theme.Bg;
            Foreground = Theme.Label;
            FontFamily = Theme.Font;
            WindowStyle = WindowStyle.None;
            AllowsTransparency = true;

            var shell = new Border
            {
                Background = Theme.Card, CornerRadius = new CornerRadius(18),
                Padding = new Thickness(26, 24, 26, 22),
                Effect = new System.Windows.Media.Effects.DropShadowEffect { BlurRadius = 36, Opacity = 0.6, ShadowDepth = 8, Color = Colors.Black },
            };
            var g = new Grid();
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            g.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

            g.Children.Add(Theme.Text(title, 19, Theme.C_Label, true));
            var bodyScroll = new ScrollViewer { Content = Body, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, Margin = new Thickness(0, 18, 0, 0), MaxHeight = 520 };
            Grid.SetRow(bodyScroll, 1);
            g.Children.Add(bodyScroll);

            var actions = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right, Margin = new Thickness(0, 18, 0, 0) };
            CancelBtn = Theme.GhostButton("取消", 38); CancelBtn.Width = 88; CancelBtn.Margin = new Thickness(0, 0, 10, 0);
            OkBtn = Theme.Pill("保存", Theme.C_Accent, Colors.White, 38, 13); OkBtn.Width = 88;
            CancelBtn.Click += (s, e) => { DialogResult = false; Close(); };
            actions.Children.Add(CancelBtn); actions.Children.Add(OkBtn);
            Grid.SetRow(actions, 2);
            g.Children.Add(actions);

            shell.Child = g;
            Content = shell;
            MouseLeftButtonDown += (s, e) => { if (e.ButtonState == MouseButtonState.Pressed) DragMove(); };
        }

        protected static void Field(StackPanel host, string label, Control input)
        {
            host.Children.Add(Theme.Text(label, 12, Theme.C_Secondary, true));
            input.Margin = new Thickness(0, 6, 0, 14);
            host.Children.Add(input);
        }
    }

    internal class IdentityDialog : DarkDialog
    {
        public Identity Result { get; private set; }
        private readonly TextBox _value, _label, _notes;
        private readonly ComboBox _type;
        private readonly bool _isNew;

        public IdentityDialog(Identity existing) : base(existing == null ? "新增身份" : "编辑身份", 440)
        {
            _isNew = existing == null;
            Result = existing ?? new Identity();

            _type = new ComboBox { Height = 42, FontSize = 14, FontFamily = Theme.Font, Foreground = Theme.Label, Background = new SolidColorBrush(Color.FromRgb(44, 44, 46)), BorderBrush = new SolidColorBrush(Color.FromArgb(30, 255, 255, 255)), Padding = new Thickness(10, 8, 10, 8) };
            _type.Items.Add("邮箱"); _type.Items.Add("手机号");
            _type.SelectedIndex = Result.type == "phone" ? 1 : 0;

            _value = Theme.Input("邮箱地址或手机号");
            _value.Text = Result.value ?? "";
            _label = Theme.Input("备注名称（如：工作、个人）");
            _label.Text = Result.label ?? "";
            _notes = Theme.Input("备注");
            _notes.Text = Result.notes ?? "";

            Field(Body, "类型", _type);
            Field(Body, "邮箱 / 手机号", _value);
            Field(Body, "备注名称", _label);
            Field(Body, "备注", _notes);

            OkBtn.Click += OkBtn_Click;
            Owner = Application.Current?.MainWindow;
        }

        private void OkBtn_Click(object sender, RoutedEventArgs e)
        {
            var v = Theme.RealText(_value).Trim();
            if (v.Length == 0) { MessageBox.Show(this, "请填写邮箱或手机号。", "账号仓库"); return; }
            Result.type = _type.SelectedIndex == 1 ? "phone" : "email";
            Result.value = v;
            Result.label = Theme.RealText(_label).Trim();
            Result.notes = Theme.RealText(_notes).Trim();
            if (string.IsNullOrEmpty(Result.color)) Result.color = "#0a84ff";
            DialogResult = true; Close();
        }
    }

    internal class AccountDialog : DarkDialog
    {
        public Account Result { get; private set; }
        private readonly TextBox _name, _domain, _notes;
        private readonly ComboBox _category;
        private readonly StackPanel _idChecks;
        private readonly List<Identity> _ids;
        private readonly bool _isNew;

        public AccountDialog(Account existing, List<Identity> identities) : base(existing == null ? "新增网站账号" : "编辑网站账号", 480)
        {
            _ids = identities ?? new List<Identity>();
            _isNew = existing == null;
            Result = existing ?? new Account();

            _name = Theme.Input("如：GitHub"); _name.Text = Result.name ?? "";
            _domain = Theme.Input("如：github.com（可选，用于图标）"); _domain.Text = Result.domain ?? "";

            _category = new ComboBox { Height = 42, FontSize = 14, FontFamily = Theme.Font, Foreground = Theme.Label, Background = new SolidColorBrush(Color.FromRgb(44, 44, 46)), BorderBrush = new SolidColorBrush(Color.FromArgb(30, 255, 255, 255)), Padding = new Thickness(10, 8, 10, 8) };
            foreach (var c in Presets.Categories) _category.Items.Add(c);
            int ci = Array.IndexOf(Presets.Categories, Result.category ?? "其他"); _category.SelectedIndex = ci >= 0 ? ci : Presets.Categories.Length - 1;

            _notes = Theme.Input("备注（可选）"); _notes.Text = Result.notes ?? "";

            Field(Body, "网站 / 服务名称", _name);
            Field(Body, "域名", _domain);
            Field(Body, "分类", _category);
            Field(Body, "备注", _notes);

            Body.Children.Add(Theme.Text("关联身份", 12, Theme.C_Secondary, true));
            _idChecks = new StackPanel { Margin = new Thickness(0, 6, 0, 8) };
            if (_ids.Count == 0)
                _idChecks.Children.Add(Theme.Text("尚未添加身份，可稍后在左侧添加。", 11.5, Theme.C_Tertiary));
            foreach (var id in _ids)
            {
                var cb = new CheckBox
                {
                    Content = $"{id.value}  {id.label}",
                    IsChecked = Result.identityIds.Contains(id.id),
                    Foreground = Theme.Label, FontSize = 12.5, FontFamily = Theme.Font,
                    Margin = new Thickness(0, 0, 0, 8), Tag = id.id,
                };
                _idChecks.Children.Add(cb);
            }
            Body.Children.Add(_idChecks);

            OkBtn.Click += OkBtn_Click;
            Owner = Application.Current?.MainWindow;
        }

        private void OkBtn_Click(object sender, RoutedEventArgs e)
        {
            var n = Theme.RealText(_name).Trim();
            if (n.Length == 0) { MessageBox.Show(this, "请填写网站或服务名称。", "账号仓库"); return; }
            Result.name = n;
            Result.domain = Theme.RealText(_domain).Trim();
            Result.category = _category.SelectedItem as string ?? "其他";
            Result.notes = Theme.RealText(_notes).Trim();
            Result.identityIds = _idChecks.Children.OfType<CheckBox>().Where(c => c.IsChecked == true).Select(c => (string)c.Tag).ToList();
            DialogResult = true; Close();
        }
    }

    internal class ChangePasswordDialog : DarkDialog
    {
        private readonly PasswordBox _old, _pw1, _pw2;
        public ChangePasswordDialog() : base("修改主密码", 420)
        {
            _old = Theme.PasswordInput();
            _pw1 = Theme.PasswordInput();
            _pw2 = Theme.PasswordInput();
            Field(Body, "当前主密码", _old);
            Field(Body, "新主密码（至少 4 位）", _pw1);
            Field(Body, "再次输入新主密码", _pw2);
            OkBtn.Content = "修改";
            OkBtn.Click += OkBtn_Click;
            Owner = Application.Current?.MainWindow;
        }

        private void OkBtn_Click(object sender, RoutedEventArgs e)
        {
            if (_pw1.Password.Length < 4) { MessageBox.Show(this, "新主密码至少 4 位。", "账号仓库"); return; }
            if (_pw1.Password != _pw2.Password) { MessageBox.Show(this, "两次输入的新主密码不一致。", "账号仓库"); return; }
            if (!Vault.ChangePassword(_old.Password ?? "", _pw1.Password))
            { MessageBox.Show(this, "当前主密码不正确。", "账号仓库", MessageBoxButton.OK, MessageBoxImage.Error); return; }
            DialogResult = true; Close();
        }
    }
}
