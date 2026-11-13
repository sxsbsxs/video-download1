package main

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"time"
)

type videoFormat struct {
	ID    string
	Label string
}

type videoInfo struct {
	Title     string
	Thumbnail string
	Duration  int
	Uploader  string
	Formats   []videoFormat
}

func main() {
	if len(os.Args) < 4 {
		usage()
		os.Exit(2)
	}
	base := strings.TrimRight(os.Args[1], "/")
	action, videoURL := os.Args[2], os.Args[3]
	quality := "best"
	if len(os.Args) > 4 {
		quality = os.Args[4]
	}
	client := &http.Client{Timeout: 30 * time.Minute}

	switch action {
	case "info":
		payload, _ := json.Marshal(map[string]string{"url": videoURL})
		resp, err := client.Post(base+"/api/info", "application/json", strings.NewReader(string(payload)))
		check(err)
		defer resp.Body.Close()
		if resp.StatusCode < 200 || resp.StatusCode >= 300 {
			fail(readError(resp.Body))
		}
		var info videoInfo
		check(json.NewDecoder(resp.Body).Decode(&info))
		fmt.Printf("标题：%s\n上传者：%s\n时长：%d 秒\n", info.Title, info.Uploader, info.Duration)
		for _, f := range info.Formats {
			fmt.Printf("画质：%s (%s)\n", f.Label, f.ID)
		}
	case "download":
		if len(os.Args) < 6 {
			usage()
			os.Exit(2)
		}
		outputPath := os.Args[5]
		out, err := os.Create(outputPath)
		check(err)
		defer out.Close()
		payload, _ := json.Marshal(map[string]string{"url": videoURL, "quality": quality})
		resp, err := client.Post(base+"/api/download", "application/json", strings.NewReader(string(payload)))
		check(err)
		defer resp.Body.Close()
		if resp.StatusCode < 200 || resp.StatusCode >= 300 {
			_ = os.Remove(outputPath)
			fail(readError(resp.Body))
		}
		n, err := io.Copy(out, resp.Body)
		check(err)
		fmt.Printf("已保存 %s（%d 字节）\n", outputPath, n)
	default:
		usage()
		os.Exit(2)
	}
}

func readError(r io.Reader) string {
	b, _ := io.ReadAll(io.LimitReader(r, 4096))
	return string(b)
}

func check(err error) {
	if err != nil {
		fail(err.Error())
	}
}

func fail(message string) {
	fmt.Fprintln(os.Stderr, "VideoDL:", message)
	os.Exit(1)
}

func usage() {
	fmt.Fprintln(os.Stderr, "用法：videodl-go <api-base> <info|download> <video-url> [quality] [output-file]")
}
