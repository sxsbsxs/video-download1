import unittest

from videodl.errors import InvalidVideoURL
from videodl.url_input import extract_video_url


class ExtractVideoUrlTests(unittest.TestCase):
    def test_plain_https_url_is_unchanged(self):
        url = "https://www.bilibili.com/video/BV1xx411c7mD"
        self.assertEqual(extract_video_url(url), url)

    def test_extracts_url_from_chinese_share_text(self):
        text = (
            "朋友分享给你一个视频，快来看看！\n"
            "https://v.douyin.com/AbCdEf/ 复制打开抖音"
        )
        self.assertEqual(extract_video_url(text), "https://v.douyin.com/AbCdEf/")

    def test_extracts_markdown_link_and_html_escaped_ampersands(self):
        text = "推荐：[点这里观看](https://example.com/watch?id=7&amp;part=2)，谢谢！"
        self.assertEqual(
            extract_video_url(text),
            "https://example.com/watch?id=7&part=2",
        )

    def test_adds_https_to_scheme_less_domain(self):
        text = "复制这个链接：www.bilibili.com/video/BV1xx411c7mD。"
        self.assertEqual(
            extract_video_url(text),
            "https://www.bilibili.com/video/BV1xx411c7mD",
        )

    def test_protocol_relative_link(self):
        self.assertEqual(
            extract_video_url("打开看看 //example.com/video/123 。"),
            "https://example.com/video/123",
        )

    def test_repeated_same_url_is_not_treated_as_multiple(self):
        url = "https://example.com/video"
        self.assertEqual(
            extract_video_url(f"链接：{url}\n再次分享：{url}"),
            url,
        )

    def test_rejects_multiple_different_urls(self):
        with self.assertRaisesRegex(InvalidVideoURL, "多个链接"):
            extract_video_url(
                "视频一 https://example.com/one 视频二 https://example.com/two"
            )

    def test_rejects_text_without_a_link(self):
        with self.assertRaisesRegex(InvalidVideoURL, "没有识别到"):
            extract_video_url("这个视频挺好看的，快去搜索标题吧。")

    def test_rejects_oversized_pasted_text(self):
        with self.assertRaisesRegex(InvalidVideoURL, "过长"):
            extract_video_url("说明文字" * 3000)

    def test_does_not_extract_email_domain(self):
        with self.assertRaises(InvalidVideoURL):
            extract_video_url("联系 test@example.com 获取视频")


if __name__ == "__main__":
    unittest.main()
