"""Posed renders of the Overhead models (Blockbench must have them open from build_one.py)."""
import boss_env  # noqa: F401
import mcp, lib
c = mcp.Client()
P = [("overhead", "missile_open", 1.2, [-110, 70, -130], [0, 25, 0]),
     ("overhead", "brownout", 0.5, [-110, 60, -130], [0, 25, 0]),
     ("overhead", "death", 6.0, [-110, 60, -130], [0, 25, 0]),
     ("generator_pylon", "break", 1.5, [-90, 70, -110], [0, 40, 0]),
     ("generator_pylon", "overcharge", 1.7, [-90, 70, -110], [0, 40, 0])]
for model, anim, t, pos, tgt in P:
    lib.pose(c, f"animation.{model}.{anim}", t)
    lib.render(c, model, [(f"pose_{anim}", pos, tgt)])
    lib.edit_mode(c)
