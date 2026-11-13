// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "VideoDLApple",
    platforms: [.macOS(.v13)],
    products: [.executable(name: "VideoDLApple", targets: ["VideoDLApple"])],
    targets: [.executableTarget(name: "VideoDLApple", path: "Sources/VideoDLApple")]
)
