"""Tiny Marionette HTTP client for in-game testing of the mod.
    python tools/mar.py ping
    python tools/mar.py cmd "skylorecalyx status"
    python tools/mar.py shot name
    python tools/mar.py POST /world/create '{"type":"void"}'"""
import json, os, sys, time, urllib.request

BASE = "http://127.0.0.1:" + os.environ.get("MARIONETTE_PORT", "25585")


def call(method, path, body=None, timeout=300):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method, headers={"content-type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return {"http_error": e.code, "body": e.read().decode()[:2000]}


def unpause():
    try:
        p = call("GET", "/ping", timeout=10)
        if p.get("screen") and "PauseScreen" in p["screen"]:
            call("POST", "/screen/close", {})
    except Exception:
        pass


def wait(ticks):
    """Wait in small steps, closing the pause menu if the window loses focus."""
    left = ticks
    while left > 0:
        unpause()
        n = min(20, left)
        call("POST", "/wait", {"ticks": n})
        left -= n


def cmd(c, origin=None):
    unpause()
    b = {"command": c}
    if origin:
        b["origin"] = origin
    return call("POST", "/server_command", b)


def wait_ready(timeout=600):
    t0 = time.time()
    while time.time() - t0 < timeout:
        try:
            return call("GET", "/ping", timeout=5)
        except Exception:
            time.sleep(3)
    raise TimeoutError("marionette not reachable")


if __name__ == "__main__":
    a = sys.argv[1:]
    if a[0] == "ping":
        print(json.dumps(wait_ready()))
    elif a[0] == "cmd":
        print(json.dumps(cmd(a[1]), indent=1))
    elif a[0] == "shot":
        print(json.dumps(call("POST", "/screenshot", {"name": a[1]})))
    elif a[0] == "wait":
        print(json.dumps(call("POST", "/wait", {"ticks": int(a[1])})))
    else:
        print(json.dumps(call(a[0], a[1], json.loads(a[2]) if len(a) > 2 else None), indent=1))
