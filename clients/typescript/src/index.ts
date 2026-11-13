export interface VideoFormat {
  id: string;
  label: string;
}

export interface VideoInfo {
  title: string;
  thumbnail: string | null;
  duration: number | null;
  uploader: string;
  formats: VideoFormat[];
}

export interface VideoDLClientOptions {
  baseUrl: string;
  fetchImplementation?: typeof fetch;
}

/** Typed client for the VideoDL Python HTTP API. */
export class VideoDLClient {
  private readonly baseUrl: string;
  private readonly fetcher: typeof fetch;

  constructor(options: string | VideoDLClientOptions) {
    const normalized = typeof options === "string" ? { baseUrl: options } : options;
    if (!normalized.baseUrl.trim()) throw new Error("VideoDL base URL is required.");
    this.baseUrl = normalized.baseUrl.replace(/\/+$/, "");
    this.fetcher = normalized.fetchImplementation ?? fetch;
  }

  async health(): Promise<{ status: string; engine: string }> {
    const response = await this.fetcher(this.baseUrl + "/api/health");
    return this.readJson(response);
  }

  async info(url: string): Promise<VideoInfo> {
    const response = await this.fetcher(this.baseUrl + "/api/info", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ url }),
    });
    return this.readJson(response);
  }

  /** Returns Response so consumers can stream large files instead of buffering them. */
  async download(url: string, quality = "best"): Promise<Response> {
    const response = await this.fetcher(this.baseUrl + "/api/download", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ url, quality }),
    });
    if (!response.ok) {
      const body = await response.text();
      throw new Error(this.extractError(body) ?? "Download failed: HTTP " + response.status);
    }
    return response;
  }

  private async readJson<T>(response: Response): Promise<T> {
    const body = await response.text();
    if (!response.ok) {
      throw new Error(this.extractError(body) ?? "VideoDL request failed: HTTP " + response.status);
    }
    return JSON.parse(body) as T;
  }

  private extractError(body: string): string | undefined {
    try {
      const parsed: unknown = JSON.parse(body);
      if (typeof parsed === "object" && parsed !== null && "error" in parsed) {
        const message = (parsed as { error?: unknown }).error;
        return typeof message === "string" ? message : undefined;
      }
    } catch {
      // Use an HTTP fallback for non-JSON errors.
    }
    return undefined;
  }
}
