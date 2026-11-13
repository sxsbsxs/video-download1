using System.Net.Http;
using System.Net.Http.Json;
using System.Text.Json;
using Avalonia.Controls;
using Avalonia.Markup.Xaml;
using Avalonia.Platform.Storage;

namespace VideoDL.Desktop;

public partial class MainWindow : Window
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromMinutes(30) };
    private TextBox Input(string name) => this.FindControl<TextBox>(name)!;
    private TextBlock Label(string name) => this.FindControl<TextBlock>(name)!;

    public MainWindow() => InitializeComponent();

    private void InitializeComponent() => AvaloniaXamlLoader.Load(this);

    private string ApiBase()
    {
        var value = Input("ApiBaseBox").Text?.Trim().TrimEnd('/') ?? "";
        if (!Uri.TryCreate(value, UriKind.Absolute, out var uri) ||
            (uri.Scheme != Uri.UriSchemeHttps && !uri.IsLoopback && uri.Host != "localhost"))
        {
            throw new InvalidOperationException("请输入 HTTPS 引擎地址；本地测试可使用 localhost。");
        }
        return value;
    }

    private string VideoUrl()
    {
        var value = Input("VideoUrlBox").Text?.Trim() ?? "";
        if (!Uri.TryCreate(value, UriKind.Absolute, out var uri) ||
            (uri.Scheme != Uri.UriSchemeHttps && uri.Scheme != Uri.UriSchemeHttp))
        {
            throw new InvalidOperationException("请输入有效的视频 URL。");
        }
        return value;
    }

    private async void Info_Click(object? sender, Avalonia.Interactivity.RoutedEventArgs e)
    {
        try
        {
            Label("StatusText").Text = "正在解析…";
            using var response = await Http.PostAsJsonAsync(ApiBase() + "/api/info", new { url = VideoUrl() });
            var body = await response.Content.ReadAsStringAsync();
            EnsureSuccess(response, body);
            using var json = JsonDocument.Parse(body);
            var root = json.RootElement;
            Label("TitleText").Text = root.TryGetProperty("title", out var title) ? title.GetString() ?? "未命名视频" : "未命名视频";
            Label("UploaderText").Text = root.TryGetProperty("uploader", out var uploader) ? uploader.GetString() ?? "" : "";
            var labels = root.TryGetProperty("formats", out var formats)
                ? formats.EnumerateArray().Select(f => f.TryGetProperty("label", out var label) ? label.GetString() : null)
                    .Where(x => !string.IsNullOrWhiteSpace(x))
                : Enumerable.Empty<string?>();
            Label("FormatsText").Text = "可用画质：" + string.Join("、", labels);
            Label("StatusText").Text = "解析完成。";
        }
        catch (Exception ex)
        {
            Label("StatusText").Text = ex.Message;
        }
    }

    private async void Download_Click(object? sender, Avalonia.Interactivity.RoutedEventArgs e)
    {
        try
        {
            var quality = string.IsNullOrWhiteSpace(Input("QualityBox").Text) ? "best" : Input("QualityBox").Text.Trim();
            Label("StatusText").Text = "正在请求下载…";
            using var request = new HttpRequestMessage(HttpMethod.Post, ApiBase() + "/api/download")
            {
                Content = JsonContent.Create(new { url = VideoUrl(), quality })
            };
            using var response = await Http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead);
            if (!response.IsSuccessStatusCode)
            {
                var body = await response.Content.ReadAsStringAsync();
                EnsureSuccess(response, body);
            }

            var extension = quality == "audio" ? ".mp3" : ".mp4";
            var file = await StorageProvider.SaveFilePickerAsync(new FilePickerSaveOptions
            {
                Title = "保存 VideoDL 文件",
                SuggestedFileName = "VideoDL" + extension
            });
            if (file is null)
            {
                Label("StatusText").Text = "已取消保存。";
                return;
            }

            await using var source = await response.Content.ReadAsStreamAsync();
            await using var target = await file.OpenWriteAsync();
            await source.CopyToAsync(target);
            Label("StatusText").Text = "下载完成：" + file.Name;
        }
        catch (Exception ex)
        {
            Label("StatusText").Text = ex.Message;
        }
    }

    private static void EnsureSuccess(HttpResponseMessage response, string body)
    {
        if (response.IsSuccessStatusCode) return;
        string? message = null;
        try
        {
            using var json = JsonDocument.Parse(body);
            if (json.RootElement.TryGetProperty("error", out var error)) message = error.GetString();
        }
        catch (JsonException) { }
        throw new InvalidOperationException(message ?? "引擎请求失败：HTTP " + (int)response.StatusCode);
    }
}
