const $ = (id) => document.getElementById(id);
const form = $("parse-form");
const urlInput = $("video-url");
const parseBtn = $("parse-btn");
const loading = $("loading");
const result = $("result");
const errorBox = $("error");
const qualitySelect = $("quality");
let currentUrl = "";

function showError(message) {
  errorBox.textContent = message || "发生未知错误，请稍后重试。";
  errorBox.classList.remove("hidden");
}
function clearPanels() {
  errorBox.classList.add("hidden");
  result.classList.add("hidden");
}
function formatDuration(seconds) {
  if (!Number.isFinite(Number(seconds))) return "";
  const s = Math.max(0, Math.floor(Number(seconds)));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  return h ? `${h}:${String(m).padStart(2,"0")}:${String(sec).padStart(2,"0")}` : `${m}:${String(sec).padStart(2,"0")}`;
}

$("paste-btn").addEventListener("click", async () => {
  try {
    const text = await navigator.clipboard.readText();
    if (text) urlInput.value = text.trim();
    else urlInput.focus();
  } catch {
    urlInput.focus();
    showError("浏览器未授予剪贴板权限，请长按输入框并选择粘贴。");
  }
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  clearPanels();
  const url = urlInput.value.trim();
  if (!url) return showError("请先粘贴视频链接。");
  currentUrl = url;
  loading.classList.remove("hidden");
  parseBtn.disabled = true;
  parseBtn.querySelector("span").textContent = "解析中…";
  try {
    const response = await fetch("/api/info", {
      method: "POST",
      headers: {"Content-Type":"application/json"},
      body: JSON.stringify({url})
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || "解析失败。");
    $("video-title").textContent = data.title || "未命名视频";
    $("uploader").textContent = data.uploader || "公开视频";
    $("duration").textContent = formatDuration(data.duration);
    const image = $("thumbnail");
    image.referrerPolicy = "no-referrer";
    image.src = data.thumbnail || "";
    image.style.visibility = data.thumbnail ? "visible" : "hidden";
    qualitySelect.replaceChildren();
    (data.formats || [{id:"best",label:"最佳画质"}]).forEach((format) => {
      const option = document.createElement("option");
      option.value = format.id;
      option.textContent = format.label;
      qualitySelect.append(option);
    });
    result.classList.remove("hidden");
    result.scrollIntoView({behavior:"smooth",block:"nearest"});
  } catch (error) {
    showError(error.message);
  } finally {
    loading.classList.add("hidden");
    parseBtn.disabled = false;
    parseBtn.querySelector("span").textContent = "解析视频";
  }
});

$("download-btn").addEventListener("click", async () => {
  const button = $("download-btn");
  button.disabled = true;
  button.querySelector("span").textContent = "正在准备…";
  try {
    const response = await fetch("/api/download", {
      method: "POST",
      headers: {"Content-Type":"application/json"},
      body: JSON.stringify({url:currentUrl,quality:qualitySelect.value})
    });
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      throw new Error(data.error || "下载失败，请稍后重试。");
    }
    button.querySelector("span").textContent = "正在接收…";
    const blob = await response.blob();
    const disposition = response.headers.get("Content-Disposition") || "";
    const match = disposition.match(/filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i);
    const filename = match ? decodeURIComponent(match[1] || match[2]) : "videodl-video.mp4";
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = objectUrl;
    anchor.download = filename;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(objectUrl), 30000);
    button.querySelector("span").textContent = "已开始保存";
    setTimeout(() => { button.querySelector("span").textContent = "下载视频"; }, 2500);
  } catch (error) {
    showError(error.message);
    button.querySelector("span").textContent = "重试下载";
  } finally {
    button.disabled = false;
  }
});
