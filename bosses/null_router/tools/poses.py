"""Posed renders of Null Router (Blockbench must have the models open from build_one.py)."""
import boss_env  # noqa: F401
import mcp, lib
c = mcp.Client()
CAM, TGT = [-60, 50, -80], [0, 26, 0]
P = [("null_router", "idle", 1.0), ("null_router", "solid", 0.5), ("null_router", "lance_windup", 1.3),
     ("null_router", "burst_windup", 1.3), ("null_router", "ttl_windup", 1.3), ("null_router", "death", 6.5)]
for model, anim, t in P:
    lib.pose(c, f"animation.{model}.{anim}", t)
    lib.render(c, model, [(f"pose_{anim}", CAM, TGT)])
    lib.edit_mode(c)
