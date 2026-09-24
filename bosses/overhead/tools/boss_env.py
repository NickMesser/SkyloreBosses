"""Import first in every Overhead tool script: wires in the shared pipeline (tools/blockbench), points it at this
boss (sound namespace + model output folder bosses/overhead/models) and selects the industrial material palette."""
import os, sys

BOSS = "overhead"
BOSS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
REPO = os.path.abspath(os.path.join(BOSS_DIR, "..", ".."))
MODELS = os.path.join(BOSS_DIR, "models")
sys.path.insert(0, os.path.join(REPO, "tools", "blockbench"))

import lib  # noqa: E402
import texture  # noqa: E402

lib.BOSS_ID = BOSS
lib.OUT = MODELS
texture.use("industrial")
