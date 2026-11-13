use reqwest::blocking::{Client, Response};
use serde::Deserialize;
use serde_json::json;
use std::{env, error::Error, fs::File, io, time::Duration};

#[derive(Debug, Deserialize)]
struct Format {
    id: String,
    label: String,
}

#[derive(Debug, Deserialize)]
struct VideoInfo {
    title: String,
    #[serde(default)]
    uploader: String,
    #[serde(default)]
    duration: Option<u64>,
    #[serde(default)]
    formats: Vec<Format>,
}

fn main() {
    if let Err(error) = run() {
        eprintln!("VideoDL: {error}");
        std::process::exit(1);
    }
}

fn run() -> Result<(), Box<dyn Error>> {
    let args: Vec<String> = env::args().collect();
    if args.len() < 4 {
        return Err("用法：videodl-rust <api-base> <info|download> <video-url> [quality] [output-file]".into());
    }
    let base = args[1].trim_end_matches('/');
    let action = &args[2];
    let video_url = &args[3];
    let quality = args.get(4).map(String::as_str).unwrap_or("best");
    let client = Client::builder().timeout(Duration::from_secs(30 * 60)).build()?;

    match action.as_str() {
        "info" => {
            let response = client.post(format!("{base}/api/info"))
                .json(&json!({"url": video_url}))
                .send()?;
            let info: VideoInfo = ensure_success(response)?.json()?;
            println!("标题：{}\n上传者：{}\n时长：{:?} 秒", info.title, info.uploader, info.duration);
            for format in info.formats {
                println!("画质：{} ({})", format.label, format.id);
            }
        }
        "download" => {
            if args.len() < 6 {
                return Err("下载模式还需要指定输出文件名".into());
            }
            let mut response = ensure_success(
                client.post(format!("{base}/api/download"))
                    .json(&json!({"url": video_url, "quality": quality}))
                    .send()?
            )?;
            let mut output = File::create(&args[5])?;
            let bytes = io::copy(&mut response, &mut output)?;
            println!("已保存 {}（{} 字节）", args[5], bytes);
        }
        _ => return Err("动作只能是 info 或 download".into()),
    }
    Ok(())
}

fn ensure_success(response: Response) -> Result<Response, Box<dyn Error>> {
    if response.status().is_success() {
        Ok(response)
    } else {
        let status = response.status();
        let body = response.text().unwrap_or_default();
        Err(format!("HTTP {status}: {body}").into())
    }
}
