import boss_env  # noqa: F401
import mcp, lib
c = mcp.Client()
P = [("calyx_bloom", "laser_charge", 2.0, [-120, 150, -230], [0, 100, 0]),
     ("calyx_bloom", "emerge", 1.0, [-120, 120, -230], [0, 60, 0]),
     ("calyx_bloom", "death", 8.0, [-120, 120, -230], [0, 60, 0]),
     ("slam_arm", "telegraph", 2.2, [140, 90, 0], [0, 60, 0]),
     ("slam_arm", "open_window", 1.5, [-40, 40, -90], [0, 25, 0]),
     ("charging_arm", "open_window", 1.5, [-60, 70, 60], [0, 25, 0]),
     ("mouth_arm", "telegraph", 1.0, [-40, 110, -110], [0, 80, 0]),
     ("spitting_arm", "telegraph", 1.2, [110, 90, -40], [0, 70, 0]),
     ("nerve_arm", "pulse", 0.65, [-70, 100, -110], [0, 70, 0]),
     ("spore_vent", "spawn_add", 1.2, [-40, 50, -60], [0, 20, 0])]
for model, anim, t, pos, tgt in P:
    lib.pose(c, f"animation.{model}.{anim}", t)
    lib.render(c, model, [(f"pose_{anim}", pos, tgt)])
    lib.edit_mode(c)
