using System;
using System.IO;
using System.Text;

namespace LucenseSetup
{
    /// <summary>
    /// The MSI this installer carries, appended to its own file by
    /// tools/build-setup.ps1:
    /// <c>[setup][msi][version and upgrade code, a line each, UTF-8][their length: int32][msi length: int64]["LUCSETUP"]</c>.
    /// Appending rather than embedding keeps the installer the same program
    /// for every release: only what follows it changes.
    /// </summary>
    internal sealed class Payload
    {
        private static readonly byte[] Magic = Encoding.ASCII.GetBytes("LUCSETUP");

        public string Version { get; private set; }

        /// <summary>The MSI's upgrade code: what every Lucense shares, so each finds the last.</summary>
        public string UpgradeCode { get; private set; }

        public long Length { get; private set; }
        private string file;
        private long offset;

        /// <summary>What [path] carries, or null when it carries nothing (a build run on its own).</summary>
        public static Payload Read(string path)
        {
            using (var stream = File.OpenRead(path))
            using (var reader = new BinaryReader(stream))
            {
                if (stream.Length < 20) return null;
                stream.Seek(-8, SeekOrigin.End);
                var magic = reader.ReadBytes(8);
                for (var i = 0; i < 8; i++) if (magic[i] != Magic[i]) return null;
                stream.Seek(-20, SeekOrigin.End);
                var versionLength = reader.ReadInt32();
                var msiLength = reader.ReadInt64();
                var versionAt = stream.Length - 20 - versionLength;
                var msiAt = versionAt - msiLength;
                if (versionLength <= 0 || versionLength > 256 || msiLength <= 0 || msiAt <= 0) return null;
                stream.Seek(versionAt, SeekOrigin.Begin);
                var lines = Encoding.UTF8.GetString(reader.ReadBytes(versionLength)).Split(new[] { '\n' });
                return new Payload
                {
                    Version = lines[0].Trim(),
                    UpgradeCode = lines.Length > 1 ? lines[1].Trim() : Msi.UpgradeCode,
                    Length = msiLength,
                    file = path,
                    offset = msiAt
                };
            }
        }

        /// <summary>Writes the MSI out to [target], reporting how far it has got.</summary>
        public void Extract(string target, Action<double> progress)
        {
            var buffer = new byte[1 << 20];
            using (var source = File.OpenRead(file))
            using (var output = File.Create(target))
            {
                source.Seek(offset, SeekOrigin.Begin);
                long left = Length;
                while (left > 0)
                {
                    var read = source.Read(buffer, 0, (int)Math.Min(buffer.Length, left));
                    if (read <= 0) throw new EndOfStreamException("The installer's file is cut short. Download it again.");
                    output.Write(buffer, 0, read);
                    left -= read;
                    progress?.Invoke(1.0 - (double)left / Length);
                }
            }
        }
    }
}
