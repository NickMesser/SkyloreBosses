# Boss: Null Router, the Unacked (Act IV, Automaton network)

| | |
|---|---|
| Boss id | `null_router` |
| Design | [DESIGN.md](DESIGN.md): all 17 deliverables, per-phase action catalogs, ack/misroute card, wet math, implementation order |
| Java module | [`src/main/java/.../bosses/nullrouter/`](../../src/main/java/net/teamaof/skylorebosses/bosses/nullrouter/NullRouterBoss.java) |
| Assets in the mod | `src/main/resources/assets/skylore_bosses/{geo,animations,textures,sounds}/.../null_router/` |
| In-game test | [`tools/testing/t_null_router.py`](../../tools/testing/t_null_router.py) (100 checks) |

An orphaned Automaton routing process still answering tickets for a guild that left. The boss bar is its unacked request queue. Set the three channel consoles to the glyphs on the bright lamp row and the chassis stops being a hologram: it docks on its pad for a short ACK window in which damage clears requests. Retry packets walk to consoles you matched and flip them back. In P2 a stale decoy request sits on the dim row and matching it misroutes you into a service alcove. From P3 a dry chassis barely registers damage, so bring water to the pad during the window (the coolant vents fill four basins) and keep it off the floor between windows, where it shorts you. Stall and the retry storm comes. The queue at zero is the win.

## Folder contents

```
bosses/null_router/
├─ DESIGN.md
├─ models/     Blockbench sources, one folder per model (.bbmodel, .geo.json, .animation.json,
│              texture + _glowmask, particles/*.json, renders/ incl. posed and in-game sheets)
├─ textures/
│  ├─ codex_raw/   raw Codex CLI image generations (kept so reruns only fill gaps)
│  └─ src/         processed game-size textures: blocks, the trophy icon, 16 entity material swatches
└─ tools/
   ├─ boss_env.py          import first: shared pipeline, this boss, automaton palette + Codex swatches
   ├─ codex_textures.py    generate textures with the Codex CLI's image tool, then downscale/key/quantise
   ├─ models.py            model specs: null_router, retry_packet
   ├─ build_all.py, build_one.py, poses.py   build and render in Blockbench via the MCP
   ├─ export_to_mod.py     copies models into the mod; generates RouterLocators/RouterSoundIds.java, particle
   │                       descriptions, block/item textures (console faces and lamp modes derived from the lamp
   │                       glyphs), synthesized sounds
   └─ gen_data.py          lang, blockstates, block/item models, loot, damage types, advancements
```

## Models

| Model | What | Animations |
|---|---|---|
| `null_router` | a router rack: white clean-room front, cyan status lens in a ring of amber LEDs, a three-glyph ticket screen, port pods with cable bundles, rear fins, an antenna crown, four orbiting drive sleds, a hover skirt and four docking clamps. The renderer draws it as a translucent cyan hologram while it is a ghost and fully solid while docked | idle, solid, solid_warn; dock, release, boot, chime, ping, lance_windup, lance, burst_windup, burst, flick, ttl_windup, ttl, misroute, storm_windup, storm, hurt, death |
| `retry_packet` | a glowing amber data packet in a dark frame with a circling retry-loop ring | idle, move, uplink |

## Rebuild

Blockbench open with the MCP plugin (`http://127.0.0.1:3000/bb-mcp`); the Codex CLI logged in for textures.

```bash
cd bosses/null_router/tools
python codex_textures.py   # only generates textures that are missing (pass names to redo them)
python build_all.py        # rebuild every model and render it
python export_to_mod.py    # copy into the mod (add --no-sounds to keep the existing OGGs)
python gen_data.py         # regenerate data and lang entries
```

## Try it

```
/skylorenullrouter build          # test vault 30 blocks above you
/skylorenullrouter tp             # entry pad; walking in starts the fight
/skylorenullrouter status
/skylorenullrouter hold true      # freeze the attack bag and the pressure clocks while you look around
```
