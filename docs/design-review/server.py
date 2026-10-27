#!/usr/bin/env python3
"""Tiny local backend: serves the design-review dashboard and persists comments to comments.json."""
import json, os, http.server, socketserver
from urllib.parse import urlparse

PORT = 8731
ROOT = os.path.dirname(os.path.abspath(__file__))
COMMENTS_PATH = os.path.join(ROOT, "comments.json")

def load_comments():
    if os.path.exists(COMMENTS_PATH):
        with open(COMMENTS_PATH) as f:
            return json.load(f)
    return []

def save_comments(data):
    with open(COMMENTS_PATH, "w") as f:
        json.dump(data, f, indent=2)

class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ROOT, **kwargs)

    def _send_json(self, obj, code=200):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/api/comments":
            return self._send_json(load_comments())
        return super().do_GET()

    def do_POST(self):
        path = urlparse(self.path).path
        if path == "/api/comments":
            length = int(self.headers.get("Content-Length", 0))
            body = json.loads(self.rfile.read(length))
            comments = load_comments()
            comments.append(body)
            save_comments(comments)
            return self._send_json({"ok": True, "count": len(comments)})
        if path == "/api/comments/clear":
            save_comments([])
            return self._send_json({"ok": True})
        self.send_response(404)
        self.end_headers()

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def log_message(self, fmt, *args):
        pass  # quiet

if __name__ == "__main__":
    if not os.path.exists(COMMENTS_PATH):
        save_comments([])
    with socketserver.TCPServer(("127.0.0.1", PORT), Handler) as httpd:
        print(f"Design review dashboard: http://127.0.0.1:{PORT}/dashboard.html")
        httpd.serve_forever()
