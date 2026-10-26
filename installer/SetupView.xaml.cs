using System;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media.Animation;

namespace LucenseSetup
{
    /// <summary>
    /// Everything the installer shows and does: one card, a page at a time -
    /// install or update, working, done, what went wrong, and what to do when
    /// this version is already there.
    /// </summary>
    public partial class SetupView : UserControl, Msi.IListener
    {
        /// <summary>The window around the card, for moving, minimising and closing it.</summary>
        public Window Host { get; set; }

        private Payload payload;
        private Msi.Installed installed;
        private Version ours;
        private string installDir;
        private string log;
        private volatile bool cancelled;
        private volatile bool working;
        private string firstError;
        private Func<Task> lastJob;

        public SetupView()
        {
            InitializeComponent();
        }

        // ---- Starting ---------------------------------------------------------------

        internal void Start(Payload carried)
        {
            payload = carried;
            if (payload == null)
            {
                VersionText.Text = "";
                ShowError("There's nothing to install", "This copy of the installer carries no Lucense. Download it again from the Lucense releases page.", retry: false);
                return;
            }
            Version.TryParse(Normalise(payload.Version), out ours);
            VersionText.Text = "Version " + payload.Version;
            installed = Msi.FindInstalled(payload.UpgradeCode);
            var programFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
            installDir = (installed?.Location).NullIfBlank()?.TrimEnd('\\') ?? Path.Combine(programFiles, "Lucense");
            LocationText.Text = installDir;
            LocationText.ToolTip = installDir;

            if (installed == null || installed.Version == null || ours == null || installed.Version < ours)
            {
                var updating = installed != null;
                WelcomeTitle.Text = updating ? "Update Lucense" : "Install Lucense";
                InstallButton.Content = updating ? "Update" : "Install";
                if (updating)
                {
                    WelcomeText.Text = $"Lucense {payload.Version} takes the place of {Shown(installed.Version)}. Your library, " +
                                       "playlists, ratings and play counts stay just as they are.";
                }
                RunningNote.Visibility = updating && LucenseIsOpen() ? Visibility.Visible : Visibility.Collapsed;
                Show(WelcomePage);
                return;
            }

            if (installed.Version == ours)
            {
                InstalledTitle.Text = $"Lucense {payload.Version} is installed";
                InstalledText.Text = InstalledNote;
            }
            else
            {
                InstalledTitle.Text = "A newer Lucense is installed";
                InstalledText.Text = $"Lucense {Shown(installed.Version)} is already on this PC, which is newer than this " +
                                     $"installer's {payload.Version}. Nothing needs doing.";
                ReinstallButton.Visibility = Visibility.Collapsed;
                RemoveButton.Visibility = Visibility.Collapsed;
            }
            Show(InstalledPage);
        }

        private const string InstalledNote =
            "Open it, reinstall it if something about it seems broken, or remove it. Removing it leaves your music, " +
            "and Lucense's library and settings, where they are.";

        /// <summary>"1.53.0" from Windows Installer's "1.53.0.0" or similar.</summary>
        private static string Shown(Version version) =>
            version == null ? "" : version.Build >= 0 ? $"{version.Major}.{version.Minor}.{version.Build}" : $"{version.Major}.{version.Minor}";

        private static string Normalise(string version) => version.Count(c => c == '.') == 0 ? version + ".0" : version;

        // ---- Installing -------------------------------------------------------------

        private void OnInstall(object sender, RoutedEventArgs e) => Run(InstallAsync);

        private void OnReinstall(object sender, RoutedEventArgs e) => Run(InstallAsync);

        private void OnRemove(object sender, RoutedEventArgs e) => Run(RemoveAsync);

        private void OnRetry(object sender, RoutedEventArgs e)
        {
            if (lastJob != null) Run(lastJob);
        }

        private async void Run(Func<Task> job)
        {
            lastJob = job;
            try
            {
                await job();
            }
            catch (Exception failure)
            {
                working = false;
                ShowError("That didn't work", failure.Message, retry: true);
            }
        }

        private async Task InstallAsync()
        {
            var updating = installed != null && installed.Version != null && ours != null && installed.Version < ours;
            var reinstalling = installed != null && installed.Version == ours;
            BeginWork(updating ? "Updating Lucense" : reinstalling ? "Reinstalling Lucense" : "Installing Lucense");

            await Task.Run(CloseLucense);
            if (cancelled) { BackToStart(); return; }

            var msi = Path.Combine(Path.GetTempPath(), $"Lucense-{payload.Version}.msi");
            await Task.Run(() => payload.Extract(msi, part => Progress(part * Unpacking, "Unpacking…")));
            if (cancelled) { BackToStart(); TryDelete(msi); return; }

            var shortcut = DesktopShortcut.IsChecked == true ? "1" : "\"\"";
            var properties = $"INSTALLDIR=\"{installDir}\" JP_INSTALL_DESKTOP_SHORTCUT={shortcut} REBOOT=ReallySuppress";
            // A reinstall puts every file back, whatever Windows Installer thinks of them.
            if (reinstalling) properties += " REINSTALL=ALL REINSTALLMODE=amus";
            log = Path.Combine(Path.GetTempPath(), "Lucense-setup.log");
            var result = await Task.Run(() => Msi.Install(msi, properties, log, new Scaled(this, Unpacking)));
            TryDelete(msi);
            working = false;

            switch (result)
            {
                case 0:
                case 3010:
                    installed = Msi.FindInstalled(payload.UpgradeCode) ?? installed;
                    DoneTitle.Text = updating ? "Lucense is updated" : "Lucense is ready";
                    DoneText.Text = (updating ? "Everything you had is where you left it. " : "Find it in the Start menu, or open it now. ") +
                                    (result == 3010 ? "Restart Windows when it suits you to finish putting it in place." : "");
                    OpenButton.Visibility = Visibility.Visible;
                    Show(DonePage);
                    break;
                case 1602:
                    BackToStart();
                    break;
                default:
                    ShowError("That didn't work", Explain(result), retry: true);
                    break;
            }
        }

        private async Task RemoveAsync()
        {
            BeginWork("Removing Lucense", removing: true);
            await Task.Run(CloseLucense);
            log = Path.Combine(Path.GetTempPath(), "Lucense-setup.log");
            var result = await Task.Run(() => Msi.Remove(installed.ProductCode, log, this));
            working = false;
            if (result == 0 || result == 3010)
            {
                installed = null;
                DoneTitle.Text = "Lucense is removed";
                DoneText.Text = "Your music, and Lucense's library and settings, are still on this PC, so installing it again " +
                                "picks up where you left off.";
                OpenButton.Visibility = Visibility.Collapsed;
                Show(DonePage);
            }
            else if (result == 1602)
            {
                BackToStart();
            }
            else
            {
                ShowError("Lucense couldn't be removed", Explain(result), retry: true);
            }
        }

        /// <summary>What Windows Installer's [code] means, in words that say what to do.</summary>
        private string Explain(uint code)
        {
            switch (code)
            {
                case 1618:
                    return "Another installation is under way on this PC. Let it finish, then try again.";
                case 1625:
                case 1643:
                    return "This PC's policy doesn't allow installing programs. Ask whoever looks after it.";
                case 1632:
                    return "Windows' temporary folder can't be written to. Restart Windows, then try again.";
                case 112:
                case 1601:
                    return "There isn't enough room on the drive. Free some space, or choose another folder, then try again.";
            }
            var said = firstError ?? new Win32Exception((int)code).Message;
            return $"{said} (Windows Installer said {code}.)";
        }

        private void BeginWork(string title, bool removing = false)
        {
            working = true;
            cancelled = false;
            firstError = null;
            ProgressTitle.Text = title;
            ProgressText.Text = removing
                ? "Taking Lucense off this PC. Your music, and Lucense's library and settings, stay where they are."
                : "This takes a minute. Everything Lucense needs comes inside it, so there's nothing else to download.";
            Bar.Value = 0;
            PercentText.Text = "0%";
            StatusText.Text = "Getting ready…";
            CancelButton.IsEnabled = true;
            Show(ProgressPage);
        }

        private void BackToStart()
        {
            working = false;
            Start(payload);
        }

        /// <summary>Lucense open while it is replaced: asked to close, as its own close button would, then ended.</summary>
        private void CloseLucense()
        {
            var open = Process.GetProcessesByName("Lucense");
            if (open.Length == 0) return;
            Progress(0, "Closing Lucense…");
            foreach (var process in open)
            {
                try { process.CloseMainWindow(); } catch { /* already gone */ }
            }
            var until = DateTime.UtcNow.AddSeconds(10);
            while (DateTime.UtcNow < until && open.Any(p => { try { return !p.HasExited; } catch { return false; } }))
            {
                Thread.Sleep(200);
            }
            foreach (var process in open)
            {
                try { if (!process.HasExited) process.Kill(); } catch { /* gone, or not ours to end */ }
            }
        }

        private static bool LucenseIsOpen() => Process.GetProcessesByName("Lucense").Length > 0;

        private static void TryDelete(string file)
        {
            try { File.Delete(file); } catch { /* left in the temporary folder */ }
        }

        // ---- What the installer says --------------------------------------------------

        /// <summary>The first share of the bar goes to unpacking; Windows Installer's progress fills the rest.</summary>
        private const double Unpacking = 0.06;

        bool Msi.IListener.Cancelled => cancelled;

        public void Progress(double fraction, string doing)
        {
            Dispatcher.BeginInvoke(new Action(() =>
            {
                Bar.Value = fraction;
                PercentText.Text = $"{(int)Math.Round(fraction * 100)}%";
                if (!string.IsNullOrEmpty(doing)) StatusText.Text = doing;
            }));
        }

        public void Error(string message)
        {
            if (firstError == null) firstError = message;
        }

        /// <summary>Windows Installer's part of the bar: what is left after unpacking.</summary>
        private sealed class Scaled : Msi.IListener
        {
            private readonly SetupView view;
            private readonly double start;
            public Scaled(SetupView view, double start) { this.view = view; this.start = start; }
            public bool Cancelled => view.cancelled;
            public void Progress(double fraction, string doing) => view.Progress(start + fraction * (1 - start), doing);
            public void Error(string message) => view.Error(message);
        }

        // ---- Pages ----------------------------------------------------------------------

        private void Show(FrameworkElement page)
        {
            foreach (var each in new FrameworkElement[] { WelcomePage, ProgressPage, DonePage, ErrorPage, InstalledPage })
            {
                each.Visibility = each == page ? Visibility.Visible : Visibility.Collapsed;
            }
            // Each page slides up a little as it comes in.
            var shift = new System.Windows.Media.TranslateTransform(0, 10);
            page.RenderTransform = shift;
            page.BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(220)));
            shift.BeginAnimation(System.Windows.Media.TranslateTransform.YProperty,
                new DoubleAnimation(10, 0, TimeSpan.FromMilliseconds(260)) { EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut } });
        }

        private void ShowError(string title, string text, bool retry)
        {
            ErrorTitle.Text = title;
            ErrorText.Text = text;
            RetryButton.Visibility = retry ? Visibility.Visible : Visibility.Collapsed;
            LogButton.Visibility = log != null && File.Exists(log) ? Visibility.Visible : Visibility.Collapsed;
            Show(ErrorPage);
        }

        // ---- Buttons ----------------------------------------------------------------------

        private void OnChangeLocation(object sender, RoutedEventArgs e)
        {
            var handle = Host != null ? new WindowInteropHelper(Host).Handle : IntPtr.Zero;
            var chosen = FolderPicker.Pick(handle, installDir, "Where to install Lucense");
            if (string.IsNullOrEmpty(chosen)) return;
            // A folder of its own, unless one was chosen already.
            installDir = Path.GetFileName(chosen).Equals("Lucense", StringComparison.OrdinalIgnoreCase)
                ? chosen
                : Path.Combine(chosen, "Lucense");
            LocationText.Text = installDir;
            LocationText.ToolTip = installDir;
        }

        private void OnOpen(object sender, RoutedEventArgs e)
        {
            var exe = Path.Combine((installed?.Location).NullIfBlank() ?? installDir, "Lucense.exe");
            if (!File.Exists(exe)) exe = Path.Combine(installDir, "Lucense.exe");
            try
            {
                // Through Explorer, so Lucense runs as the person, not with the installer's administrator rights.
                Process.Start(new ProcessStartInfo("explorer.exe", "\"" + exe + "\"") { UseShellExecute = true });
            }
            catch
            {
                // Nothing to do but leave it to the Start menu.
            }
            Host?.Close();
        }

        private void OnOpenLog(object sender, RoutedEventArgs e)
        {
            if (log == null || !File.Exists(log)) return;
            try { Process.Start(new ProcessStartInfo("notepad.exe", "\"" + log + "\"") { UseShellExecute = true }); } catch { }
        }

        private void OnCancel(object sender, RoutedEventArgs e)
        {
            cancelled = true;
            CancelButton.IsEnabled = false;
            StatusText.Text = "Stopping, and putting things back as they were…";
        }

        private void OnClose(object sender, RoutedEventArgs e)
        {
            // Mid-install, closing means stopping: the window stays until Windows Installer has put things back.
            if (working)
            {
                OnCancel(sender, e);
                return;
            }
            Host?.Close();
        }

        private void OnMinimize(object sender, RoutedEventArgs e)
        {
            if (Host != null) Host.WindowState = WindowState.Minimized;
        }

        private void OnDrag(object sender, MouseButtonEventArgs e)
        {
            if (e.ChangedButton != MouseButton.Left || Host == null) return;
            try { Host.DragMove(); } catch (InvalidOperationException) { /* the button was already up */ }
        }

        // ---- For trying it out --------------------------------------------------------------

        /// <summary>Installs to [dir] instead, for trials.</summary>
        internal void UseFolder(string dir)
        {
            installDir = dir;
            LocationText.Text = dir;
        }

        /// <summary>Presses what the page offers - Install, Update, or Remove - as a person would.</summary>
        internal void PressForTrial(bool remove)
        {
            if (WelcomePage.Visibility == Visibility.Visible) Run(InstallAsync);
            else if (InstalledPage.Visibility == Visibility.Visible && remove) Run(RemoveAsync);
        }

        /// <summary>Which page is showing and what it says, for trials to check.</summary>
        internal string Describe()
        {
            foreach (var page in new FrameworkElement[] { WelcomePage, ProgressPage, DonePage, ErrorPage, InstalledPage })
            {
                if (page.Visibility != Visibility.Visible) continue;
                var texts = new System.Collections.Generic.List<string>();
                Collect(page, texts);
                return page.Name + ": " + string.Join(" | ", texts);
            }
            return "no page";
        }

        private static void Collect(DependencyObject node, System.Collections.Generic.List<string> texts)
        {
            if (node is TextBlock block && block.IsVisible && !string.IsNullOrWhiteSpace(block.Text)) texts.Add(block.Text);
            if (node is Button button && button.IsVisible && button.Content is string label) texts.Add("[" + label + "]");
            for (var i = 0; i < System.Windows.Media.VisualTreeHelper.GetChildrenCount(node); i++)
                Collect(System.Windows.Media.VisualTreeHelper.GetChild(node, i), texts);
        }

        internal bool Working => working;

        // ---- For looking at it ------------------------------------------------------------

        /// <summary>Puts the card in [state] with made-up values, for a picture of it; nothing is installed.</summary>
        internal void Preview(string state)
        {
            VersionText.Text = "Version 1.53.0";
            installDir = @"C:\Program Files\Lucense";
            LocationText.Text = installDir;
            switch (state)
            {
                case "install":
                    Show(WelcomePage);
                    break;
                case "update":
                    WelcomeTitle.Text = "Update Lucense";
                    InstallButton.Content = "Update";
                    WelcomeText.Text = "Lucense 1.53.0 takes the place of 1.52.0. Your library, playlists, ratings and play counts stay just as they are.";
                    RunningNote.Visibility = Visibility.Visible;
                    Show(WelcomePage);
                    break;
                case "progress":
                    BeginWork("Updating Lucense");
                    Bar.Value = 0.62;
                    PercentText.Text = "62%";
                    StatusText.Text = "Copying Lucense and its tools…";
                    break;
                case "done":
                    DoneTitle.Text = "Lucense is updated";
                    DoneText.Text = "Everything you had is where you left it.";
                    Show(DonePage);
                    break;
                case "error":
                    log = Path.GetTempFileName();
                    ShowError("That didn't work", Explain(1618), retry: true);
                    break;
                case "installed":
                    InstalledTitle.Text = "Lucense 1.53.0 is installed";
                    InstalledText.Text = InstalledNote;
                    Show(InstalledPage);
                    break;
            }
            // Pictures are taken at once: no sliding in.
            foreach (var page in new FrameworkElement[] { WelcomePage, ProgressPage, DonePage, ErrorPage, InstalledPage })
            {
                page.BeginAnimation(OpacityProperty, null);
                page.Opacity = 1;
                page.RenderTransform = null;
            }
        }
    }

    internal static class Strings
    {
        public static string NullIfBlank(this string text) => string.IsNullOrWhiteSpace(text) ? null : text;
    }
}
