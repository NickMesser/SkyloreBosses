# Boss: Matris Calyx, the Parasite Mother (Act V finale)

| | |
|---|---|
| Boss id | `matris_calyx` |
| Design | [DESIGN.md](DESIGN.md): all 17 deliverables and the implementation order |
| Java module | [`src/main/java/.../bosses/matriscalyx/`](../../src/main/java/net/teamaof/skylorebosses/bosses/matriscalyx/MatrisCalyxBoss.java) |
| Assets in the mod | `src/main/resources/assets/skylore_bosses/{geo,animations,textures,sounds}/.../matris_calyx/` |

## Folder contents

```
bosses/matris_calyx/
├─ DESIGN.md
├─ models/     Blockbench sources, one folder per model (.bbmodel, .geo.json, .animation.json,
│              texture + _glowmask, particles/*.json, renders/)
└─ tools/      model definitions and generators for this boss
   ├─ boss_env.py         import first: points the shared pipeline (tools/blockbench) at this boss
   ├─ arms.py, bloom.py, props.py, adds.py   the model specs
   ├─ build_all.py, build_one.py, poses.py   build and render in Blockbench via the MCP
   ├─ export_to_mod.py    copies the models into the mod; generates MatrisLocators/MatrisSoundIds.java,
   │                      particle sprites, block/item textures and synthesized sounds
   ├─ gen_data.py         lang, block/item models, loot, recipe, tags, advancements, Proto-World
   └─ sound_ids.txt       the boss's sound list (written by export_to_mod.py)
```

## Models

| Model | What | Animations |
|---|---|---|
| `calyx_bloom` | Phase 3 organ: split-heart lobes, stalk, petal crown, lidded eye | idle, emerge, laser_charge, laser_fire, retina_flash, stagger, death |
| `grasping_arm` | Hooked tentacle with a three-finger claw | idle, telegraph, grab, yank + shared |
| `slam_arm` | Thick trunk with a bone-knuckle fist | idle, telegraph, slam + shared |
| `charging_arm` | Rooted crawler with a ram skull and umbilical tail | idle, walk, telegraph, charge, impact + shared |
| `mouth_arm` | Lamprey maw with lips and tooth rings | idle, telegraph, bite, feed + shared |
| `spitting_arm` | Bile sac with a nozzle | idle, telegraph, volley + shared |
| `nerve_arm` | Braided nerve trunk with a ganglion | idle, pulse + shared |
| `spore_vent` | Breakable add spawner (block entity) | idle, puff, spawn_add, hurt, broken |
| `bile_glob` | Projectile | fly, burst |
| `spore_thrall`, `spore_mite`, `infected_drifter` | Adds | idle/walk, attack, swell, tether, hurt, death, emerge |

"Shared" on every arm means `open_window`, `window_warn`, `hurt`, `death` and `emerge`.

## Rebuild

Open Blockbench with the MCP plugin (`http://127.0.0.1:3000/bb-mcp`), then:

```bash
cd bosses/matris_calyx/tools
python build_all.py        # rebuild every model and render it in Blockbench
python export_to_mod.py    # copy into the mod (add --no-sounds to keep the existing OGGs)
python gen_data.py         # regenerate this boss's data and lang entries
```
