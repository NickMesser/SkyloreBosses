"""Minimal streamable-HTTP MCP client for the Blockbench MCP plugin."""
import json, urllib.request

URL = "http://127.0.0.1:3000/bb-mcp"

class Client:
    def __init__(self):
        self.sid = None
        self.n = 0
        r = self._post({"jsonrpc": "2.0", "id": self._id(), "method": "initialize",
                        "params": {"protocolVersion": "2025-03-26", "capabilities": {},
                                   "clientInfo": {"name": "matris-build", "version": "1"}}})
        self._post({"jsonrpc": "2.0", "method": "notifications/initialized"})

    def _id(self):
        self.n += 1
        return self.n

    def _post(self, msg):
        h = {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"}
        if self.sid:
            h["mcp-session-id"] = self.sid
        req = urllib.request.Request(URL, json.dumps(msg).encode(), h)
        with urllib.request.urlopen(req, timeout=600) as resp:
            self.sid = resp.headers.get("mcp-session-id") or self.sid
            body = resp.read().decode("utf-8")
        if not body.strip():
            return None
        if body.lstrip().startswith("{"):
            return json.loads(body)
        out = None
        for line in body.splitlines():
            if line.startswith("data:"):
                d = json.loads(line[5:])
                if "id" in d:
                    out = d
        return out

    def call(self, name, args):
        r = self._post({"jsonrpc": "2.0", "id": self._id(), "method": "tools/call",
                        "params": {"name": name, "arguments": args}})
        if "error" in r:
            raise RuntimeError(r["error"])
        res = r["result"]
        texts = [c.get("text", "") for c in res.get("content", []) if c.get("type") == "text"]
        if res.get("isError"):
            raise RuntimeError("\n".join(texts))
        return "\n".join(texts), res

    def eval(self, code):
        assert "//" not in code and "/*" not in code and "console." not in code, "forbidden token in JS"
        t, _ = self.call("risky_eval", {"code": code})
        return t
