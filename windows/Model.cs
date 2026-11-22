using System;
using System.Collections.Generic;

namespace AccountVault
{
    internal class Identity
    {
        public string id { get; set; } = Guid.NewGuid().ToString("N");
        public string type { get; set; } = "email";   // email | phone
        public string value { get; set; } = "";
        public string label { get; set; } = "";
        public string color { get; set; } = "#0a84ff";
        public string notes { get; set; } = "";
    }

    internal class Account
    {
        public string id { get; set; } = Guid.NewGuid().ToString("N");
        public string name { get; set; } = "";
        public string domain { get; set; } = "";
        public string category { get; set; } = "其他";
        public List<string> identityIds { get; set; } = new List<string>();
        public string handle { get; set; } = "";
        public string notes { get; set; } = "";
        public long createdAt { get; set; } = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
    }

    internal class VaultData
    {
        public List<Identity> identities { get; set; } = new List<Identity>();
        public List<Account> accounts { get; set; } = new List<Account>();
    }

    internal static class Presets
    {
        public static readonly string[] Categories =
        {
            "开发", "社交", "购物", "支付金融", "娱乐", "工具", "AI", "加密", "游戏", "其他"
        };
    }
}
