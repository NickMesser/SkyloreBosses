# Boss: The Static Deacon (Act III, Church of Ender)

| | |
|---|---|
| Boss id | `static_deacon` |
| Design | [DESIGN.md](DESIGN.md): all 17 deliverables, per-phase attack catalogs, implementation order |
| Java module | [`src/main/java/.../bosses/staticdeacon/`](../../src/main/java/net/teamaof/skylorebosses/bosses/staticdeacon/StaticDeaconBoss.java) |
| Assets in the mod | `src/main/resources/assets/skylore_bosses/{geo,animations,textures,sounds}/.../static_deacon/` |
| In-game test | [`tools/testing/t_static_deacon.py`](../../tools/testing/t_static_deacon.py) (75 checks) |

A Beryl caretaker under a Church of Ender altar. While it stands on a live consecrated flagstone it is in communion with the whole nave: up to 50% damage reduction and 4% max HP/s regen, scaled by how much of the floor is still live. Break, cover or blow up one block of a flagstone and the whole 3x3 stone dies. Strip all 48 and it kneels on the unbreakable altar plinth (vigil); knock it off with burst damage and kill it before the reseed rite regrows the floor.

## Folder contents

```
bosses/static_deacon/
├─ DESIGN.md
├─ models/     Blockbench sources, one folder per model (.bbmodel, .geo.json, .animation.json,
│              texture + _glowmask, particles/*.json, renders/ incl. posed and in-game sheets)
├─ textures/
│  ├─ codex_raw/   raw Codex CLI image generations (kept so reruns only fill gaps)
│  └─ src/         processed game-size textures: 13 blocks, 1 item, 16 entity material swatches
└─ tools/
   ├─ boss_env.py          import first: shared pipeline, this boss, liturgical palette + Codex swatches
   ├─ codex_textures.py    generate textures with the Codex CLI's image tool, then downscale/key/quantise
   ├─ models.py            model specs: static_deacon, static_bolt, homing_shard
   ├─ build_all.py, build_one.py, poses.py   build and render in Blockbench via the MCP
   ├─ export_to_mod.py     copies models into the mod; generates DeaconLocators/DeaconSoundIds.java,
   │                       particle descriptions, block/item textures, synthesized sounds
   └─ gen_data.py          lang, blockstates, block/item models, loot, tags, advancements
```

## Models

| Model | What | Animations |
|---|---|---|
| `static_deacon` | alb, purple dalmatic with gold clavi, the diagonal deacon's stole, faceted crystal head in a peaked cowl, halo of Beryl shards, a crystal-lattice arm, a censer on a chain | idle, walk, commune, vigil, reseed, stagger, beam, static_step; vesting, chime, pulse_windup, pulse, lash_windup, lash, bolt_cast, shatter_windup, shatter, shard_cast, pull_windup, pull, beam_windup, reseed_begin, hurt, knockoff, step_out, step_in, death |
| `static_bolt`, `homing_shard` | projectiles | fly |

## Rebuild

Blockbench open with the MCP plugin (`http://127.0.0.1:3000/bb-mcp`); the Codex CLI logged in for textures.

```bash
cd bosses/static_deacon/tools
python codex_textures.py   # only generates textures that are missing (pass names to redo them)
python build_all.py        # rebuild every model and render it
python export_to_mod.py    # copy into the mod (add --no-sounds to keep the existing OGGs)
python gen_data.py         # regenerate data and lang entries
```

## Try it

```
/skyloredeacon build          # test undercroft 30 blocks above you
/skyloredeacon tp             # entry pad; walking in starts the fight
/skyloredeacon status
```
