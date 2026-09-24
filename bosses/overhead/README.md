# Boss: Overhead, Noven's Decommissioned Prototype (Act II)

| | |
|---|---|
| Boss id | `overhead` |
| Design | [DESIGN.md](DESIGN.md): all 17 deliverables, per-phase attack catalogs, implementation order |
| Java module | [`src/main/java/.../bosses/overhead/`](../../src/main/java/net/teamaof/skylorebosses/bosses/overhead/OverheadBoss.java) |
| Assets in the mod | `src/main/resources/assets/skylore_bosses/{geo,animations,textures,sounds}/.../overhead/` |
| In-game test | [`tools/testing/t_overhead.py`](../../tools/testing/t_overhead.py) |

A hovering artillery chassis guarding a Teknari yard. Four corner generator pylons give it 90% damage reduction; each pylon you break drops it to the floor for a 10 s window; with all four down it is fully exposed until it runs an emergency re-arm.

## Folder contents

```
bosses/overhead/
├─ DESIGN.md
├─ models/     Blockbench sources, one folder per model (.bbmodel, .geo.json, .animation.json,
│              texture + _glowmask, particles/*.json, renders/)
└─ tools/
   ├─ boss_env.py         import first: points the shared pipeline at this boss, selects the industrial palette
   ├─ models.py           model specs: overhead, generator_pylon, howitzer_shell, seeker_missile
   ├─ build_all.py, build_one.py, poses.py   build and render in Blockbench via the MCP
   ├─ export_to_mod.py    copies models into the mod; generates OverheadLocators/OverheadSoundIds.java,
   │                      particle descriptions, block/item textures, synthesized sounds
   └─ gen_data.py         lang, blockstates, block/item models, loot, tags, advancements
```

## Models

| Model | What | Animations |
|---|---|---|
| `overhead` | chassis: hull, lens, turret + howitzer, nose gatling, missile pods with bay doors, flare rack, four ducted rotors | idle, brownout, laser_fire, strafe, power_on, power_pulse, howitzer_load, howitzer_fire, missile_open, missile_close, laser_charge, laser_vent, strafe_spinup, flare_fire, overcharge, carpet, shield_pulse, hurt, death |
| `generator_pylon` | block entity: transformer base, copper coil column, cage, energy core, lamps | online, rebuild, offline, hurt, overcharge, break |
| `howitzer_shell`, `seeker_missile` | ordnance | fly |

## Rebuild

Blockbench open with the MCP plugin (`http://127.0.0.1:3000/bb-mcp`). The GeckoLib Blockbench plugin is optional: without it the builder uses the Bedrock format, which GeckoLib reads directly.

```bash
cd bosses/overhead/tools
python build_all.py        # rebuild every model and render it
python export_to_mod.py    # copy into the mod (add --no-sounds to keep the existing OGGs)
python gen_data.py         # regenerate data and lang entries
```

## Try it

```
/skyloreoverhead build          # test yard 40 blocks above you
/skyloreoverhead tp             # entry pad; walking in starts the fight
/skyloreoverhead status
```
