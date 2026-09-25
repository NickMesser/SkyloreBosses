"""Build (and render) named Amanita models through the Blockbench MCP.
    python build_one.py amanita lamp_eater hollow_spawn hollow_bolt"""
import boss_env  # noqa: F401
import sys, mcp, lib, models
c = mcp.Client()
fn = {f.__name__: f for f in models.ALL}
for name in sys.argv[1:]:
    m = fn[name]()
    m.build(c)
    h = max(cu["to"][1] for cu in m.cubes)
    w = max(max(abs(cu["to"][0]), abs(cu["frm"][0]), abs(cu["to"][2]), abs(cu["frm"][2])) for cu in m.cubes)
    d = max(h, w * 2) * 1.75
    tgt = [0, h * 0.5, 0]
    lib.edit_mode(c)
    lib.render(c, m.name, [("front34", [-d * 0.6, h * 0.7, -d * 0.9], tgt), ("side", [d, h * 0.5, 0], tgt)])
