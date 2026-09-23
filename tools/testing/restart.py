"""Stop the running dev client (if any), relaunch it via gradle, and wait for the title screen."""
import subprocess, time, os
from mar import call

MOD = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))  # repo root = Gradle project
try:
    call("POST", "/world/leave", {}, timeout=60)
    call("POST", "/stop", {}, timeout=10)
    time.sleep(12)
except Exception:
    pass
log = open(os.path.join(MOD, "run_client.log"), "w")
subprocess.Popen(["cmd", "/c", os.path.join(MOD, "gradlew.bat"), "runClient"], cwd=MOD, stdout=log, stderr=subprocess.STDOUT,
                 creationflags=0x00000008)  # DETACHED_PROCESS
for i in range(200):
    try:
        s = call("GET", "/ping", timeout=5)
        if any(k in str(s.get("screen")) for k in ("Title", "Accessibility")):
            print("ready", s.get("screen"))
            break
    except Exception:
        pass
    time.sleep(3)
