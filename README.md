# Skylore Bosses

The boss encounters of the Skylore modpack, as one NeoForge mod. This repository is the mod project: build it from here.

| | |
|---|---|
| Mod id | `skylore_bosses` (package `net.teamaof.skylorebosses`) |
| Minecraft / loader | 1.21.1, NeoForge 21.1.248+, Java 21 |
| Required | Architectury API 13.x, GeckoLib 4.7+ |
| Optional | Immortuos Calyx (used by Matris Calyx's infection clock) |
| Jar | `build/libs/skylore_bosses-0.1.0.jar` |

## Bosses

| Boss | Boss folder (design, models, tools) | Java module |
|---|---|---|
| **Matris Calyx**, the Parasite Mother (Act V finale) | [bosses/matris_calyx](bosses/matris_calyx/README.md) | `bosses/matriscalyx/` |
| **Overhead**, Noven's Decommissioned Prototype (Act II) | [bosses/overhead](bosses/overhead/README.md) | `bosses/overhead/` |

## Repository layout

```
SkyloreBosses/
├─ build.gradle, settings.gradle, gradle.properties, gradlew   the mod's Gradle project
├─ src/main/java/net/teamaof/skylorebosses/
│  ├─ SkyloreBosses.java              mod entry: loads every boss module
│  ├─ core/                           shared by all bosses
│  │  ├─ BossModule, BossModules      the module API and the list of bosses
│  │  ├─ Doctors                      Skylore doctor role
│  │  ├─ api/BossEvents               generic STARTED / DEFEATED events for the pack
│  │  ├─ registry/                    shared registers + creative tab, sounds, particles
│  │  ├─ fx/                          AnimFx (Blockbench keyframes, locators), ModelLocators
│  │  ├─ net/, client/, command/, config/
│  └─ bosses/
│     └─ matriscalyx/                 BOSS: Matris Calyx (entities, blocks, encounter, client, ...)
├─ src/main/resources/assets|data/skylore_bosses/
│  └─ .../<boss>/                     each boss's models, textures, sounds, advancements, tags
├─ bosses/
│  └─ matris_calyx/                   BOSS: Matris Calyx: DESIGN.md, Blockbench models/, tools/
├─ tools/
│  ├─ blockbench/                     shared Blockbench pipeline (MCP client, model builder, atlas, sound synth)
│  └─ testing/                        in-game tests driven by Marionette over HTTP
└─ docs/                              screenshots, ADDING_A_BOSS.md
```

Naming conventions:

| What | Pattern | Example |
|---|---|---|
| Sounds | `skylore_bosses:<boss>.<sound>` | `skylore_bosses:matris_calyx.arm.open` |
| Asset folders | `.../<boss>/` | `geo/entity/matris_calyx/grasping_arm.geo.json` |
| Advancements | `skylore_bosses:<boss>/...` | `skylore_bosses:matris_calyx/bloom_slain` |
| Boss-specific tags | `#skylore_bosses:<boss>/...` | |
| Entity, block and item ids | plain | `skylore_bosses:grasping_arm` |

To add a boss, follow [docs/ADDING_A_BOSS.md](docs/ADDING_A_BOSS.md).

## Build and test

```bash
./gradlew build
./gradlew runClient
```

- `gradle.properties` pins `org.gradle.java.home` to a local JDK 21. Change or remove it on other machines, or override per run with `-Dorg.gradle.java.home=<jdk21>`.
- A second dev client can run beside another one: `./gradlew runClient -PmarionettePort=25590`, then set `MARIONETTE_PORT=25590` for the test scripts. Set `pauseOnLostFocus:false` in `run/options.txt` so a background window keeps ticking.
- **In-game tests:** put the Marionette jar in `run/mods/`, then run `tools/testing/restart.py` followed by one of the `t_*.py` scripts:
  - `t_gate.py`: window gating
  - `t_flow.py`: phase flow
  - `t_laser.py`: laser and victory
  - `t_arms.py`: per-arm attacks
  - `t_proto.py`: Proto-World entry
  - `t_overhead.py`: Overhead end to end (phases, gate, pylons, re-arm, persistence, every attack, victory)

## Commands (op level 2)

- `/skylorebosses list`
- `/skylorebosses <boss> ...`. For Matris, `/skylorecalyx` is a shortcut:
  - `start [pos] [nodome]`
  - `tp`
  - `status`
  - `skipphase`
  - `reset`
  - `windows`
  - `attack <kind>`
  - `infection <players> <value>`

- `/skylorebosses overhead ...`. For Overhead, `/skyloreoverhead` is a shortcut:
  - `build [pos]`, `register [pos]`, `unregister`
  - `start`, `reset`, `skipphase`, `status`, `tp`
  - `breakpylon <0..3>`, `setpylons <0..4>`, `attack <id>`

## For the pack

- **Any boss:** listen to `BossEvents.DEFEATED` (level, bossId, origin, participants) to grant stages, e.g. `calyx_purged` for `matris_calyx`.
- **Matris details:** `MatrisEvents`, the advancement `skylore_bosses:matris_calyx/bloom_slain`, or `victoryFunction` in the `[matris_calyx]` section of `skylore_bosses-server.toml`.
- **Proto-World:** the Tempad target is `skylore_bosses:proto_world`.
- **Overhead details:** `OverheadEvents` (including `START_CHECK` to gate the fight, e.g. on the Mek steel stage), the advancement `skylore_bosses:overhead/prototype_down`, or `victoryFunction` in the `[overhead]` section. Grant `teknari_prototype_down` from `BossEvents.DEFEATED` with boss id `overhead`. See [bosses/overhead/DESIGN.md](bosses/overhead/DESIGN.md) �12.
- **Tags to fill:** `#skylore_bosses:doctor_tools`, `#skylore_bosses:matris_calyx/cures`, `#skylore_bosses:matris_calyx/laser_proof`, `#skylore_bosses:overhead/breakable_cover`, `#skylore_bosses:overhead/flight_vehicles`.
