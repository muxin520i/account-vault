using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace AccountVault
{
    /// <summary>
    /// 账号仓库 · 本地加密保险层
    /// vault.dat = {"v":1,"salt":b64,"iv":b64,"ct":b64(ciphertext||tag)}
    /// key = PBKDF2-HMAC-SHA256(主密码 UTF-8, salt[16], 310000, 32)
    /// </summary>
    internal static class Vault
    {
        private const int ITERATIONS = 310_000;
        private const string PROBE = "accountvault-probe-v1";
        private static readonly string AppDir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "AccountVault");
        private static readonly string DataFile = Path.Combine(AppDir, "vault.dat");
        private static readonly string MetaFile = Path.Combine(AppDir, "vault.meta.json");

        private static byte[] _key;

        public static bool IsSetup => File.Exists(MetaFile) && File.Exists(DataFile);
        public static bool IsUnlocked => _key != null;

        private class Meta { public string salt { get; set; } public string probeIv { get; set; } public string probeCt { get; set; } }

        private static void EnsureDir() { if (!Directory.Exists(AppDir)) Directory.CreateDirectory(AppDir); }

        private static byte[] Derive(string password, byte[] salt)
        {
            using var pbkdf2 = new Rfc2898DeriveBytes(password, salt, ITERATIONS, HashAlgorithmName.SHA256);
            return pbkdf2.GetBytes(32);
        }

        private static byte[] Encrypt(byte[] plain, byte[] key, byte[] iv)
        {
            using var aes = new AesGcm(key);
            var ct = new byte[plain.Length];
            var tag = new byte[16];
            aes.Encrypt(iv, plain, ct, tag);
            var outBuf = new byte[ct.Length + 16];
            Buffer.BlockCopy(ct, 0, outBuf, 0, ct.Length);
            Buffer.BlockCopy(tag, 0, outBuf, ct.Length, 16);
            return outBuf;
        }

        private static byte[] Decrypt(byte[] ctTag, byte[] key, byte[] iv)
        {
            using var aes = new AesGcm(key);
            var ct = new byte[ctTag.Length - 16];
            var tag = new byte[16];
            Buffer.BlockCopy(ctTag, 0, ct, 0, ct.Length);
            Buffer.BlockCopy(ctTag, ct.Length, tag, 0, 16);
            var plain = new byte[ct.Length];
            aes.Decrypt(iv, ct, tag, plain);
            return plain;
        }

        private static byte[] Random(int n) { var b = new byte[n]; using var rng = RandomNumberGenerator.Create(); rng.GetBytes(b); return b; }

        /// <summary>首次设置主密码</summary>
        public static void Create(string password)
        {
            EnsureDir();
            var salt = Random(16);
            var key = Derive(password, salt);
            var piv = Random(12);
            var pct = Encrypt(Encoding.UTF8.GetBytes(PROBE), key, piv);
            var meta = new Meta { salt = Convert.ToBase64String(salt), probeIv = Convert.ToBase64String(piv), probeCt = Convert.ToBase64String(pct) };
            File.WriteAllText(MetaFile, JsonSerializer.Serialize(meta));
            _key = key;
            // 空数据加密落盘
            WriteData(new VaultData());
        }

        private static Meta ReadMeta()
        {
            if (!File.Exists(MetaFile)) return null;
            return JsonSerializer.Deserialize<Meta>(File.ReadAllText(MetaFile));
        }

        /// <summary>用主密码验证身份，成功则派生密钥驻留内存</summary>
        public static bool Unlock(string password)
        {
            var meta = ReadMeta();
            if (meta == null || !File.Exists(DataFile)) return false;
            try
            {
                var salt = Convert.FromBase64String(meta.salt);
                var key = Derive(password, salt);
                var piv = Convert.FromBase64String(meta.probeIv);
                var pct = Convert.FromBase64String(meta.probeCt);
                var probe = Encoding.UTF8.GetString(Decrypt(pct, key, piv));
                if (probe != PROBE) return false;
                _key = key;
                return true;
            }
            catch { return false; }
        }

        public static bool Verify(string password)
        {
            var meta = ReadMeta();
            if (meta == null) return false;
            try
            {
                var key = Derive(password, Convert.FromBase64String(meta.salt));
                var probe = Encoding.UTF8.GetString(Decrypt(Convert.FromBase64String(meta.probeCt), key, Convert.FromBase64String(meta.probeIv)));
                return probe == PROBE;
            }
            catch { return false; }
        }

        public static bool ChangePassword(string old, string neu)
        {
            if (!Verify(old) || neu.Length < 4) return false;
            var data = ReadData();
            var salt = Random(16);
            var key = Derive(neu, salt);
            var piv = Random(12);
            var pct = Encrypt(Encoding.UTF8.GetBytes(PROBE), key, piv);
            var meta = new Meta { salt = Convert.ToBase64String(salt), probeIv = Convert.ToBase64String(piv), probeCt = Convert.ToBase64String(pct) };
            File.WriteAllText(MetaFile, JsonSerializer.Serialize(meta));
            _key = key;
            WriteData(data);
            return true;
        }

        public static void Lock() { _key = null; }

        private class Envelope
        {
            public string app { get; set; }
            public int v { get; set; }
            public string salt { get; set; }
            public string iv { get; set; }
            public string ct { get; set; }
        }

        /// <summary>读取并解密数据；返回空数据表示损坏或密码错误</summary>
        public static VaultData ReadData()
        {
            if (_key == null || !File.Exists(DataFile)) return new VaultData();
            try
            {
                var env = JsonSerializer.Deserialize<Envelope>(File.ReadAllText(DataFile));
                var iv = Convert.FromBase64String(env.iv);
                var ct = Convert.FromBase64String(env.ct);
                var plain = Decrypt(ct, _key, iv);
                return JsonSerializer.Deserialize<VaultData>(Encoding.UTF8.GetString(plain)) ?? new VaultData();
            }
            catch { return new VaultData(); }
        }

        private static void WriteData(VaultData data)
        {
            if (_key == null) return;
            EnsureDir();
            var iv = Random(12);
            var plain = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(data));
            var ct = Encrypt(plain, _key, iv);
            var env = new Envelope
            {
                app = "accountvault-local", v = 1,
                salt = ReadMeta()?.salt,
                iv = Convert.ToBase64String(iv),
                ct = Convert.ToBase64String(ct),
            };
            var tmp = DataFile + ".tmp";
            File.WriteAllText(tmp, JsonSerializer.Serialize(env));
            File.Move(tmp, DataFile, true);
        }

        public static void Save(VaultData data) => WriteData(data);

        /// <summary>导出加密备份（内容仍为密文，备份密码=主密码）</summary>
        public static string ExportEncrypted()
        {
            if (_key == null) return null;
            var data = ReadData();
            var iv = Random(12);
            var plain = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(data));
            var ct = Encrypt(plain, _key, iv);
            var env = new Envelope
            {
                app = "accountvault-backup", v = 1,
                salt = ReadMeta()?.salt,
                iv = Convert.ToBase64String(iv),
                ct = Convert.ToBase64String(ct),
            };
            return JsonSerializer.Serialize(env);
        }

        /// <summary>导入加密备份：用当前主密码解开验证后替换数据</summary>
        public static bool ImportEncrypted(string text)
        {
            if (_key == null) return false;
            try
            {
                var env = JsonSerializer.Deserialize<Envelope>(text);
                if (env?.app != "accountvault-backup") return false;
                var plain = Decrypt(Convert.FromBase64String(env.ct), _key, Convert.FromBase64String(env.iv));
                var data = JsonSerializer.Deserialize<VaultData>(Encoding.UTF8.GetString(plain));
                if (data == null) return false;
                WriteData(data);
                return true;
            }
            catch { return false; }
        }
    }
}
