import AppKit
import SwiftUI

struct FormatOption: Decodable, Identifiable {
    let id: String
    let label: String
}

struct VideoInfo: Decodable {
    let title: String
    let thumbnail: String?
    let duration: Double?
    let uploader: String
    let formats: [FormatOption]
}

struct VideoDLService {
    let baseURL: String

    private func endpoint(_ path: String) throws -> URL {
        guard let base = URL(string: baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/"))) else {
            throw NSError(domain: "VideoDL", code: 1, userInfo: [NSLocalizedDescriptionKey: "引擎地址无效。"])
        }
        let local = base.scheme == "http" && (base.host == "127.0.0.1" || base.host == "localhost")
        guard base.scheme == "https" || local else {
            throw NSError(domain: "VideoDL", code: 2, userInfo: [NSLocalizedDescriptionKey: "远程引擎地址必须使用 HTTPS。"])
        }
        return base.appending(path: path)
    }

    private func post(path: String, body: [String: String]) async throws -> (Data, HTTPURLResponse) {
        var request = URLRequest(url: try endpoint(path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        request.timeoutInterval = 1800

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw NSError(domain: "VideoDL", code: 3, userInfo: [NSLocalizedDescriptionKey: "引擎没有返回有效的 HTTP 响应。"])
        }
        return (data, http)
    }

    func info(url: String) async throws -> VideoInfo {
        let (data, response) = try await post(path: "/api/info", body: ["url": url])
        guard (200..<300).contains(response.statusCode) else {
            throw apiError(data, status: response.statusCode)
        }
        return try JSONDecoder().decode(VideoInfo.self, from: data)
    }

    @MainActor
    func download(url: String, quality: String) async throws -> URL {
        let endpoint = try endpoint("/api/download")
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["url": url, "quality": quality])
        request.timeoutInterval = 1800

        let (temporaryURL, response) = try await URLSession.shared.download(for: request)
        guard let http = response as? HTTPURLResponse else {
            try? FileManager.default.removeItem(at: temporaryURL)
            throw NSError(domain: "VideoDL", code: 4, userInfo: [NSLocalizedDescriptionKey: "引擎没有返回有效的 HTTP 响应。"])
        }
        guard (200..<300).contains(http.statusCode) else {
            let data = try Data(contentsOf: temporaryURL)
            try? FileManager.default.removeItem(at: temporaryURL)
            throw apiError(data, status: http.statusCode)
        }

        let panel = NSSavePanel()
        panel.nameFieldStringValue = quality == "audio" ? "VideoDL-audio.mp3" : "VideoDL-video.mp4"
        panel.canCreateDirectories = true
        guard panel.runModal() == .OK, let destination = panel.url else {
            try? FileManager.default.removeItem(at: temporaryURL)
            throw NSError(domain: "VideoDL", code: 5, userInfo: [NSLocalizedDescriptionKey: "用户取消了保存。"])
        }
        if FileManager.default.fileExists(atPath: destination.path) {
            try FileManager.default.removeItem(at: destination)
        }
        try FileManager.default.moveItem(at: temporaryURL, to: destination)
        return destination
    }

    private func apiError(_ data: Data, status: Int) -> Error {
        let message = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["error"] as? String
        return NSError(domain: "VideoDL", code: status, userInfo: [
            NSLocalizedDescriptionKey: message ?? "引擎请求失败：HTTP \(status)"
        ])
    }
}

@MainActor
final class VideoDLModel: ObservableObject {
    @Published var apiBase = "https://your-engine.example"
    @Published var videoURL = ""
    @Published var quality = "best"
    @Published var videoInfo: VideoInfo?
    @Published var status = "填写引擎地址和公开视频链接。"
    @Published var busy = false

    func resolve() async {
        busy = true
        defer { busy = false }
        do {
            videoInfo = try await VideoDLService(baseURL: apiBase).info(url: videoURL)
            status = "解析完成。"
        } catch {
            status = error.localizedDescription
        }
    }

    func download() async {
        busy = true
        defer { busy = false }
        do {
            let path = try await VideoDLService(baseURL: apiBase).download(url: videoURL, quality: quality)
            status = "已保存到：\(path.path)"
        } catch {
            status = error.localizedDescription
        }
    }
}

struct ContentView: View {
    @StateObject private var model = VideoDLModel()

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("VideoDL").font(.largeTitle.bold())
            Text("视频下载应用").foregroundStyle(.secondary)
            TextField("引擎 API 地址（HTTPS）", text: $model.apiBase)
                .textFieldStyle(.roundedBorder)
            TextField("粘贴公开视频链接", text: $model.videoURL)
                .textFieldStyle(.roundedBorder)
            Picker("画质", selection: $model.quality) {
                Text("最佳画质").tag("best")
                Text("1080P").tag("1080")
                Text("720P").tag("720")
                Text("音频 MP3").tag("audio")
            }
            .pickerStyle(.segmented)
            HStack {
                Button("解析视频") { Task { await model.resolve() } }
                    .disabled(model.busy || model.videoURL.isEmpty)
                Button("下载文件") { Task { await model.download() } }
                    .disabled(model.busy || model.videoURL.isEmpty || model.videoInfo == nil)
            }
            if let info = model.videoInfo {
                if let thumbnail = info.thumbnail, let url = URL(string: thumbnail) {
                    AsyncImage(url: url) { image in
                        image.resizable().scaledToFit()
                    } placeholder: {
                        ProgressView()
                    }
                    .frame(maxHeight: 190)
                }
                Text(info.title).font(.headline)
                Text(info.uploader).foregroundStyle(.secondary)
                Text("可用画质：" + info.formats.map(\.label).joined(separator: "、"))
                    .font(.callout)
            }
            Text(model.status).font(.callout).foregroundStyle(.secondary)
            Spacer()
        }
        .padding(24)
        .frame(minWidth: 620, minHeight: 520)
    }
}

@main
struct VideoDLAppleApp: App {
    var body: some Scene {
        WindowGroup { ContentView() }
    }
}
