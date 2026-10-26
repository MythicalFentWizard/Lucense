using System;
using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace LucenseSetup
{
    public partial class App : Application
    {
        protected override void OnStartup(StartupEventArgs e)
        {
            base.OnStartup(e);
            ShutdownMode = ShutdownMode.OnMainWindowClose;

            // --snapshot <folder>: pictures of every page, for looking at the design; nothing is installed.
            if (e.Args.Length >= 2 && e.Args[0] == "--snapshot")
            {
                Snapshot(e.Args[1]);
                Shutdown();
                return;
            }

            // --payload <file>: carry what another file carries, for trying the installer out.
            var carrier = e.Args.Length >= 2 && e.Args[0] == "--payload"
                ? e.Args[1]
                : Process.GetCurrentProcess().MainModule.FileName;
            Payload payload = null;
            try { payload = Payload.Read(carrier); } catch { /* shown as nothing to install */ }

            var view = new SetupView();
            var offscreen = Has(e.Args, "--offscreen");
            var window = new Window
            {
                Title = "Lucense Setup",
                Content = view,
                WindowStyle = WindowStyle.None,
                AllowsTransparency = true,
                Background = Brushes.Transparent,
                ResizeMode = ResizeMode.CanMinimize,
                SizeToContent = SizeToContent.WidthAndHeight,
                WindowStartupLocation = WindowStartupLocation.CenterScreen,
                Icon = BitmapFrame.Create(new Uri("pack://application:,,,/Assets/lucense-256.png")),
                ShowInTaskbar = true
            };
            view.Host = window;
            MainWindow = window;
            if (offscreen)
            {
                window.WindowStartupLocation = WindowStartupLocation.Manual;
                window.Left = -10000;
                window.Top = -10000;
                window.ShowInTaskbar = false;
            }
            window.Show();
            view.Start(payload);
            Trial(e.Args, view, window);
        }

        private static bool Has(string[] args, string flag) => Array.IndexOf(args, flag) >= 0;

        private static string After(string[] args, string flag)
        {
            var at = Array.IndexOf(args, flag);
            return at >= 0 && at + 1 < args.Length ? args[at + 1] : null;
        }

        /// <summary>
        /// --auto [--remove] [--dir folder] [--report file]: presses on by itself
        /// and writes down where it ended up, then closes. For trying the
        /// installer out with a package of its own; nothing a person passes.
        /// </summary>
        private static async void Trial(string[] args, SetupView view, Window window)
        {
            if (!Has(args, "--auto")) return;
            var dir = After(args, "--dir");
            if (dir != null) view.UseFolder(dir);
            var report = After(args, "--report");
            var seen = new System.Collections.Generic.List<string> { view.Describe() };
            view.PressForTrial(Has(args, "--remove"));
            var until = DateTime.UtcNow.AddMinutes(5);
            while (DateTime.UtcNow < until)
            {
                await System.Threading.Tasks.Task.Delay(250);
                var now = view.Describe();
                if (now != seen[seen.Count - 1]) seen.Add(now);
                if (!view.Working && !now.StartsWith("ProgressPage") && seen.Count > 1) break;
            }
            if (report != null) File.WriteAllLines(report, seen);
            window.Close();
        }

        /// <summary>Every page as a PNG in [folder], at twice the size, as a high-resolution screen shows it.</summary>
        private static void Snapshot(string folder)
        {
            Directory.CreateDirectory(folder);
            foreach (var state in new[] { "install", "update", "progress", "done", "error", "installed" })
            {
                var view = new SetupView();
                view.Preview(state);
                var size = new Size(view.Width, view.Height);
                view.Measure(size);
                view.Arrange(new Rect(size));
                view.UpdateLayout();
                var bitmap = new RenderTargetBitmap((int)(size.Width * 2), (int)(size.Height * 2), 192, 192, PixelFormats.Pbgra32);
                // A desktop behind it, so the shadow and rounded corners show as they would.
                var backdrop = new DrawingVisual();
                using (var context = backdrop.RenderOpen())
                {
                    context.DrawRectangle(new SolidColorBrush(Color.FromRgb(0x2B, 0x3A, 0x55)), null, new Rect(size));
                }
                bitmap.Render(backdrop);
                bitmap.Render(view);
                var encoder = new PngBitmapEncoder();
                encoder.Frames.Add(BitmapFrame.Create(bitmap));
                using (var file = File.Create(Path.Combine(folder, state + ".png")))
                {
                    encoder.Save(file);
                }
            }
        }
    }
}
