using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;

namespace AccountVault
{
    /// <summary>主密码解锁 / 首次设置窗口（深色卡片，对齐原版风格）</summary>
    internal class UnlockWindow : Window
    {
        public VaultData Data { get; private set; }
        private readonly bool _setup;
        private readonly PasswordBox _pw1 = new PasswordBox();
        private readonly PasswordBox _pw2 = new PasswordBox();
        private readonly TextBlock _err = Theme.Text("", 12.5, Theme.C_Red);

        public UnlockWindow()
        {
            _setup = !Vault.IsSetup;

            Title = "账号仓库";
            Width = 520; Height = _setup ? 430 : 380;
            ResizeMode = ResizeMode.NoResize;
            WindowStartupLocation = WindowStartupLocation.CenterScreen;
            Background = Theme.Bg;
            Foreground = Theme.Label;
            FontFamily = Theme.Font;
            WindowStyle = WindowStyle.None;
            AllowsTransparency = true;

            // 外层圆角容器
            var shell = new Border
            {
                Background = Theme.Card,
                CornerRadius = new CornerRadius(20),
                Padding = new Thickness(34, 30, 34, 30),
                SnapsToDevicePixels = true,
            };
            var shadow = new System.Windows.Media.Effects.DropShadowEffect
            {
                BlurRadius = 40, Opacity = 0.6, ShadowDepth = 8, Color = Colors.Black
            };
            shell.Effect = shadow;

            var stack = new StackPanel { VerticalAlignment = VerticalAlignment.Center };

            // 锁形图标（用圆形 + 符号模拟）
            var badge = new Border
            {
                Width = 62, Height = 62, CornerRadius = new CornerRadius(18),
                Background = new SolidColorBrush(Color.FromArgb(40, 10, 132, 255)),
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 0, 0, 18),
                Child = new TextBlock
                {
                    Text = "🔒", FontSize = 28, HorizontalAlignment = HorizontalAlignment.Center,
                    VerticalAlignment = VerticalAlignment.Center,
                },
            };
            stack.Children.Add(badge);

            stack.Children.Add(Theme.Text("账号仓库", 26, Theme.C_Label, true, TextAlignment.Center));
            stack.Children.Add(new Border { Height = 8 });
            stack.Children.Add(Theme.Text(
                _setup ? "设置一个主密码，用它加密保护你的全部账号数据。\n主密码不会上传，忘记后无法找回。" : "输入主密码以解锁保险库",
                13.5, Theme.C_Secondary, false, TextAlignment.Center));
            stack.Children.Add(new Border { Height = 22 });

            ApplyInputStyle(_pw1, "主密码");
            stack.Children.Add(_pw1);
            if (_setup)
            {
                stack.Children.Add(new Border { Height = 12 });
                ApplyInputStyle(_pw2, "再次输入主密码");
                stack.Children.Add(_pw2);
            }
            stack.Children.Add(new Border { Height = 10 });
            stack.Children.Add(_err);
            stack.Children.Add(new Border { Height = 14 });

            var submit = Theme.Pill(_setup ? "创建保险库" : "解锁", Theme.C_Accent, Colors.White, 44, 15);
            submit.HorizontalAlignment = HorizontalAlignment.Stretch;
            submit.Click += Submit_Click;
            stack.Children.Add(submit);

            shell.Child = stack;
            Content = shell;

            // 标题栏拖动
            MouseLeftButtonDown += (s, e) => { if (e.ButtonState == MouseButtonState.Pressed) DragMove(); };
            _pw1.Focus();
            _pw1.KeyDown += (s, e) => { if (e.Key == Key.Enter) Submit_Click(s, null); };
            _pw2.KeyDown += (s, e) => { if (e.Key == Key.Enter) Submit_Click(s, null); };
        }

        private static void ApplyInputStyle(PasswordBox box, string placeholder)
        {
            box.Height = 44;
            box.FontSize = 15;
            box.Foreground = Theme.Label;
            box.Background = new SolidColorBrush(Color.FromArgb(255, 40, 40, 44));
            box.BorderBrush = new SolidColorBrush(Color.FromArgb(30, 255, 255, 255));
            box.BorderThickness = new Thickness(1);
            box.Padding = new Thickness(14, 10, 14, 10);
            box.VerticalContentAlignment = VerticalAlignment.Center;
            box.PasswordChar = '●';
            box.ToolTip = placeholder;
        }

        private void Submit_Click(object sender, RoutedEventArgs e)
        {
            var pw = _pw1.Password ?? "";
            _err.Text = "";
            if (pw.Length < 4)
            {
                _err.Text = "主密码至少 4 位";
                return;
            }
            if (_setup)
            {
                if (pw != (_pw2.Password ?? "")) { _err.Text = "两次输入不一致"; return; }
                Vault.Create(pw);
            }
            else
            {
                if (!Vault.Unlock(pw)) { _err.Text = "主密码错误，请重试"; _pw1.Clear(); _pw1.Focus(); return; }
            }
            Data = Vault.ReadData();
            DialogResult = true;
            Close();
        }
    }
}
