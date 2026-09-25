"""Import first in every Null Router tool script: wires in the shared pipeline (tools/blockbench), points it at this
boss (sound namespace + model output folder bosses/null_router/models) and selects the automaton material palette,
with the Codex-painted swatches in textures/src/ replacing the procedural tiles."""
import os, sys

BOSS = "null_router"
BOSS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
REPO = os.path.abspath(os.path.join(BOSS_DIR, "..", ".."))
MODELS = os.path.join(BOSS_DIR, "models")
TEXTURE_SRC = os.path.join(BOSS_DIR, "textures", "src")
sys.path.insert(0, os.path.join(REPO, "tools", "blockbench"))

import lib  # noqa: E402
import texture  # noqa: E402

lib.BOSS_ID = BOSS
lib.OUT = MODELS
texture.use("automaton", TEXTURE_SRC)
