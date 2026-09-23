"""Shared Blockbench pipeline for all Skylore bosses: model spec -> Blockbench JS builder + exporter (driven through
the Blockbench MCP), shared Snowstorm particle library, renders. Configure BOSS_ID/OUT via a boss's tools/boss_env.py."""
import json, math, os, random
import texture

# Set by each boss's tools/boss_env.py before building:
BOSS_ID = "boss"   # namespace of sound keyframes ("<boss>:<sound>" -> skylore_bosses:<boss>.<sound>)
OUT = None         # folder that receives one subfolder per model (bosses/<boss>/models)
PARTICLE_NS = "skylore_bosses"  # shared particle library
FACES = ("north", "south", "east", "west", "up", "down")


class Model:
    def __init__(self, name, seed=1):
        self.name = name
        self.groups = []
        self.cubes = []
        self.locators = []
        self.anims = []
        self.particles = {}
        self.rng = random.Random(seed)
        self._g = set()

    # ---------- geometry ----------
    def group(self, name, pivot=(0, 0, 0), parent=None, rot=(0, 0, 0)):
        assert name not in self._g, name
        self._g.add(name)
        self.groups.append(dict(name=name, origin=list(pivot), parent=parent, rotation=list(rot)))
        return name

    def cube(self, g, frm, to, mat="flesh", rot=(0, 0, 0), pivot=None, inflate=0, name=None):
        frm, to = list(map(float, frm)), list(map(float, to))
        for i in range(3):
            if frm[i] > to[i]:
                frm[i], to[i] = to[i], frm[i]
        if pivot is None:
            pivot = [(a + b) / 2 for a, b in zip(frm, to)]
        self.cubes.append(dict(group=g, name=name or f"{g}_{mat if isinstance(mat, str) else 'mix'}_{len(self.cubes)}",
                               frm=frm, to=to,
                               mat=mat if isinstance(mat, dict) else {f: mat for f in FACES},
                               rot=[round(x, 2) for x in rot], pivot=list(pivot), inflate=inflate))

    def box(self, g, center, size, mat="flesh", **kw):
        c, s = center, size
        self.cube(g, [c[i] - s[i] / 2 for i in range(3)], [c[i] + s[i] / 2 for i in range(3)], mat, **kw)

    def locator(self, g, name, pos):
        self.locators.append(dict(group=g, name=name, pos=list(pos)))

    # ---------- detail helpers ----------
    def blob(self, g, center, r, mat="flesh", n=5, jitter=0.35, rot_j=18):
        """Lumpy organic mass from overlapping rotated cubes."""
        R = self.rng
        self.box(g, center, [r * 1.5] * 3, mat)
        for _ in range(n):
            off = [R.uniform(-r, r) * jitter * 2 for _ in range(3)]
            s = [r * R.uniform(0.7, 1.3) for _ in range(3)]
            self.box(g, [center[i] + off[i] for i in range(3)], s, mat,
                     rot=[R.uniform(-rot_j, rot_j) for _ in range(3)])

    def ring(self, g, center, radius, count, size, mat, axis="y", phase=0, tilt=0):
        """Ring of cubes. axis y: horizontal ring. axis z: ring in XY plane (faces north)."""
        cx, cy, cz = center
        for i in range(count):
            a = phase + 2 * math.pi * i / count
            if axis == "y":
                p = [cx + math.cos(a) * radius, cy, cz + math.sin(a) * radius]
                rot = [0, -math.degrees(a), tilt]
            else:
                p = [cx + math.cos(a) * radius, cy + math.sin(a) * radius, cz]
                rot = [tilt * math.sin(a), -tilt * math.cos(a), math.degrees(a) - 90]
            self.box(g, p, size, mat, rot=rot, pivot=p)

    def veins(self, g, start, direction, length, count=3, mat="vein", thick=1.2):
        R = self.rng
        for _ in range(count):
            p = list(start)
            d = list(direction)
            for _ in range(int(length // 4)):
                d = [d[i] + R.uniform(-0.35, 0.35) for i in range(3)]
                nrm = math.sqrt(sum(x * x for x in d)) or 1
                d = [x / nrm for x in d]
                q = [p[i] + d[i] * 4 for i in range(3)]
                c = [(p[i] + q[i]) / 2 for i in range(3)]
                self.box(g, c, [thick + abs(d[i]) * 3.2 for i in range(3)], mat, inflate=0.1)
                p = q

    def pustules(self, g, center, spread, count, mat="bile", size=(2.5, 4)):
        R = self.rng
        for _ in range(count):
            s = R.uniform(*size)
            p = [center[i] + R.uniform(-spread[i], spread[i]) for i in range(3)]
            self.box(g, p, [s, s, s], mat, rot=[R.uniform(0, 45) for _ in range(3)])

    def tentacle(self, parent, base, segs, length, r0, r1, name, mat="flesh", mat2="flesh_dark",
                 plates=None, suckers=None, rest_curl=(0, 0, 0), rest=None):
        """Vertical bone chain (grows +Y). Each segment: core, two muscle rings, optional back plate and
        front suckers. Returns (bones, tip_position)."""
        bones = []
        seg_len = length / segs
        pos = list(base)
        for i in range(segs):
            t = i / max(1, segs - 1)
            r = r0 + (r1 - r0) * t
            b = self.group(f"{name}_{i}", pos, parent, rot=[rest[i], 0, 0] if rest else (list(rest_curl) if i else [0, 0, 0]))
            c = [pos[0], pos[1] + seg_len / 2, pos[2]]
            self.box(b, c, [r * 2, seg_len + 1, r * 2], mat)
            for j in range(2):
                self.box(b, [pos[0], pos[1] + seg_len * (0.3 + j * 0.45), pos[2]], [r * 2 + 1.2, 1.6, r * 2 + 1.2], mat2)
            if plates:
                self.box(b, [c[0], c[1], c[2] + r + 0.3], [r * 1.6, seg_len * 0.85, 1.4], plates, rot=[-8, 0, 0])
                self.box(b, [c[0], pos[1] + seg_len * 0.8, c[2] + r + 1.0], [1.2, 2.4, 1.6], "bone", rot=[35, 0, 0])
            if suckers:
                for j in range(2):
                    self.box(b, [c[0], pos[1] + seg_len * (0.25 + 0.5 * j), c[2] - r - 0.2],
                             [r * 0.9, r * 0.9, 1.0], suckers)
            bones.append(b)
            parent = b
            pos = [pos[0], pos[1] + seg_len, pos[2]]
        return bones, pos

    # ---------- animation ----------
    def anim(self, name, length, loop="loop"):
        a = dict(name=f"animation.{self.name}.{name}", length=length, loop=loop, bones={}, particles=[], sounds=[])
        self.anims.append(a)
        return a

    @staticmethod
    def key(a, bone, channel, t, v, interp="catmullrom"):
        if isinstance(v, (int, float)):
            v = [v, v, v]
        lst = a["bones"].setdefault(bone, [])
        t = round(t, 2)
        lst[:] = [k for k in lst if not (k["channel"] == channel and k["time"] == t)]
        lst.append(dict(channel=channel, time=t, v=[round(x, 3) for x in v], interp=interp))

    def keys(self, a, bone, channel, seq, interp="catmullrom"):
        for t, v in seq:
            self.key(a, bone, channel, t, v, interp)

    def wave(self, a, bones, channel, amp, period, length, phase_step=0.12, axis=(1, 0, 0), samples=None, base=None):
        """Travelling sine wave down a bone chain; loops seamlessly when length is a multiple of period."""
        samples = samples or max(4, int(round(length / period * 6)))
        for i, b in enumerate(bones):
            for s in range(samples + 1):
                t = length * s / samples
                v = amp * math.sin(2 * math.pi * (t / period - i * phase_step))
                bv = base[i] if base else (0, 0, 0)
                self.key(a, b, channel, t, [bv[k] + v * axis[k] for k in range(3)])

    def fx(self, a, t, effect, locator):
        ts = {p["time"] for p in a["particles"]}
        t = round(t, 2)
        while t in ts:
            t = round(t + 0.01, 2)
        a["particles"].append(dict(time=t, effect=effect, locator=locator))

    def sfx(self, a, t, sound):
        ts = {p["time"] for p in a["sounds"]}
        t = round(t, 2)
        while t in ts:
            t = round(t + 0.01, 2)
        a["sounds"].append(dict(time=t, effect=f"{BOSS_ID}:{sound}"))

    # ---------- JS ----------
    def js(self):
        atlas, glow = texture.build()
        self._atlas, self._glow = atlas, glow
        R = random.Random(99)
        cubes = []
        for c in self.cubes:
            faces = {}
            sx, sy, sz = [c["to"][i] - c["frm"][i] for i in range(3)]
            dims = {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy),
                    "up": (sx, sz), "down": (sx, sz)}
            for f in FACES:
                ox, oy = texture.tile_origin(c["mat"][f])
                w = max(1, min(62, round(dims[f][0]))); h = max(1, min(62, round(dims[f][1])))
                u = ox + 1 + R.randint(0, 62 - w); v = oy + 1 + R.randint(0, 62 - h)
                faces[f] = [u, v, u + w, v + h]
            cubes.append([c["group"], c["name"], c["frm"], c["to"], c["pivot"], c["rot"], c["inflate"], faces])
        payload = dict(name=self.name, groups=self.groups, cubes=cubes, locators=self.locators, anims=self.anims,
                       tex=texture.data_url(atlas), particles={k: [getattr(self, "_ppaths", {}).get(k, k + ".json"), json.dumps(v)] for k, v in self.particles.items()})
        code = BUILD_JS.replace("__DATA__", json.dumps(payload))
        while "//" in code:
            code = code.replace("//", "/\\/")
        return code

    def build(self, client):
        d = os.path.join(OUT, self.name)
        pdir = os.path.abspath(os.path.join(d, "particles"))
        os.makedirs(pdir, exist_ok=True)
        self._ppaths = {}
        for k, v in self.particles.items():
            p = os.path.join(pdir, f"{k}.json")
            with open(p, "w") as f:
                json.dump(v, f, indent=2)
            self._ppaths[k] = p.replace("\\", "/")
        r = client.eval(self.js())
        print(self.name, "->", r)
        raw = json.loads(client.eval(EXPORT_JS))
        exp = json.loads(raw) if isinstance(raw, str) else raw
        with open(os.path.join(d, f"{self.name}.bbmodel"), "w") as f:
            f.write(exp["project"])
        with open(os.path.join(d, f"{self.name}.geo.json"), "w") as f:
            f.write(exp["geo"])
        with open(os.path.join(d, f"{self.name}.animation.json"), "w") as f:
            json.dump(exp["anim"], f, indent=2)
        self._atlas.save(os.path.join(d, f"{self.name}.png"))
        self._glow.save(os.path.join(d, f"{self.name}_glowmask.png"))
        return r


BUILD_JS = r"""
(function(){
var D = __DATA__;
newProject(Formats.animated_entity_model);
Project.name = D.name; Project.geometry_name = D.name;
Project.box_uv = false; Project.texture_width = 256; Project.texture_height = 256;
var tex = new Texture({name: D.name + '.png'}).fromDataURL(D.tex).add(false);
var G = {};
D.groups.forEach(function(g){
  var grp = new Group({name: g.name, origin: g.origin, rotation: g.rotation});
  grp.addTo(g.parent ? G[g.parent] : undefined); grp.init(); G[g.name] = grp;
});
D.cubes.forEach(function(c){
  var faces = {};
  Object.keys(c[7]).forEach(function(f){ faces[f] = {uv: c[7][f], texture: tex.uuid}; });
  var cu = new Cube({name: c[1], from: c[2], to: c[3], origin: c[4], rotation: c[5], inflate: c[6], box_uv: false, faces: faces});
  cu.addTo(G[c[0]]); cu.init();
});
D.locators.forEach(function(l){
  var lo = new Locator({name: l.name, position: l.pos}); lo.addTo(G[l.group]); lo.init();
});
Object.keys(D.particles).forEach(function(k){
  Animator.loadParticleEmitter(D.particles[k][0], D.particles[k][1]);
});
D.anims.forEach(function(a){
  var an = new Animation({name: a.name, length: a.length, loop: a.loop, snapping: 100}).add(false);
  Object.keys(a.bones).forEach(function(b){
    var ba = an.getBoneAnimator(G[b]);
    a.bones[b].forEach(function(k){
      ba.addKeyframe({channel: k.channel, time: k.time, interpolation: k.interp,
        data_points: [{x: k.v[0], y: k.v[1], z: k.v[2]}]});
    });
  });
  if (a.particles.length || a.sounds.length) {
    var ef = an.animators.effects || (an.animators.effects = new EffectAnimator(an));
    a.particles.forEach(function(p){
      ef.addKeyframe({channel: 'particle', time: p.time, data_points: [{effect: p.effect, locator: p.locator, script: '', file: D.particles[p.effect] ? D.particles[p.effect][0] : ''}]});
    });
    a.sounds.forEach(function(s){
      ef.addKeyframe({channel: 'sound', time: s.time, data_points: [{effect: s.effect, file: ''}]});
    });
  }
});
Canvas.updateAll();
return {groups: Group.all.length, cubes: Cube.all.length, locators: Locator.all.length, anims: Animation.all.length};
})()
"""

EXPORT_JS = r"""
(function(){
var geo = Codecs.bedrock.compile();
return JSON.stringify({
  project: Codecs.project.compile({editor_state: false}),
  geo: typeof geo === 'string' ? geo : JSON.stringify(geo, null, 2),
  anim: Animator.buildFile(null, true)
});
})()
"""


# ---------- particles (Snowstorm, vanilla particle sheet) ----------
# vanilla textures/particle/particles.png (128x128) rows: 0 = generic smoke puffs (8 frames),
# 1 = splash/drip, 2 = bubble/crit..., we reference a few safe cells.
def _hex(c):
    return "#%02X%02X%02X%02X" % tuple(int(x * 255) for x in (c[3], c[0], c[1], c[2]))


def particle(ident, *, rate=None, burst=None, lifetime=1.0, max_particles=100, speed=2.0, direction="outwards",
             shape="sphere", radius=0.5, gravity=0.0, drag=0.0, size=(0.2, 0.2), size_end=None, color=(1, 1, 1, 1),
             color_end=None, uv=(0, 0), frames=1, blend="blend", duration=1.0, box=None, cone_dir=None, spin=0,
             facing="rotate_xyz"):
    comps = {}
    if rate:
        comps["minecraft:emitter_rate_steady"] = {"spawn_rate": rate, "max_particles": max_particles}
    else:
        comps["minecraft:emitter_rate_instant"] = {"num_particles": burst or 20}
    comps["minecraft:emitter_lifetime_once"] = {"active_time": duration}
    if shape == "sphere":
        comps["minecraft:emitter_shape_sphere"] = {"radius": radius, "direction": direction}
    elif shape == "box":
        comps["minecraft:emitter_shape_box"] = {"half_dimensions": box or [radius] * 3, "direction": direction}
    elif shape == "point":
        comps["minecraft:emitter_shape_point"] = {"direction": cone_dir or [0, 1, 0]}
    elif shape == "disc":
        comps["minecraft:emitter_shape_disc"] = {"radius": radius, "plane_normal": "y", "direction": direction}
    comps["minecraft:particle_lifetime_expression"] = {"max_lifetime": f"{lifetime} * (0.7 + variable.particle_random_1 * 0.6)"}
    comps["minecraft:particle_initial_speed"] = f"{speed} * (0.5 + variable.particle_random_2)"
    if spin:
        comps["minecraft:particle_initial_spin"] = {"rotation": "variable.particle_random_3 * 360", "rotation_rate": spin}
    comps["minecraft:particle_motion_dynamic"] = {"linear_acceleration": [0, -gravity, 0], "linear_drag_coefficient": drag}
    se = size_end or size
    life = "variable.particle_age / variable.particle_lifetime"
    uvc = ({"texture_width": 128, "texture_height": 128,
            "flipbook": {"base_UV": list(uv), "size_UV": [8, 8], "step_UV": [8, 0],
                         "max_frame": frames, "stretch_to_lifetime": True}} if frames > 1 else
           {"texture_width": 128, "texture_height": 128, "uv": list(uv), "uv_size": [8, 8]})
    comps["minecraft:particle_appearance_billboard"] = {
        "size": [f"math.lerp({size[0]}, {se[0]}, {life})", f"math.lerp({size[1]}, {se[1]}, {life})"],
        "facing_camera_mode": facing, "uv": uvc}
    ce = color_end or color
    comps["minecraft:particle_appearance_tinting"] = {"color": {"interpolant": life,
                                                                "gradient": {"0.0": _hex(color), "1.0": _hex(ce)}}}
    return {"format_version": "1.10.0", "particle_effect": {
        "description": {"identifier": f"{PARTICLE_NS}:{ident}", "basic_render_parameters": {
            "material": "particles_blend" if blend == "blend" else "particles_add",
            "texture": "textures/particle/particles"}},
        "components": comps}}


# Shared particle library: every model registers the subset it uses.
P = {
    "bile_drip": particle("bile_drip", rate=6, duration=1, lifetime=1.2, speed=0.2, direction="outwards", radius=0.3,
                          gravity=9, size=(0.12, 0.2), color=(0.6, 0.85, 0.15, 1), color_end=(0.35, 0.55, 0.05, 0.8),
                          uv=(0, 56)),
    "bile_splash": particle("bile_splash", burst=40, duration=0.1, lifetime=0.8, speed=6, radius=0.4, gravity=14,
                            drag=0.8, size=(0.25, 0.25), size_end=(0.08, 0.08), color=(0.72, 0.95, 0.2, 1),
                            color_end=(0.3, 0.45, 0.05, 0.6), uv=(0, 56)),
    "spore_puff": particle("spore_puff", burst=24, duration=0.1, lifetime=2.4, speed=2.5, direction=[0, 1, 0],
                           radius=0.6, gravity=-0.4, drag=1.2, size=(0.3, 0.3), size_end=(0.9, 0.9),
                           color=(0.75, 0.66, 0.45, 0.9), color_end=(0.45, 0.4, 0.3, 0), uv=(56, 0), frames=1),
    "spore_haze": particle("spore_haze", rate=10, duration=2, lifetime=3, speed=0.4, radius=1.5, gravity=-0.15,
                           drag=0.5, size=(0.4, 0.4), size_end=(1.2, 1.2), color=(0.7, 0.62, 0.42, 0.55),
                           color_end=(0.5, 0.45, 0.35, 0), uv=(0, 0), frames=8),
    "nerve_spark": particle("nerve_spark", rate=18, duration=1, lifetime=0.35, speed=5, radius=0.5, drag=3,
                            size=(0.14, 0.14), size_end=(0.02, 0.02), color=(0.7, 0.95, 1, 1),
                            color_end=(0.2, 0.5, 1, 0.6), uv=(8, 88), blend="add", facing="lookat_direction"),
    "nerve_pulse": particle("nerve_pulse", burst=60, duration=0.1, lifetime=0.6, speed=9, radius=0.3, drag=4,
                            size=(0.18, 0.18), size_end=(0.02, 0.02), color=(0.8, 1, 1, 1),
                            color_end=(0.3, 0.6, 1, 0), uv=(8, 88), blend="add", facing="lookat_direction"),
    "core_glow": particle("core_glow", rate=14, duration=1, lifetime=0.9, speed=0.6, radius=0.6, gravity=-1,
                          size=(0.25, 0.25), size_end=(0.05, 0.05), color=(1, 0.55, 0.75, 1),
                          color_end=(1, 0.2, 0.5, 0), uv=(0, 88), blend="add"),
    "blood_burst": particle("blood_burst", burst=50, duration=0.1, lifetime=1.1, speed=7, radius=0.6, gravity=16,
                            drag=0.6, size=(0.3, 0.3), size_end=(0.1, 0.1), color=(0.55, 0.05, 0.12, 1),
                            color_end=(0.25, 0.02, 0.05, 0.7), uv=(0, 56)),
    "flesh_chunks": particle("flesh_chunks", burst=30, duration=0.1, lifetime=1.8, speed=8, radius=1, gravity=18,
                             drag=0.3, size=(0.35, 0.35), color=(0.6, 0.25, 0.28, 1), color_end=(0.35, 0.12, 0.16, 1),
                             uv=(0, 16), spin=360),
    "root_dust": particle("root_dust", burst=40, duration=0.1, lifetime=1.5, speed=4, shape="disc", radius=2,
                          gravity=1, drag=2.5, size=(0.4, 0.4), size_end=(1, 1), color=(0.45, 0.32, 0.26, 0.9),
                          color_end=(0.3, 0.22, 0.2, 0), uv=(0, 0), frames=8),
    "laser_charge": particle("laser_charge", rate=60, duration=2, lifetime=0.6, speed=-6, direction="outwards",
                             radius=4, size=(0.2, 0.2), size_end=(0.05, 0.05), color=(1, 0.8, 0.3, 0.2),
                             color_end=(1, 0.95, 0.6, 1), uv=(8, 88), blend="add", facing="lookat_direction"),
    "laser_beam": particle("laser_beam", rate=120, duration=3, lifetime=0.4, speed=40, shape="point",
                           cone_dir=[0, 0, -1], size=(0.6, 0.6), size_end=(0.2, 0.2), color=(1, 0.9, 0.5, 1),
                           color_end=(1, 0.4, 0.1, 0), uv=(0, 88), blend="add", max_particles=300),
    "eye_glint": particle("eye_glint", rate=4, duration=1, lifetime=0.8, speed=0.2, radius=0.4, size=(0.3, 0.3),
                          size_end=(0, 0), color=(1, 0.95, 0.7, 1), color_end=(1, 0.7, 0.3, 0), uv=(0, 88),
                          blend="add"),
    "mucus_string": particle("mucus_string", rate=5, duration=1, lifetime=1.6, speed=0.1, radius=0.8, gravity=3,
                             drag=2, size=(0.06, 0.35), color=(0.85, 0.75, 0.7, 0.9), color_end=(0.7, 0.6, 0.55, 0),
                             uv=(0, 56)),
}


def render(client, name, shots, size=(900, 900)):
    """shots: list of (label, camera_position, target). Saves PNGs into <model>/renders/."""
    import base64
    d = os.path.join(OUT, name, "renders")
    os.makedirs(d, exist_ok=True)
    try:
        client.call("delete_offscreen_view", {"view": "mc_render"})
    except Exception:
        pass
    client.call("create_offscreen_view", {"id": "mc_render", "width": size[0], "height": size[1], "copy_view": "none"})
    for label, pos, target in shots:
        client.call("set_camera_angle", {"view": "mc_render", "position": pos, "target": target, "projection": "perspective", "fov": 45})
        _, res = client.call("capture_screenshot", {"view": "mc_render"})
        for c in res.get("content", []):
            if c.get("type") == "image":
                with open(os.path.join(d, f"{label}.png"), "wb") as f:
                    f.write(base64.b64decode(c["data"]))
    client.call("delete_offscreen_view", {"view": "mc_render"})


def select_project(client, name):
    client.eval("(function(){var p=ModelProject.all.filter(function(x){return x.name==%s}).pop();if(p)p.select();return !!p})()" % json.dumps(name))


def pose(client, anim, t):
    """Scrub the named animation to time t for a posed render."""
    select_project(client, anim.split(".")[1])
    client.eval("(function(){var a=Animation.all.find(function(x){return x.name==%s});if(!a)return 0;"
                "Modes.options.animate.select();a.select();Timeline.setTime(%s);Animator.preview();Animator.preview();return 1})()"
                % (json.dumps(anim), t))


def edit_mode(client):
    client.eval("(function(){Modes.options.edit.select();return 1})()")
