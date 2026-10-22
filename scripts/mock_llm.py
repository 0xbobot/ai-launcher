#!/usr/bin/env python3
"""冒烟专用 mock LLM（意图改进二批第 17 段）：OpenAI 兼容 /chat/completions 假端点。

对任何请求都回同一个结构化结果：capability=daohang、params 为空——
用来在 CI 里把协调器逼进「缺必填参数」追问态（真端点/真 Key 不进 CI）。
请求体原文逐行追加写进日志文件，冒烟脚本据此断言 hints（带参数说明）
与上下文包确实出现在发往模型的 prompt 里（端到端取证）。

用法：mock_llm.py <port> <request-log-path>
仅标准库；只监听本机，冒烟结束由脚本 kill。
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 18741
LOG_PATH = sys.argv[2] if len(sys.argv) > 2 else "mock_llm_requests.log"

INNER = json.dumps(
    {"action": {"capability_id": "daohang", "params": {}, "confirm_text": ""}},
    ensure_ascii=False,
)
BODY = json.dumps(
    {
        "id": "chatcmpl-mock",
        "object": "chat.completion",
        "choices": [
            {
                "index": 0,
                "message": {"role": "assistant", "content": INNER},
                "finish_reason": "stop",
            }
        ],
        "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2},
    },
    ensure_ascii=False,
).encode("utf-8")


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):  # 静音默认访问日志
        pass

    def do_GET(self):
        if self.path == "/health":
            data = b"ok"
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length > 0 else b""
        try:
            with open(LOG_PATH, "a", encoding="utf-8") as f:
                f.write(raw.decode("utf-8", errors="replace") + "\n")
        except OSError:
            pass
        # v0.68.98 第 22 段（Planning 取消）专用：请求体含标记词 manmanxiang 时
        # 延迟再回（仍在协调器 15 秒超时内）——把协调器稳定停在 Planning
        # 态，等冒烟点完「取消」后，这份迟到结果（daohang 空参 -> 本应进
        # NeedsInput 追问）若仍上屏即断言失败。第 17 段的输入不含标记词，
        # 行为不变。sleep 在请求线程内，ThreadingHTTPServer 不阻塞其他请求。
        # v0.68.99（R-1）：延迟 10 -> 13 秒——R-1 后进输入态要多走点七仔格 +
        # 轮询 dump，10 秒余量被吃光，三轮实测回包与点取消同秒竞态（迟到
        # 结果抢先落成追问态）；13 秒仍在 15 秒超时内，留出稳定取消窗口。
        if b"manmanxiang" in raw:
            import time

            time.sleep(13)
        if self.path.rstrip("/").endswith("chat/completions"):
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(BODY)))
            self.end_headers()
            self.wfile.write(BODY)
        else:
            self.send_response(404)
            self.end_headers()


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
