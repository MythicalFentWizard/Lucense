using System;
using System.Runtime.InteropServices;
using System.Text;

namespace LucenseSetup
{
    /// <summary>
    /// Windows Installer, driven directly: the MSI runs with its own dialogs
    /// switched off, and what it reports - progress, the action under way,
    /// errors - comes here to be shown in Lucense's own window.
    /// </summary>
    internal static class Msi
    {
        // ---- What Lucense is to Windows Installer ----------------------------------

        /// <summary>The same in every Lucense MSI, so each replaces the last (desktop/build.gradle.kts); the payload says which.</summary>
        public const string UpgradeCode = "{6E3F1C42-6B7B-4C3E-9A1F-2B7D5E0A9C11}";

        public sealed class Installed
        {
            public string ProductCode;
            public Version Version;
            public string Location;
        }

        /// <summary>The Lucense already on this PC, or null.</summary>
        public static Installed FindInstalled(string upgradeCode)
        {
            var code = new StringBuilder(39);
            for (uint i = 0; MsiEnumRelatedProducts(upgradeCode, 0, i, code) == 0; i++)
            {
                var product = code.ToString();
                var version = Info(product, "VersionString");
                if (version == null) continue;
                Version.TryParse(version, out var parsed);
                return new Installed { ProductCode = product, Version = parsed, Location = Info(product, "InstallLocation") };
            }
            return null;
        }

        private static string Info(string product, string property)
        {
            uint size = 512;
            var value = new StringBuilder((int)size);
            return MsiGetProductInfo(product, property, value, ref size) == 0 ? value.ToString() : null;
        }

        // ---- Running it ------------------------------------------------------------

        /// <summary>What the installer is up to, as it says so.</summary>
        public interface IListener
        {
            /// <summary>How far, from 0 to 1, and what is being done; on the installer's thread.</summary>
            void Progress(double fraction, string doing);

            /// <summary>Whether to stop: asked as it goes.</summary>
            bool Cancelled { get; }

            void Error(string message);
        }

        /// <summary>Installs [package] with [properties]; Windows Installer's own result code.</summary>
        public static uint Install(string package, string properties, string log, IListener listener) =>
            Run(listener, log, () => MsiInstallProduct(package, properties));

        /// <summary>Removes the product [productCode].</summary>
        public static uint Remove(string productCode, string log, IListener listener) =>
            Run(listener, log, () => MsiConfigureProductEx(productCode, 0, InstallStateAbsent, "REBOOT=ReallySuppress"));

        private static uint Run(IListener listener, string log, Func<uint> action)
        {
            var reader = new ProgressReader(listener);
            // Kept in a field of the reader for as long as the installer may call it.
            InstallUIHandler handler = reader.Handle;
            var parent = IntPtr.Zero;
            MsiSetInternalUI(InstallUiLevelNone, ref parent);
            if (log != null) MsiEnableLog(LogModes, log, 0);
            var previous = MsiSetExternalUI(handler, MessageFilter, IntPtr.Zero);
            try
            {
                return action();
            }
            finally
            {
                MsiSetExternalUI(previous, 0, IntPtr.Zero);
                GC.KeepAlive(handler);
            }
        }

        /// <summary>
        /// Windows Installer's progress messages turned into one fraction. It
        /// counts in ticks over two passes - working out what to do, then doing
        /// it - restarting the count for each; the first pass is given a tenth
        /// of the bar, so the bar only ever moves forward.
        /// </summary>
        private sealed class ProgressReader
        {
            private readonly IListener listener;
            private long total, done, step;
            private bool forward = true, perAction, planning;
            private double shown;
            private string doing = "Getting ready…";

            public ProgressReader(IListener listener) { this.listener = listener; }

            public int Handle(IntPtr context, uint type, string message)
            {
                if (listener.Cancelled) return IdCancel;
                switch (type & 0xFF000000)
                {
                    case MessageProgress:
                        OnProgress(message);
                        break;
                    case MessageActionData:
                        if (perAction && total > 0) { done += forward ? step : -step; Report(); }
                        break;
                    case MessageActionStart:
                        doing = Describe(message) ?? doing;
                        Report();
                        break;
                    case MessageError:
                    case MessageFatalExit:
                        if (!string.IsNullOrWhiteSpace(message)) listener.Error(message.Trim());
                        break;
                    // Lucense is closed before starting. Anything else holding one of its
                    // files is left running: the file is replaced on the next restart.
                    case MessageFilesInUse:
                        return IdIgnore;
                    case MessageRmFilesInUse:
                        return IdNo;
                }
                return 0;
            }

            private void OnProgress(string message)
            {
                var f = Fields(message);
                if (f.Length < 2) return;
                switch (f[0])
                {
                    case 0: // a new count
                        total = f[1];
                        forward = f.Length < 3 || f[2] == 0;
                        done = forward ? 0 : total;
                        perAction = false;
                        planning = f.Length >= 4 && f[3] == 1;
                        break;
                    case 1: // ticks for each step of the action under way
                        if (f.Length >= 3 && f[2] == 1) { step = f[1]; perAction = true; } else perAction = false;
                        break;
                    case 2: // ticks done
                        if (total > 0) done += forward ? f[1] : -f[1];
                        break;
                    case 3: // more ticks to do
                        total += f[1];
                        break;
                }
                Report();
            }

            private void Report()
            {
                var part = total > 0 ? Math.Max(0, Math.Min(1, (double)done / total)) : 0;
                var overall = planning ? part * 0.1 : 0.1 + part * 0.9;
                shown = Math.Max(shown, overall);
                listener.Progress(shown, doing);
            }

            /// <summary>"1: 0 2: 4135 3: 0 4: 1" as numbers.</summary>
            private static long[] Fields(string message)
            {
                var parts = message.Split(new[] { ' ' }, StringSplitOptions.RemoveEmptyEntries);
                var values = new System.Collections.Generic.List<long>();
                for (var i = 0; i + 1 < parts.Length; i += 2)
                {
                    if (!long.TryParse(parts[i + 1], out var value)) break;
                    values.Add(value);
                }
                return values.ToArray();
            }

            /// <summary>The action under way, in plain words; null to keep the last.</summary>
            private static string Describe(string message)
            {
                // "Action 12:34:56: InstallFiles. Copying new files"
                var name = message;
                var colon = message.IndexOf(": ", StringComparison.Ordinal);
                if (colon >= 0) name = message.Substring(colon + 2);
                var dot = name.IndexOf('.');
                var action = dot >= 0 ? name.Substring(0, dot) : name;
                switch (action.Trim())
                {
                    case "InstallValidate":
                    case "InstallInitialize":
                    case "CostInitialize":
                    case "FileCost":
                    case "CostFinalize":
                        return "Getting ready…";
                    case "RemoveExistingProducts":
                    case "UnpublishProduct":
                    case "RemoveFiles":
                        return "Taking away the old version…";
                    case "InstallFiles":
                        return "Copying Lucense and its tools…";
                    case "CreateShortcuts":
                    case "RemoveShortcuts":
                        return "Putting it in the Start menu…";
                    case "WriteRegistryValues":
                    case "RegisterProduct":
                    case "PublishFeatures":
                    case "PublishProduct":
                    case "InstallFinalize":
                        return "Finishing…";
                    default:
                        return null;
                }
            }
        }

        // ---- Windows Installer's own API -----------------------------------------

        private const int InstallUiLevelNone = 2;
        private const int InstallStateAbsent = 2;
        private const int IdCancel = 2;
        private const int IdIgnore = 5;
        private const int IdNo = 7;

        private const uint MessageFatalExit = 0x00000000;
        private const uint MessageError = 0x01000000;
        private const uint MessageFilesInUse = 0x05000000;
        private const uint MessageActionStart = 0x08000000;
        private const uint MessageActionData = 0x09000000;
        private const uint MessageProgress = 0x0A000000;
        private const uint MessageRmFilesInUse = 0x19000000;

        // INSTALLLOGMODE flags: what the handler hears.
        private const uint MessageFilter =
            (1u << 0) | (1u << 1) | (1u << 5) | (1u << 8) | (1u << 9) | (1u << 10) | (1u << 25);

        // What the log keeps: errors, warnings, information, actions and their data, the outcome.
        private const uint LogModes =
            (1u << 0) | (1u << 1) | (1u << 2) | (1u << 3) | (1u << 4) | (1u << 6) | (1u << 7) | (1u << 8) | (1u << 9);

        private delegate int InstallUIHandler(IntPtr context, uint messageType, [MarshalAs(UnmanagedType.LPWStr)] string message);

        [DllImport("msi.dll", CharSet = CharSet.Unicode)]
        private static extern uint MsiInstallProduct(string packagePath, string commandLine);

        [DllImport("msi.dll", CharSet = CharSet.Unicode)]
        private static extern uint MsiConfigureProductEx(string product, int installLevel, int installState, string commandLine);

        [DllImport("msi.dll")]
        private static extern int MsiSetInternalUI(int uiLevel, ref IntPtr window);

        [DllImport("msi.dll", CharSet = CharSet.Unicode)]
        private static extern InstallUIHandler MsiSetExternalUI(InstallUIHandler handler, uint messageFilter, IntPtr context);

        [DllImport("msi.dll", CharSet = CharSet.Unicode)]
        private static extern uint MsiEnableLog(uint logMode, string logFile, uint logAttributes);

        [DllImport("msi.dll", CharSet = CharSet.Unicode)]
        private static extern uint MsiEnumRelatedProducts(string upgradeCode, uint reserved, uint index, StringBuilder productCode);

        [DllImport("msi.dll", CharSet = CharSet.Unicode)]
        private static extern uint MsiGetProductInfo(string product, string property, StringBuilder value, ref uint size);
    }
}
