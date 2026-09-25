"""Import first in every Amanita tool script: wires in the shared pipeline (tools/blockbench), points it at this
boss (sound namespace + model output folder bosses/amanita/models) and selects the hollow (fungal-shadow) material palette,
with the Codex-painted swatches in textures/src/ replacing the procedural tiles."""
import os, sys

BOSS = "amanita"
BOSS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
REPO = os.path.abspath(os.path.join(BOSS_DIR, "..", ".."))
MODELS = os.path.join(BOSS_DIR, "models")
TEXTURE_SRC = os.path.join(BOSS_DIR, "textures", "src")
sys.path.insert(0, os.path.join(REPO, "tools", "blockbench"))

import lib  # noqa: E402
import texture  # noqa: E402

lib.BOSS_ID = BOSS
lib.OUT = MODELS
texture.use("hollow", TEXTURE_SRC)
