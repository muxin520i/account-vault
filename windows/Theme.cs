using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace AccountVault
{
    /// <summary>
    /// 账号仓库 · 深色现代主题（对齐原项目：纯黑背景 + 蓝色强调 + 圆角卡片）
    /// 全部用纯代码构建控件，避免 XAML 编译依赖。
    /// </summary>
    internal static class Theme
    {
        // 配色（与原项目 CSS 变量一致）
        public static readonly Color C_Bg = Color.FromRgb(0, 0, 0);            // #000
        public static readonly Color C_Card = Color.FromRgb(28, 28, 30);       // #1c1c1e
        public static readonly Color C_Card2 = Color.FromRgb(44, 44, 46);      // #2c2c2e
        public static readonly Color C_Fill = Color.FromRgb(58, 58, 60);       // 填充
        public static readonly Color C_Label = Color.FromRgb(245, 245, 247);   // 主文字
        public static readonly Color C_Secondary = Color.FromRgb(152, 152, 158);
        public static readonly Color C_Tertiary = Color.FromRgb(100, 100, 105);
        public static readonly Color C_Accent = Color.FromRgb(10, 132, 255);   // #0a84ff
        public static readonly Color C_AccentDark = Color.FromRgb(0, 96, 200);
        public static readonly Color C_Red = Color.FromRgb(255, 69, 58);
        public static readonly Color C_Green = Color.FromRgb(48, 209, 88);
        public static readonly Color C_Sep = Color.FromArgb(26, 255, 255, 255);

        public static readonly FontFamily Font = new FontFamily("Microsoft YaHei UI, Segoe UI, sans-serif");

        public static SolidColorBrush Brush(Color c) => new SolidColorBrush(c);
        public static SolidColorBrush Bg => Brush(C_Bg);
        public static SolidColorBrush Card => Brush(C_Card);
        public static SolidColorBrush Label => Brush(C_Label);
        public static SolidColorBrush Secondary => Brush(C_Secondary);
        public static SolidColorBrush Accent => Brush(C_Accent);
        public static SolidColorBrush Red => Brush(C_Red);

        /// <summary>圆角边框容器（卡片）</summary>
        public static Border CardBorder(UIElement child, double radius = 14, Color? bg = null, Thickness? pad = null)
        {
            return new Border
            {
                Background = Brush(bg ?? C_Card),
                CornerRadius = new CornerRadius(radius),
                Padding = pad ?? new Thickness(16),
                Child = child
            };
        }

        /// <summary>现代胶囊按钮</summary>
        public static Button Pill(string text, Color bg, Color fg, double height = 40, double fontSize = 14)
        {
            var btn = new Button
            {
                Content = text,
                Height = height,
                FontSize = fontSize,
                FontWeight = FontWeights.SemiBold,
                FontFamily = Font,
                Foreground = Brush(fg),
                Cursor = System.Windows.Input.Cursors.Hand,
                HorizontalContentAlignment = HorizontalAlignment.Center,
                VerticalContentAlignment = VerticalAlignment.Center,
            };
            btn.Template = ButtonTemplate(bg);
            return btn;
        }

        /// <summary>按钮模板：圆角背景 + 悬停/按下变色</summary>
        public static ControlTemplate ButtonTemplate(Color bg)
        {
            var t = new ControlTemplate(typeof(Button));
            var border = new FrameworkElementFactory(typeof(Border));
            border.SetValue(Border.BackgroundProperty, Brush(bg));
            border.SetValue(Border.CornerRadiusProperty, new CornerRadius(12));
            border.SetValue(Border.PaddingProperty, new Thickness(14, 8, 14, 8));
            var cp = new FrameworkElementFactory(typeof(ContentPresenter));
            cp.SetValue(ContentPresenter.HorizontalAlignmentProperty, HorizontalAlignment.Center);
            cp.SetValue(ContentPresenter.VerticalAlignmentProperty, VerticalAlignment.Center);
            border.AppendChild(cp);
            t.VisualTree = border;
            return t;
        }

        /// <summary>幽灵按钮（次要操作）：透明底 + 细边框</summary>
        public static Button GhostButton(string text, double height = 36)
        {
            var btn = new Button
            {
                Content = text,
                Height = height,
                FontSize = 13,
                FontFamily = Font,
                Foreground = Label,
                Cursor = System.Windows.Input.Cursors.Hand,
            };
            var t = new ControlTemplate(typeof(Button));
            var border = new FrameworkElementFactory(typeof(Border));
            border.SetValue(Border.BackgroundProperty, Brush(C_Card2));
            border.SetValue(Border.CornerRadiusProperty, new CornerRadius(10));
            border.SetValue(Border.PaddingProperty, new Thickness(12, 6, 12, 6));
            var cp = new FrameworkElementFactory(typeof(ContentPresenter));
            cp.SetValue(ContentPresenter.HorizontalAlignmentProperty, HorizontalAlignment.Center);
            cp.SetValue(ContentPresenter.VerticalAlignmentProperty, VerticalAlignment.Center);
            border.AppendChild(cp);
            t.VisualTree = border;
            btn.Template = t;
            return btn;
        }

        /// <summary>深色输入框</summary>
        public static TextBox Input(string placeholder = "")
        {
            var tb = new TextBox
            {
                Height = 42,
                FontSize = 14,
                FontFamily = Font,
                Foreground = Label,
                Background = Brush(C_Card2),
                BorderBrush = Brush(C_Sep),
                BorderThickness = new Thickness(1),
                Padding = new Thickness(12, 9, 12, 9),
                VerticalContentAlignment = VerticalAlignment.Center,
                Tag = placeholder,
            };
            SetPlaceholder(tb, placeholder);
            return tb;
        }

        public static void SetPlaceholder(TextBox tb, string placeholder)
        {
            if (string.IsNullOrEmpty(tb.Text) && !string.IsNullOrEmpty(placeholder))
            {
                tb.Text = placeholder;
                tb.Foreground = Secondary;
            }
        }

        public static string RealText(TextBox tb)
        {
            var s = tb.Text ?? "";
            if (s == (tb.Tag as string)) return "";
            return s;
        }

        /// <summary>深色密码框</summary>
        public static PasswordBox PasswordInput()
        {
            return new PasswordBox
            {
                Height = 42,
                FontSize = 14,
                FontFamily = Font,
                Foreground = Label,
                Background = Brush(C_Card2),
                BorderBrush = Brush(C_Sep),
                BorderThickness = new Thickness(1),
                Padding = new Thickness(12, 9, 12, 9),
                VerticalContentAlignment = VerticalAlignment.Center,
                PasswordChar = '●',
            };
        }

        /// <summary>文字块</summary>
        public static TextBlock Text(string s, double size = 14, Color? color = null, bool bold = false, TextAlignment align = TextAlignment.Left)
        {
            return new TextBlock
            {
                Text = s,
                FontSize = size,
                FontFamily = Font,
                Foreground = Brush(color ?? C_Label),
                FontWeight = bold ? FontWeights.SemiBold : FontWeights.Normal,
                TextAlignment = align,
                TextWrapping = TextWrapping.Wrap,
                VerticalAlignment = VerticalAlignment.Center,
            };
        }

        public static Color Lighten(Color c, double amt)
        {
            int r = Math.Min(255, (int)(c.R + (255 - c.R) * amt));
            int g = Math.Min(255, (int)(c.G + (255 - c.G) * amt));
            int b = Math.Min(255, (int)(c.B + (255 - c.B) * amt));
            return Color.FromRgb((byte)r, (byte)g, (byte)b);
        }

        /// <summary>从颜色字符串（#RRGGBB）解析</summary>
        public static Color Parse(string hex, Color fallback)
        {
            try
            {
                if (string.IsNullOrEmpty(hex) || hex.Length < 7 || hex[0] != '#') return fallback;
                return Color.FromRgb(
                    Convert.ToByte(hex.Substring(1, 2), 16),
                    Convert.ToByte(hex.Substring(3, 2), 16),
                    Convert.ToByte(hex.Substring(5, 2), 16));
            }
            catch { return fallback; }
        }
    }
}
