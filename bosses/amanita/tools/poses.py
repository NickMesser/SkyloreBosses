"""Posed renders of Amanita (Blockbench must have the models open from build_one.py)."""
import boss_env  # noqa: F401
import mcp, lib
c = mcp.Client()
CAM, TGT = [-60, 48, -80], [0, 26, 0]
P = [("amanita", "idle", 1.0), ("amanita", "wilt", 0.5), ("amanita", "full_snuff_windup", 2.8), ("amanita", "deep_idle", 1.0),
     ("amanita", "lash_windup", 0.7), ("amanita", "slam_windup", 1.4), ("amanita", "death", 6.5)]
for model, anim, t in P:
    lib.pose(c, f"animation.{model}.{anim}", t)
    lib.render(c, model, [(f"pose_{anim}", CAM, TGT)])
    lib.edit_mode(c)
