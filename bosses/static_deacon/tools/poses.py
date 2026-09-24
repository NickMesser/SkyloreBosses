"""Posed renders of the Static Deacon (Blockbench must have the models open from build_one.py)."""
import boss_env  # noqa: F401
import mcp, lib
c = mcp.Client()
CAM, TGT = [-70, 50, -95], [0, 28, 0]
P = [("static_deacon", "vigil", 1.0), ("static_deacon", "lash_windup", 0.8), ("static_deacon", "beam", 0.5),
     ("static_deacon", "shatter_windup", 1.4), ("static_deacon", "reseed", 0.6), ("static_deacon", "death", 6.5)]
for model, anim, t in P:
    lib.pose(c, f"animation.{model}.{anim}", t)
    lib.render(c, model, [(f"pose_{anim}", CAM, TGT)])
    lib.edit_mode(c)
