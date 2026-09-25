# Boss: Amanita, the Hollow Bloom (Act I-II, Tenebris nemesis)

| | |
|---|---|
| Boss id | `amanita` |
| Design | [DESIGN.md](DESIGN.md): all 18 deliverables, per-phase action catalogs, light-gate/snuff card, damage math, origin asymmetry, implementation order |
| Java module | [`src/main/java/.../bosses/amanita/`](../../src/main/java/net/teamaof/skylorebosses/bosses/amanita/AmanitaBoss.java) |
| Assets in the mod | `src/main/resources/assets/skylore_bosses/{geo,animations,textures,sounds}/.../amanita/` |
| In-game test | [`tools/testing/t_amanita.py`](../../tools/testing/t_amanita.py) (85 checks) |

A fungal-shadow warlord in a sealed, unlit hollow. While the block light at her is under 7 she takes no damage at all. Bring light to her: torches, lanterns, Spectrum lamps, Ars light glyphs, or the six stocked braziers in the room. She keeps to dark tiles, snuffs lights around her, dashes at the brightest light, and her lamp-eaters crawl to your torches and eat them. At half HP she closes the flower and every light in the hollow goes out; relight three sources under pressure from her hollow-spawn. Leave her in the dark too long and she deep-blooms: she needs light 10, regrows, and ends the bloom with another full snuff unless you force her open. She can only die lit.

## Folder contents

```
bosses/amanita/
├─ DESIGN.md
├─ models/     Blockbench sources, one folder per model (.bbmodel, .geo.json, .animation.json,
│              texture + _glowmask, particles/*.json, renders/ incl. posed renders)
├─ textures/
│  ├─ codex_raw/   raw Codex CLI image generations (kept so reruns only fill gaps)
│  ├─ src/         processed game-size textures: blocks, the trophy icon, 16 entity material swatches
│  └─ contact_sheet.png
└─ tools/
   ├─ boss_env.py          import first: shared pipeline, this boss, the 'hollow' palette + Codex swatches
   ├─ codex_textures.py    generate textures with the Codex CLI's image tool, then downscale/key/quantise
   ├─ models.py            model specs: amanita, lamp_eater, hollow_spawn, hollow_bolt
   ├─ build_all.py, build_one.py, poses.py   build and render in Blockbench via the MCP
   ├─ export_to_mod.py     copies models into the mod; generates AmanitaLocators/AmanitaSoundIds.java, particle
   │                       descriptions, block/item textures, synthesized sounds
   └─ gen_data.py          lang, blockstates, block/item models, loot, tags, advancements
```

## Models

| Model | What | Animations |
|---|---|---|
| `amanita` | a gaunt pale figure grown out of a torn volva cup: ghost-white stalk robe with a ragged ring, ribbed pallid chest with a spore core, hyphae shawl and back veil, long arms ending in tendril fingers, a hooded face with cold eye slits, and a bruised-violet cap of eight petal bones over glowing lilac gills. The petals flare open in the dark, droop when she is lit and shut when she snuffs | idle, wilt, walk, dash, deep_idle, stagger; rise, bloom_open, lash_windup, lash, bolt_cast, veil_cast, snuff_windup, snuff, full_snuff_windup, full_snuff, howl_windup, howl, slam_windup, slam, dash_windup, smother, deep_bloom, hurt, recoil, bared, death |
| `lamp_eater` | a pale segmented moth-grub with feathered antennae, folded wings, husk mandibles, a glowing lantern mouth and an ember belly | idle, walk, chew_loop, chew, nip, burrow, rise |
| `hollow_spawn` | a spore-husk thrall with shelf fungi growing from it and a small glowing-gilled cap | idle, walk, attack, rise, dissolve |
| `hollow_bolt` | a dark shard with a violet core and spore fins | fly |

## Rebuild

Blockbench open with the MCP plugin (`http://127.0.0.1:3000/bb-mcp`); the Codex CLI logged in for textures.

```bash
cd bosses/amanita/tools
python codex_textures.py   # only generates textures that are missing (pass names to redo them)
python build_all.py        # rebuild every model and render it
python export_to_mod.py    # copy into the mod (add --no-sounds to keep the existing OGGs)
python gen_data.py         # regenerate data and lang entries
```

## Try it

```
/skyloreamanita build          # test hollow 30 blocks above you
/skyloreamanita tp             # entry pad; walking in starts the fight
/skyloreamanita status         # phase, light at her, exposed, multiplier, census
/skyloreamanita hold true      # freeze her attack bag and the phase/add clocks while you look around
/skyloreamanita setlight 12    # pretend she stands in light 12 (-1 = real light again)
```
