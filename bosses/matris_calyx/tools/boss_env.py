"""Import first in every Matris Calyx tool script: wires in the shared pipeline (tools/blockbench) and points it
at this boss (sound namespace + model output folder bosses/matris_calyx/models)."""
import os, sys

BOSS = "matris_calyx"
BOSS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
REPO = os.path.abspath(os.path.join(BOSS_DIR, "..", ".."))
MODELS = os.path.join(BOSS_DIR, "models")
sys.path.insert(0, os.path.join(REPO, "tools", "blockbench"))

import lib  # noqa: E402

lib.BOSS_ID = BOSS
lib.OUT = MODELS
