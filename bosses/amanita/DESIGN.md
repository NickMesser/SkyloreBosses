# Amanita, the Hollow Bloom: design package

Skylore Act I-II boss (Tenebris nemesis spine). Ships as the `amanita` module of the `skylore_bosses` mod, custom Java, NeoForge 21.1.x on Minecraft 1.21.1, Java 21. Iron's Spellbooks' Archevoker and NeoVitae's `necromancy_summon*` were tonal reference only. Nothing here summons, subclasses, patches or needs either mod.

Every number marked **TUNE ME** is a first-pass value. All of them live in the `[amanita]` section of `skylore_bosses-server.toml` (§16 lists each key), so playtest tuning needs no rebuild.

Status: the module described here is implemented in `src/main/java/net/teamaof/skylorebosses/bosses/amanita/` and was exercised in a dev client through Marionette (`tools/testing/t_amanita.py`, §14 "Verification"). Where the code and this document disagree, the code is the bug.

> *"[CASE FILE] Subject is untouchable in darkness. The hollow holds 6 braziers, stocked, unlit."*

---

## 1. One-page fight bible

**Fantasy.** Amanita keeps a sealed hollow under a sky island, and the hollow is dark because she keeps it dark. She is a fungal-shadow Tenebris warlord: a gaunt pale figure grown out of a torn volva cup, under a great bruised-violet cap whose gills glow faintly. In the dark her bloom is open and nothing touches her. Light is the one thing she treats as an assault. When the hollow gets too bright she closes the flower, and every light in the room goes out with it.

**Player fantasy.** You are the one who brings light into her house. You carry torches, find the stocked braziers, and learn that the only way to hurt her is to make her stand in light: set it next to her, drive her into it, or light the whole room so she has nowhere dark to go. You then keep that light burning through her snuffs and her lamp-eaters long enough to finish her.

**Win condition.** Amanita's HP reaches 0. She can only lose HP while she is exposed (the light at her is at least the threshold), so she can only die lit.

**Fail states.**
- Player death. Normal respawn. The hollow stays sealed; rapping the knocking cap outside the mouth re-admits a participant to the entry pad. The fight continues for everyone else.
- Everyone leaves or dies and nobody is in the hollow for 60 s (**TUNE ME**): the hollow resets to dormant. Braziers go out, cover regrows, Amanita and her servants sink away, the mouth opens. No partial progress carries over.
- Never lighting her. Not a hard fail but it works as one: she is immune, her attacks keep landing, and after 45 s of unbroken dark she goes into deep bloom (P4), where she regrows and needs brighter light. A first attempt with no light in the inventory is meant to go this way, and the braziers are in the room so the second attempt doesn't have to.

**Tone.** Fungal-shadow dread and quiet cruelty, written up in Academy crash-recorder dryness. Amanita doesn't monologue. The fight text is case-file notation ("Subject is untouchable in darkness"), terse action-bar facts ("Snuffed: 4 lights out around her") and bossbar numbers. Any sass lands on the hollow cult and Tenebris isolationism, never on the player. No Calyx, infection, parasite or "the path" language appears anywhere in this fight: this is a different fungus lane.

**Pedagogy.** This is the first lesson of the act-boss chain: *the boss decides when it can be damaged.* The first hit a player lands in the dark says *"Nothing lands. She is closed in the dark (light 0 of 7 at her). Bring light to her."* The bossbar title always shows "light N of 7 at her". The P1 opener names the braziers. Overhead (pylons), the Static Deacon (floor), Null Router (channels) and Matris (arm windows) all rhyme with it.

---

## 2. Mod architecture

Amanita is a `BossModule` inside the existing mod, like the other four bosses. A sibling jar was considered and rejected: the shared core (particles, sounds, `AnimFx`, `Telegraph`, screen FX, `BossEvents`, config sections, `/skylorebosses`) already exists, and one jar is simpler for the pack to list.

| Item | Value |
|---|---|
| Mod id | `skylore_bosses` (module id `amanita`) |
| Package | `net.teamaof.skylorebosses.bosses.amanita` |
| Hard deps | NeoForge, Architectury API 13, GeckoLib 4.7 (already required by the mod) |
| Soft deps | none. Origins, Spectrum, Ars Nouveau, TACZ, Progressive Stages, QuestQueen, Iron's Spells and NeoVitae are touched only by the pack, through events, tags, scoreboard tags and datapacks. Spectrum lamps and Ars light blocks work because the gate reads the vanilla light engine. TACZ bullets work because the gate reads vanilla `DamageSource`s. |
| License | MIT, same as the mod |

```
bosses/amanita/
├─ AmanitaBoss.java           module entry: registries, config, events, client, commands (+ /skyloreamanita alias)
├─ AmanitaConfig.java         every TUNE ME value (server config)
├─ AmanitaLocators.java       GENERATED: model locator table (cap, gills, head, core, hand_l, hand_r, feet, mouth ...)
├─ AmanitaSoundIds.java       GENERATED: 45 sound ids
├─ api/AmanitaEvents.java     stable pack hooks (§11) + the origin resolver hook (§13)
├─ registry/                  AmanitaBlocks, AmanitaEntities, AmanitaItems
├─ block/                     HollowBrazierBlock (LIT, igniters), HollowKnockerBlock (start / readmit)
├─ encounter/
│  ├─ Hollows.java            SavedData: every hollow in a level, keyed by origin; ticks them
│  ├─ Hollow.java             one hollow + its fight: phase machine, light gate, snuff, census, ledger, adds clocks,
│  │                          Tenebris call, bar, victory, saves
│  ├─ Lights.java             light sampling, census, snuff rules (the §6 card in code)
│  ├─ Phase.java              state type
│  ├─ HollowLayout.java       geometry (floor, walls, braziers, columns, cover, vents, stance tiles, volumes, leash, fall zone)
│  ├─ HollowBuilder.java      the mod-placed test hollow, cover regrowth, brazier reset, mouth membrane
│  ├─ AmanitaBossBar.java     Amanita bar + Hollow light bar
│  └─ AmanitaCommonEvents.java level tick, deaths, block place (ledger / hush) and break hooks
├─ entity/
│  ├─ AmanitaEntity.java      the boss: movement to dark tiles, action slot, 10 actions, spore veils, damage gate
│  ├─ AmanitaAction.java      roster: timings, weights, cooldowns, interrupt rules
│  ├─ LampEaterEntity.java    add: eats lights
│  ├─ HollowSpawnEntity.java  add: fights players
│  └─ HollowBoltEntity.java   projectile (outline collision: it hits torches)
├─ client/                    AmanitaClient, AmanitaRenderer, HollowBoltRenderer (GeckoLib + glowmask)
└─ command/AmanitaCommands.java  designer commands (§14)
```

**Server vs client.** Everything that decides anything runs on the logical server: the light gate reads the server's light engine, the snuff edits blocks, the census scans blocks, the phase machine lives in SavedData. The client only renders: GeckoLib models with an emissive glowmask (gills, eyes, spores, a lamp-eater's ember belly) so silhouettes read in a pitch-dark room without emitting block light, keyframed particles and sounds, the vanilla boss bars, vanilla Darkness, and the core screen shake. No custom packets were needed: the gate state the client needs for animation (exposed, staggered, deep) rides on synched entity data.

**What the pack owns (datapack / KubeJS).**
- `#skylore_bosses:amanita/igniters` (item tag): what lights a brazier.
- `#skylore_bosses:amanita/snuff_immune` (block tag): light blocks a snuff never touches.
- `skylore_bosses:gameplay/amanita/shadow_artifacts` (loot table, `minecraft:gift` type): the per-participant shadow artifact roll. The shipped table is a placeholder.
- `skylore_bosses:entities/amanita` / `lamp_eater` / `hollow_spawn` loot tables.
- `victoryFunction` config, `AmanitaEvents`, `BossEvents`, the seven advancements, the origin scoreboard tags or resolver.

---

## 3. Arena: the hollow

### Footprint

A single sealed chamber, 27 x 27 blocks of floor (`|x|, |z| <= 13` from the origin), walls one block further out, a ceiling layer 11 blocks above the floor (air from +1 to +10). The origin is the centre block of the floor layer. North is -Z; the mouth is in the south wall.

```
  z=-14  WWWWWWWWWWWWWWWWWWWWWWWWWWWWW
         W . . . . . B . . . . . B . . . . . W     B  brazier (unlit), 6 total
  z=-11  W . V . C C . . . . V . . . . C C . V W   V  vent (adds climb out here)
         W . . . C C . . . . . . . . . C C . . W   C  2x2 stalk column, floor to ceiling
  z=-6   W B . . . . . . . . . . . . . . . . B W
  z=-5   W . . . . . . s s s . . s s s . . . . W   s  gill shelf (2 tall, breakable cover)
         W . . . . . . . . . . . . . . . . . . W
  z=-2   W . . . . . . . . . A . . . . . . . . W   A  bloom bed (Amanita rises here)
  z=0    W . . s . . . . . . O . . . . . . s . W   O  origin; s at x=+-9, z=-1..1
         W . . . . . . . . . . . . . . . . . . W
  z=5    W . . . . . . s s s . . s s s . . . . W
  z=6    W B . . . . . . . . . . . . . . . . B W
         W . . . C C . . . . . . . . . C C . . W
  z=11   W . V . C C . . . . . . . . . C C . V W
  z=12   W . . . . . . . . . E . . . . . . . . W   E  entry pad
  z=14   WWWWWWWWWWWWWWWMMMWWWWWWWWWWWWWWW   M  mouth, 3 wide x 3 tall (membrane during a fight)
  z=15..20                 landing, K knocking cap at z=18
```

### Intentional darkness

Nothing the hollow places emits light: wall, loam floor, ceiling, stalk columns, gill shelves, membrane and knocker all have light level 0, and the braziers ship unlit. The room is at block light 0 when a fight starts, and the P0 bloom silently snuffs anything a player placed before starting. The only light in a fresh fight is what players bring or ignite.

### Soft cover (non-emitting)

- **Stalk columns** (4 x 2x2, floor to ceiling): hard cover, unbreakable. They break line of sight for `hollow_bolt` and the dash.
- **Gill shelves** (6 walls, 3 wide x 2 tall): soft cover. Breakable by hand (axe) and by `bloom_slam`; regrown by the hollow after 60 s (**TUNE ME**). They stop bolts.

### Teaching props

Six **hollow braziers**: an iron hook holding a heap of coal, placed against the west, east and north walls. Right-click with anything in `#skylore_bosses:amanita/igniters` (flint and steel, fire charge, torch, soul torch, lantern, soul lantern, blaze rod) to light one; a Laevis player can light one bare-handed (§13). A lit brazier is light 15. Snuffs and lamp-eaters put braziers out (they go unlit rather than breaking), and they can always be relit. Braziers are unbreakable so nobody can carry them to Amanita: the player has to bring her to them, or bring their own lights. No supply chests: the pack's Act I loadout advice (torches, Spectrum lamps, Ars light glyphs) covers the rest.

### Entry and lockdown

Walking into the interior (anywhere but the two rows by the mouth) wakes a dormant hollow, as does rapping the **knocking cap** outside the mouth. The pack can veto the start through `AmanitaEvents.START_CHECK` (e.g. on `met_amanita`). On start, the mouth fills with an unbreakable **hollow membrane**. During a fight the knocker re-admits anyone to the entry pad. The membrane opens 8 s after the kill, or on reset.

### Height bands

| Band | Offset from origin | Use |
|---|---|---|
| Subfloor | -1 | wall blocks (unbreakable) |
| Floor | 0 | hollow loam (unbreakable) |
| Play band | +1 .. +3 | players, Amanita (3.2 tall), adds; lamp-eaters reach lights in this band |
| Upper band | +4 .. +10 | high lights. Safe from lamp-eaters, still reached by every snuff |
| Ceiling | +11 | hollow ceiling (unbreakable) |

The ceiling caps flight: there is no "hover above the fight" option inside the hollow.

### Void fall

The hollow hangs under a sky island. Anyone below the hollow during a fight (the fall zone: from 3 blocks under the floor down 128 blocks, a few blocks wider than the walls) is teleported to the entry pad with 4 damage and 3 s of Slow Falling. Amanita is leashed to the interior: anything that carries her out, or below the floor, makes her sink into the loam and rise on the bloom bed.

### Distant Horizons silhouette notes

The hollow is an enclosed box under an island, so from outside and at LOD distances it is just a dark mass under the island's rock. The mouth faces south onto a small loam landing: give the island a visible overhang or a path down to it, so the entrance reads at a distance. No emissive blocks are visible from outside, and none should be added: the dark approach is the point.

### Ownership handoff (Skylore Islands)

- **Skylore Islands owns** world placement: which island has the hollow, the island terrain around it, the approach path, and the structure NBT for the shipped version.
- **This mod owns** the encounter: the blocks the fight depends on, the entities, the systems, and the test hollow template (`/skyloreamanita build`).
- **The contract** is `HollowLayout`: the Islands structure only has to contain the same blocks at the same offsets from a chosen origin (floor loam, walls, the 6 braziers, the columns, the gill shelves, the mouth opening and the knocker), plus nothing that emits light. `/skyloreamanita register <origin>` adopts a placed structure without rebuilding it. **Call this out to the Islands agent before authoring final island terrain.**
- **Fallback test box:** `/skyloreamanita build [pos]` places a sealed dark copy 30 blocks above the caller: the AI bring-up and the automated test run in it.

---

## 4. Phase flowchart

Light metric: **L** = light at Amanita = max(block light at her feet block, block light at her chest block), plus sky light only if `countSkyLight` (false by default; the hollow is roofed). **T** = threshold: 7 (**TUNE ME**) in P0-P3, 10 (**TUNE ME**) in P4. She is **exposed** when she is fighting (P1-P4), not sealed (casting `full_snuff` or `deep_bloom`), and L >= T or she was at L >= T within the last 10 ticks (afterglow). Room-wide metric: the **census** = light sources (blocks emitting light 6+) inside the interior, refreshed every 20 ticks and right after any snuff or placement.

```
DORMANT ──walk in / knock (START_CHECK ok)──▶ P0 BLOOM OPENS (160 t, immune, bloom_open at t=100)
                                                   │ t >= 160
                                                   ▼
                  ┌──────────────────────── P1 DARK IMMUNITY ◀──────────────────────────┐
                  │  immune when closed; exposed = L >= 7                               │
                  │  HP <= 50% (first time) ─────────▶ P2 FULL SNUFF                    │
                  │  closed 900 t straight ──────────▶ P4 ────┐                         │
                  ▼                                            │                         │
            P2 FULL SNUFF: full_snuff (sealed) purges the room, Darkness 200 t,          │
            hollow-spawn wave + 1 lamp-eater                   │                         │
                  │ census >= 3 after the snuff landed, or 600 t in P2                   │
                  ▼                                            │                         │
            P3 LIT DUEL ◀───────────────────────────────┐      │                         │
                  │ closed 400 t straight ──▶ P4 ───────┼──────┘                         │
                  │ HP <= 0 (only possible while exposed) ▼                              │
                  ▼                        P4 DEEP BLOOM (T = 10, regen when closed)     │
               DEFEATED ─160 t─▶ CLEARED    │ exposed 80 t (cumulative) → forced open ───┤ (bared 100 t)
                  (rematch after 5 min)     │ 900 t in P4 → closing snuff (sealed) ──────┘
                                            └─ back to P1 if P2 has not happened yet, else P3
```

| Phase | Enter | Exit | Light state | Intent |
|---|---|---|---|---|
| P0 Bloom opens | fight start: silent full snuff, membrane, Amanita rises (anim `rise`) | `phaseTicks >= 160` | hollow dark | lockdown, silhouette, "hits do nothing yet" |
| P1 Dark immunity | P0 end; P4 exit before the first snuff | HP <= 50% → P2; `darkTicks >= 900` → P4 | mostly dark; player-placed patches | light a patch, drag her into it, first lamp-eaters |
| P2 Full snuff | HP <= 50% the first time (the hit is clamped so she lands exactly on 50%) | census >= 3 once the snuff has landed → P3 (relit); `phaseTicks >= 600` → P3 anyway | purged + Darkness | panic re-light with adds already up |
| P3 Lit duel | P2 exit; P4 exit after P2 | HP 0 → DEFEATED; `darkTicks >= 400` → P4 | contested | sustained light fight, denser attacks, adds on lights |
| P4 Deep bloom | P1/P3 stall | 80 exposed ticks at L >= 10 → forced open (bared) → P1/P3; `phaseTicks >= 900` → closing snuff → P1/P3 | stall punishment | brighter light needed, she regrows, harder snuffs |
| DEFEATED | HP 0 | 160 t | whatever burns | death animation, loot, hooks |
| CLEARED | DEFEATED end | rematch after 6000 t (**TUNE ME**) | | quiet hollow |

`darkTicks` counts consecutive ticks she is closed; any exposed tick resets it. It does not count while she is sealed. `hold` (designer) freezes the phase clocks, the stall, the add clocks and her attack choice.

Death rule: **always killable only when lit.** Dark damage is multiplied by `darkMult` (0 by default); if a pack raises it above 0 for soft DR, a dark hit can never take her below 1 HP.

---

## 5. Entity and component design

| Component | Kind | Owns |
|---|---|---|
| `Hollows` | `SavedData` per level | every hollow in the level, keyed by origin; ticks them from `SERVER_LEVEL_POST` |
| `Hollow` | plain object inside `Hollows` | phase machine, light gate (threshold, afterglow, bared), census, light ledger (who placed what), snuff, hush, adds clocks, Tenebris call, boss bars, rescue, victory, persistence |
| `Lights` | static system | light sampling at an entity, census scan, "is this snuffable", snuff one block, floor-light test |
| `AmanitaEntity` | `Monster` + `GeoEntity`, 1.4 x 3.2 | movement (dark stance), targeting and threat, the action slot and all 10 actions, spore veils, the damage gate hook, sudden-light and burst interrupts |
| Bloom cap, gills, spore veil | model bones / entity-owned list | the cap is 8 petal bones on the model (open in the dark, drooped when lit, shut during snuffs). The veil is a transient list inside the entity, not an entity: it has no hitbox or HP and dies with her. |
| `LampEaterEntity` | `Monster` + `GeoEntity`, 0.9 x 0.7 | walks to a reachable light, chews it out, burrows when sated; nips players if nothing is lit |
| `HollowSpawnEntity` | `Monster` + `GeoEntity`, 0.7 x 1.95, vanilla melee goals | fights players; photophobic |
| `HollowBoltEntity` | `Projectile` + `GeoEntity`, 0.45 | straight shadow bolt; outline collision so it hits torches; snuffs a light it hits |
| `HollowBrazierBlock` | block with `LIT` | ignition (igniter tag, Laevis hand), light 15 when lit |
| `HollowKnockerBlock` | block | start / readmit |
| VFX | particles `hollow_spore`, `gill_glow`, `snuff_smoke`, `veil_mist` (+ shared `ember`, `target_mark`) | spores, gill light, the smoke of a snuffed light, the veil and bolt trails |

No block entities: the brazier's state is a blockstate property and everything else is in `Hollow`.

---

## 6. Light-gate and snuff system card

### Sampling

- `Lights.sample(level, entity)` = `max(level.getBrightness(BLOCK, feet), level.getBrightness(BLOCK, chest))`, where feet is the block at her position +0.1 and chest is +1.6 (half her height, capped). Sky light is added only when `countSkyLight = true` (as `skyLight - skyDarken`), for a pack that builds the hollow open to the sky. The shipped hollow is roofed, so sky light is 0.
- Read every tick by `Hollow.tickGate`; the bossbar shows it every 5 ticks.
- Threshold T = 7 in P0-P3 and 10 in P4 (**TUNE ME**). A torch (14) makes light 7+ within about 7 blocks (taxicab); a lantern or a lit brazier (15) about 8.
- The designer override `/skyloreamanita setlight <0..15>` replaces the sample (-1 = real light).

### What counts as a light source

- **Census and relight target:** any block in the interior with `getLightEmission(level, pos) >= 6` (**TUNE ME** `sourceMinEmission`) and not in `#skylore_bosses:amanita/snuff_immune`. Torches, lanterns, lit braziers, campfires, glowstone, shroomlights, sea lanterns, jack o'lanterns, lava, fire, Spectrum lamps and Ars Nouveau light blocks all count. Candles (3 per candle) and glow lichen (7) count at their real value (4 candles = 12 counts).
- **Snuffable:** any block with emission > 0 that is not snuff-immune, and either has the `LIT` property (and is lit), is a fluid, or is breakable (destroy speed >= 0).

### What a snuff does, per block

| Block | Result |
|---|---|
| Has `LIT` (braziers, campfires, candles, furnaces, redstone lamps) | set `LIT=false` (braziers stay put and can be relit) |
| Fluid that emits (lava) | removed ("the hollow drinks it") |
| Anything else breakable (torches, lanterns, glowstone, Spectrum lamps, Ars light blocks, fire) | broken; drops its item if `snuff.drops` (true, **TUNE ME**), so the torch economy is "pick them back up" |
| Unbreakable, or in `snuff_immune` | untouched |

A **lamp-eater** eats with no drop. A **hollow bolt** that strikes a light snuffs that one block.

### Radius purge rules

| Source | Centre | Radius | Darkness | Also |
|---|---|---|---|---|
| `snuff_pulse` P1/P3 | her feet | 8 (**TUNE ME**) | 160 t to players within 8 | P3: 3 damage in range |
| `snuff_pulse` P4 | her feet | 12 (**TUNE ME**) | 160 t within 12 | 3 damage in range |
| `full_snuff` (P2 opener, P4 closer) | whole interior | all | 200 t to every participant | P2: add wave |
| P0 bloom (fight start) | whole interior | all | none | silent, uncounted |
| `spore_veil` P3/P4 end | veil centre | 3.5 | (the veil itself) | only the lights under it |
| `light_seeker_dash` smother | the light she dashed to | 2.0 | 80 t to the target player if within 3 | |
| `bloom_slam` band | her feet, expanding ring | ring band only | none | floor-standing lights only; wall and hanging lights survive |
| `hollow_bolt` | the block it hits | that block | 40 t on a player hit | |
| lamp-eater | the block it chews | that block | none | no drop |

Distances are from her feet position to the block centre. Walls and ceilings are not cover against a snuff: if it is in range, it goes out.

### Darkness application

Vanilla `minecraft:darkness` (no particles, icon shown), applied by `Hollow.applyDarkness`. Tenebris players also get Weakness I for 100 t from every snuff that reaches them (§13), because Darkness barely bothers them.

### Multiplayer ownership of placed lights

Every light-emitting block placed inside a fighting hollow (via `BlockEvent.EntityPlaceEvent`) and every brazier lit is recorded in the hollow's **ledger** as position → player UUID, persisted with the hollow. The ledger is used for: `LIGHT_PLACED` events, dash target priority (player-placed first), `/skyloreamanita lights`, and stats. It never changes the gate: a light counts the same whoever placed it. Entries are dropped when the block stops emitting (snuffed, eaten, broken).

### Hush

`darkness_howl` hushes the hollow for 60 t (**TUNE ME**): placing any light-emitting block inside the interior is cancelled, and braziers refuse to light, with the action-bar line *"Hushed. The hollow will not take a light yet."*

### Edge sources

| Source | Counts? | Why |
|---|---|---|
| Client dynamic-light mods (held torch light) | no | client-only; the server light engine never sees it. The gate cannot be cheesed from the client. |
| Held torch (vanilla) | no | no block light. But a player holding a light-emitting item is a "light carrier" and a `light_seeker_dash` target. |
| Glowing effect, spectral arrows | no | entity outline, not light |
| Glow item frames, glow ink signs | no | vanilla: emission 0 |
| Glow lichen, candles, sculk catalysts, magma | yes, at their real emission | they are real block light; low emitters rarely reach 7 at her |
| Light blocks (`minecraft:light`) placed by creative/commands | yes | real light; breakable only in creative, so a snuff does break it (destroy speed 0) |
| Beacons | yes, and a snuff breaks them (with drop) | add to `snuff_immune` if the pack disagrees |
| Spectrum lamps, Ars light glyph blocks | yes | real block light; broken by snuffs (Ars light blocks drop nothing by their own loot) |

---

## 7. Damage-gate math

```
L        = max(blockLight(feet), blockLight(chest)) [ + max(0, skyLight - skyDarken) if countSkyLight ]
T        = 7   (P4: 10)                                             TUNE ME lightGate.threshold / deepThreshold
litTick  : on every tick with L >= T (and not sealed): exposedUntil = now + 10       TUNE ME afterglowTicks
exposed  = phase in {P1..P4} and not sealed and (L >= T or now < exposedUntil)
mult     = exposed ? (1 + 0.25 * clamp((L - T) / (15 - T), 0, 1)) * (bared ? 1.25 : 1)   TUNE ME brightBonus, baredMult
                   : darkMult (0)                                                           TUNE ME darkMult
P0, sealed (full_snuff / deep_bloom telegraph + active), DORMANT, CLEARED: mult = 0
dealt    = incomingAfterSourceChecks * mult
if !exposed: dealt = min(dealt, hp - 1)                       (dark soft DR can never kill)
if phase == P1 and !snuffedOnce and hp - dealt <= 0.5 * max:  dealt = hp - 0.5 * max; then setHealth(0.5 * max); enter P2
vanilla armour (4) and the 10-tick invulnerability window then apply inside LivingEntity.hurt
```

Examples at T = 7: light 7 → x1.00; light 11 → x1.125; light 14 (torch adjacent) → x1.22; light 15 → x1.25. Bared at light 12 → x1.45. Measured in the dev client: 20 damage at light 14 took 24 HP off (x1.22 plus rounding through armour), 20 damage bared at light 12 took 28.

**Window length after lighting.** There is no timed window: she is exposed for as long as L >= T, plus the 10-tick afterglow. The windows are created by the players (where the light is) and closed by her (walking to a dark tile, snuffing, the dash, lamp-eaters). In practice a window is the time between her stepping into light and her next snuff or reposition: a snuff has a 40-tick telegraph and a 300-400 tick cooldown, and she re-picks her stance at once when lit, but she walks at 0.85x speed while lit and every tile she can reach has to be dark for her to escape.

**Snuff cadence.** `snuff_pulse` cooldown: P1 400 t, P3 300 t, P4 200 t (**TUNE ME** in `AmanitaAction.cooldown`). It is only drawn with a light in range, and in P1 only with 2+ lights in range or while she is exposed. Weight doubles while she is exposed.

**Interrupts.**
- Sudden light: if L jumps by more than 3 in one tick while she is going from closed to exposed (someone set a light next to her) and she is telegraphing a light-interruptible action, it is cancelled and she recoils (stagger 20 t, **TUNE ME**).
- Burst: exposed damage >= 8% of max HP (**TUNE ME**) within 40 t cancels any burst-interruptible telegraph (including `snuff_pulse`) with a 30 t stagger; then 200 t immunity to burst staggers.

**Deep-bloom penalties.** T rises to 10, so a single torch no longer exposes her unless it is adjacent. While closed she regenerates 0.5% max HP per second (**TUNE ME**), at most 10% max HP per deep bloom (**TUNE ME**), and never back above the 50% snuff threshold once P2 has happened. `snuff_pulse` reaches 12 blocks and has a 200 t cooldown. After 900 t she casts a sealed closing snuff of the whole room. Forcing her open (80 cumulative exposed ticks at L >= 10) bares her for 100 t (x1.25) and staggers her for 60 t.

**Multiplayer HP.** `maxHP = 600 * min(2.5, 1 + 0.5 * (participants - 1))` (**TUNE ME**), fixed at spawn.

---

## 8. Bossbar and scoring

Two vanilla boss bars, shown to every participant (players inside the hollow volume), synced every 20 ticks and updated every 5 ticks.

**Amanita bar** (progress = HP / max, darkens the screen, boss music, world fog). Title: `Amanita, the Hollow Bloom  |  <status>`.

| State | Colour | Status line |
|---|---|---|
| P0 | white | The bloom opens. Nothing lands yet |
| closed (P1/P3) | purple | Closed in the dark: light 3 of 7 at her. Immune |
| exposed | yellow | Exposed: light 12 of 7. Taking 116% |
| bared | yellow | Bared: light 12 of 10. Taking 145% |
| P2 closed | blue | Snuffed: light 0 of 7 at her. Relight the hollow |
| P4 closed | red | Deep bloom: light 8 of 10 at her. Immune, regrowing |
| defeated | white | Wilted |

**Hollow light bar** (notched 10).

| State | Progress | Title |
|---|---|---|
| P1/P3 | census / 10 | Hollow light: N sources burning (white; yellow when any burn) |
| P2 | census / 3 | Relight the hollow: N of 3 sources (blue) |
| P4 | deepExposed / 80 | Deep bloom: forced open N% (needs light 10 at her) (red) |

Co-op players all see the same bars. No per-player score is kept. The fight summary (`EncounterStats`: ticks, deaths, snuffs, lights placed / snuffed / eaten, deep blooms, relit by players, exposed ticks) goes to `AmanitaEvents.VICTORY` for the pack to score if it wants.

---

## 9. Per-phase action catalogs

### How the AI picks

One action slot: IDLE → TELEGRAPH → ACTIVE → RECOVERY → IDLE. In IDLE she waits a gap (P1 30 t, P2 26 t, P3 18 t, P4 16 t, all divided by `attackSpeed`), then builds a weighted bag of every action whose phase weight is above 0, whose cooldown has expired and whose condition holds, and draws one. Cooldowns are per action and start when the action finishes; there is no global cooldown beyond the gap. Scripted beats (`bloom_open`, `full_snuff`, `deep_bloom`) are forced by the hollow and never drawn. A cancelled action goes on half its cooldown.

**Targeting.** Threat = damage dealt to her (decays 2% per second). "Top threat" scores threat minus 0.5 x distance. Creative and spectator players are never targeted.

**Idle / reposition (all phases).** She keeps to the **darkest legal tile near her target**: every 30 ticks, and at once when she becomes exposed, she scores floor tiles on a 2-block grid (excluding columns, cover and anything with collision at feet, +1 or +2): `score = (tile light >= T ? 100 + 4*light : 1.5*light) + |distance(tile, target) - 3| + 0.3*distance(tile, her)`, and walks to the lowest. So in the dark she closes to about 3 blocks of her target; when lit she leaves for the nearest dark tile near the target; when the whole room is lit she stays on the dimmest tile and fights. Speed x1.0 closed, x0.85 exposed (wilting), x1.1 more in P3/P4. If she makes no progress for 100 t she sinks into the loam and rises at the goal. Leash: outside the interior or below the floor she sinks and rises on the bloom bed. She is not pushable and takes no knockback or fall damage.

### P0 Bloom opens

160 t, immune. She rises out of the loam on the bloom bed (anim `rise`, 6 s). The only action is scripted:

#### Attack: `amanita:bloom_open`

| Field | Content |
|---|---|
| **Id** | `bloom_open` |
| **Display name** | Bloom Opens |
| **Phases** | P0 (scripted at P0 t=100). Designer-forceable in any phase. |
| **Unlock condition** | P0 phase tick 100 |
| **Weight / priority** | scripted, weight 0 in every bag |
| **Cooldown** | none (one-shot) |
| **Range / positioning** | centred on her; affects players within 10 blocks horizontal. She does not move. |
| **Telegraph** | 40 t: the petals slam open (anim `bloom_open`), a `gill_glow` ring expands from 1 to 9.8 blocks (every 4 t), sound `bloom.windup` |
| **Wind-up** | the 40 t telegraph; she cannot move or cancel |
| **Active** | 1 t: `hollow_spore` burst (80), sound `bloom.open` |
| **Recovery** | 20 t; immune (P0) |
| **Hit resolution** | no damage. Push 1.2 horizontal / 0.4 up away from her, Slowness I 40 t ("fear"), Darkness 60 t, action bar *"The bloom opens. Your blows do nothing to her in the dark."* |
| **Projectile / AoE specs** | radial, radius 10 |
| **Interruptibility** | never |
| **Counterplay** | none needed; it teaches. Stand back or get pushed. |
| **Multiplayer notes** | all players in range |
| **Failure / edge cases** | no players in range: plays anyway. Against a wall the push does nothing. |

### P1 Dark immunity

Bag: `shadow_lash` 6, `hollow_bolt` 5, `spore_veil` 3, `snuff_pulse` 3, `light_seeker_dash` 3. Gap 30 t. Lamp-eaters: one every 500 t while any light burns (max 2 alive); the first comes 100 t after the first light is placed.

#### Attack: `amanita:shadow_lash`

| Field | Content |
|---|---|
| **Id** | `shadow_lash` |
| **Display name** | Shadow Lash |
| **Phases** | P1, P2, P3, P4 |
| **Unlock condition** | nearest target within 4.5 blocks horizontal and 3 vertical |
| **Weight / priority** | P1 6 (x2 when in reach, which is always when it is legal) |
| **Cooldown** | 40 t |
| **Range / positioning** | 4.5-block sector, 60° half-angle, locked at telegraph start toward the nearest target; she faces the locked yaw |
| **Telegraph** | 14 t: `target_mark` sector on the ground (every 4 t), the long tendril hand draws back (anim `lash_windup`), sound `lash.windup` |
| **Wind-up** | 14 t, rooted; can be cancelled by sudden light or burst |
| **Active** | 4 t, hit resolved on active tick 1 |
| **Recovery** | 14 t; takes normal gated damage (lit = full) |
| **Hit resolution** | 7 melee (`mobAttack`, **TUNE ME**), push 0.9 / 0.3, once per player per swing |
| **Projectile / AoE specs** | sector r 4.5, ±60°, within 1.2 blocks always hits |
| **Interruptibility** | telegraph: sudden light, burst. Active: never. |
| **Counterplay** | step out of the marked sector (behind her or past 4.5); set a torch at her feet during the wind-up to cancel it |
| **Multiplayer notes** | aimed at the nearest; hits everyone in the sector |
| **Failure / edge cases** | target dies mid-telegraph: swings at the locked yaw anyway. Not legal beyond 4.5 blocks or 3 vertical. |

#### Attack: `amanita:hollow_bolt`

| Field | Content |
|---|---|
| **Id** | `hollow_bolt` |
| **Display name** | Hollow Bolt |
| **Phases** | P1, P2, P3, P4 |
| **Unlock condition** | any targetable participant |
| **Weight / priority** | P1 5 |
| **Cooldown** | 50 t |
| **Range / positioning** | any range; prefers targets in line of sight from her right hand |
| **Telegraph** | 20 t: arm raised (anim `bolt_cast`), `gill_glow` gathers at the hand every 4 t, sound `bolt.windup` |
| **Wind-up** | 20 t, rooted, cancellable by sudden light / burst |
| **Active** | P1: 1 t, one bolt at t=0 |
| **Recovery** | 14 t |
| **Hit resolution** | 6 projectile damage (**TUNE ME**) + Darkness 40 t on a player. A bolt that strikes a light-emitting block snuffs it. Cover stops it. |
| **Projectile / AoE specs** | speed 1.0 b/t, no gravity, life 60 t (60 blocks), no homing, no lead in P1-P3, outline collision (hits torches, lanterns), passes through adds |
| **Interruptibility** | telegraph: sudden light, burst |
| **Counterplay** | break line of sight with a column or gill shelf; strafe (no lead); keep your torches out of her line to you |
| **Multiplayer notes** | a rooted Tenebris player within 24 first; otherwise 30% a random target, else top threat, from those in line of sight |
| **Failure / edge cases** | target dead or gone before a scheduled bolt: that bolt is skipped. No LOS to anyone: fires at top threat anyway (hits cover). |

#### Attack: `amanita:spore_veil`

| Field | Content |
|---|---|
| **Id** | `spore_veil` |
| **Display name** | Spore Veil |
| **Phases** | P1, P2, P3, P4 |
| **Unlock condition** | nearest target within 20 blocks |
| **Weight / priority** | P1 3 |
| **Cooldown** | 220 t |
| **Range / positioning** | P1/P2: placed on the target's position at telegraph start |
| **Telegraph** | 30 t: `target_mark` ring of radius 3.5 at the landing point (every 5 t), spores shaken from her cap (anim `veil_cast`), sound `veil.windup` |
| **Wind-up** | 30 t, rooted, cancellable by sudden light / burst |
| **Active** | 1 t: the veil blooms (sound `veil.bloom`) |
| **Recovery** | 16 t |
| **Hit resolution** | the veil lasts 100 t; every 20 t each player inside (3.5 horizontal, 3 vertical) takes 1 magic damage (**TUNE ME**) and Darkness 40 t. P1/P2 veils do not snuff. |
| **Projectile / AoE specs** | static cylinder r 3.5 at the target's feet; `veil_mist` particles through it |
| **Interruptibility** | telegraph: sudden light, burst. The veil itself cannot be cleared. |
| **Counterplay** | walk out of the ring during the 30 t telegraph; fight from outside it |
| **Multiplayer notes** | top threat |
| **Failure / edge cases** | target moves: the veil lands where the ring was. Several veils can overlap. Veils are not saved; a reload removes them. |

#### Attack: `amanita:snuff_pulse`

| Field | Content |
|---|---|
| **Id** | `snuff_pulse` |
| **Display name** | Snuff Pulse |
| **Phases** | P1, P3, P4 (P2 uses `full_snuff`) |
| **Unlock condition** | P1: 2+ light sources within 8 blocks, or she is exposed |
| **Weight / priority** | P1 3, x2 while exposed |
| **Cooldown** | P1 400 t |
| **Range / positioning** | centred on her feet, radius 8 |
| **Telegraph** | 40 t: the petals fold shut (anim `snuff_windup`), a `gill_glow` ring on the ground at radius 8 (every 5 t), every light in range puffs `snuff_smoke` (every 8 t), action bar *"Snuff pulse. Lights near her will go out."*, sound `snuff.gather` |
| **Wind-up** | 40 t, rooted; cancellable only by burst (it is her answer to light, so light cannot cancel it) |
| **Active** | 1 t: snuff radius 8 (§6), Darkness 160 t to players within 8, sound `snuff.pulse`, action bar "Snuffed: N lights out around her." |
| **Recovery** | 24 t; she usually ends dark and re-picks a stance at once |
| **Hit resolution** | P1: no damage. Lights in range extinguished / broken (drops). |
| **Projectile / AoE specs** | sphere r 8 around her feet |
| **Interruptibility** | burst (8% max HP within 40 t while exposed) cancels the telegraph: *"The snuff breaks. She reels."* |
| **Counterplay** | burst her while she winds up; keep lights more than 8 blocks from her feet (a ring of torches she has to walk into); keep a torch in hand to replace the one she takes |
| **Multiplayer notes** | affects everyone in range |
| **Failure / edge cases** | no lights in range when it lands: still gives Darkness. Unbreakable / immune lights stay lit. |

#### Attack: `amanita:light_seeker_dash`

| Field | Content |
|---|---|
| **Id** | `light_seeker_dash` |
| **Display name** | Light-Seeker Dash |
| **Phases** | P1, P3, P4 |
| **Unlock condition** | a dash target exists: a player carrying light (a light-emitting block item or an igniter in either hand) 3-18 blocks away in line of sight; otherwise the brightest census light 4-20 blocks away |
| **Weight / priority** | P1 3, x2 while exposed |
| **Cooldown** | 180 t |
| **Range / positioning** | to the target: the carrier's position, or the floor under the light (clamped 0.8 inside the walls) |
| **Telegraph** | 20 t: she crouches toward it, cap folding (anim `dash_windup`), a `target_mark` line on the floor to the target (every 3 t), `snuff_smoke` on the target light, sound `dash.windup`; a carrier gets *"She is coming for the light in your hand."* |
| **Wind-up** | 20 t; burst can cancel |
| **Active** | up to 30 t of travel at 0.8 b/t (collides with walls and columns; stops early if blocked), then a 12 t smother on arrival (anim `smother`), then the snuff. Total ACTIVE <= 42 t. |
| **Recovery** | 16 t |
| **Hit resolution** | 8 melee to each player she passes through (once), push 1.0 / 0.4. On arrival: snuff radius 2.0 around the target light; Darkness 80 t to a carrier within 3. |
| **Projectile / AoE specs** | her bounding box +0.4 along the path |
| **Interruptibility** | telegraph: burst. Travel and smother: never. |
| **Counterplay** | sidestep the marked line; during the 12 t smother she stands in the light she came to eat, exposed: hit her then. Put the torch away (swap hands) to stop being the carrier. |
| **Multiplayer notes** | carriers first (so the player placing lights draws her while another fights); otherwise the light, player-placed first |
| **Failure / edge cases** | target gone at ACTIVE start: cancelled. Light snuffed before she arrives: she smothers nothing. Blocked by a column: stops there and smothers where she stands. |

### P2 Full snuff

Enter: HP <= 50% for the first time. She is sealed at once and casts `full_snuff`. Bag after that: `shadow_lash` 5, `hollow_bolt` 4, `darkness_howl` 3, `spore_veil` 2. Gap 26 t. No timed adds: the wave comes with the snuff.

#### Attack: `amanita:full_snuff`

| Field | Content |
|---|---|
| **Id** | `full_snuff` |
| **Display name** | Full Snuff ("closing the flower") |
| **Phases** | P2 (scripted on entry), P4 (scripted as the closing snuff at 900 t) |
| **Unlock condition** | P1 → P2 transition; P4 `phaseTicks >= 900` |
| **Weight / priority** | scripted |
| **Cooldown** | none |
| **Range / positioning** | the whole interior; she stays where she is |
| **Telegraph** | 60 t: the cap folds shut around her entirely (anim `full_snuff_windup`), every census light puffs `snuff_smoke` (every 6 t), a closing `gill_glow` ring, red action bar *"She is closing the flower. Every light in the hollow will go out."*, title *"Full snuff"*, screen shake, sound `snuff.windup` |
| **Wind-up** | 60 t; she is **sealed** (immune) through telegraph and active |
| **Active** | 1 t: every snuffable light in the interior goes out, Darkness 200 t to every participant, the dark "taken" screen fade, chat line "[CASE FILE] Full snuff: N lights out." |
| **Recovery** | 30 t |
| **Hit resolution** | no damage. P2: spawns hollow-spawn (1 + participants, max 5) and one lamp-eater at the vents. |
| **Projectile / AoE specs** | whole interior |
| **Interruptibility** | never |
| **Counterplay** | stop placing lights once it starts (they will go out); stand by a brazier and an igniter so you can relight the moment it lands; keep torches in the inventory for this moment |
| **Multiplayer notes** | everyone |
| **Failure / edge cases** | a reload during the telegraph: the hollow re-scripts it on her first tick (`resumeScripts`). HP damage cannot skip P2: the entering hit is clamped to exactly 50%. |

#### Attack: `amanita:shadow_lash` (P2)

Identical to P1 `shadow_lash`, with these overrides: weight 5.

#### Attack: `amanita:hollow_bolt` (P2)

Identical to P1 `hollow_bolt`, with these overrides: weight 4; ACTIVE 9 t, two bolts at active t=0 and t=8, both at the same target.

#### Attack: `amanita:spore_veil` (P2)

Identical to P1 `spore_veil`, with these overrides: weight 2.

#### Attack: `amanita:darkness_howl`

| Field | Content |
|---|---|
| **Id** | `darkness_howl` |
| **Display name** | Darkness Howl |
| **Phases** | P2, P3, P4 |
| **Unlock condition** | P2: always; P3/P4: at least one light burns |
| **Weight / priority** | P2 3 |
| **Cooldown** | 400 t |
| **Range / positioning** | room-wide effect; damage within 6 blocks |
| **Telegraph** | 30 t: she inhales, head back, cap raised (anim `howl_windup`), `veil_mist` at her head, action bar *"Darkness howl. Place your light before it lands."*, sound `howl.inhale` |
| **Wind-up** | 30 t, rooted, cancellable by sudden light or burst |
| **Active** | 1 t: Darkness 120 t to every participant, **hush** 60 t (no light can be placed or brazier lit), 2 magic damage within 6 (**TUNE ME**), sound `howl` |
| **Recovery** | 20 t |
| **Hit resolution** | as above |
| **Projectile / AoE specs** | room-wide / r 6 |
| **Interruptibility** | telegraph: sudden light (a torch set next to her during the inhale cancels the howl), burst |
| **Counterplay** | place your light during the 30 t inhale, ideally at her feet (that also cancels it) |
| **Multiplayer notes** | everyone |
| **Failure / edge cases** | a placement attempt during the hush is cancelled and the item stays in the hand |

### P3 Lit duel

Bag: `shadow_lash` 5, `hollow_bolt` 4, `spore_veil` 3, `snuff_pulse` 4, `darkness_howl` 2, `bloom_slam` 4, `light_seeker_dash` 4. Gap 18 t. Lamp-eaters every 300 t while lit (max 3), hollow-spawn every 400 t (max 3). Adds focus lights (eaters) and players (spawn).

#### Attack: `amanita:shadow_lash` (P3)

Identical to P1 `shadow_lash`, with these overrides: weight 5; ACTIVE 14 t with a second swing at active t=11, the sector turned 25° further, hit list cleared (a player can be hit by both: 14 total).

#### Attack: `amanita:hollow_bolt` (P3)

Identical to P1 `hollow_bolt`, with these overrides: weight 4; ACTIVE 11 t, three bolts at t=0, 5, 10 in a fan (0°, -10°, +10° around the aim).

#### Attack: `amanita:spore_veil` (P3)

Identical to P1 `spore_veil`, with these overrides: weight 3; the veil is placed on the **densest cluster of lights within 16 blocks** of her (the light with the most other lights within 3.5), falling back to the target; when it ends (100 t) it snuffs every light within 3.5 of its centre. Counterplay: the 100 t veil is the warning. Move the fight or add lights elsewhere.

#### Attack: `amanita:snuff_pulse` (P3)

Identical to P1 `snuff_pulse`, with these overrides: weight 4 (x2 exposed); cooldown 300 t; unlock needs only 1 light in range; deals 3 magic damage (**TUNE ME**) to players within 8.

#### Attack: `amanita:darkness_howl` (P3)

Identical to P2 `darkness_howl`, with these overrides: weight 2; needs at least one light burning.

#### Attack: `amanita:bloom_slam`

| Field | Content |
|---|---|
| **Id** | `bloom_slam` |
| **Display name** | Bloom Slam |
| **Phases** | P3, P4 |
| **Unlock condition** | nearest target within 10 blocks horizontal |
| **Weight / priority** | P3 4 |
| **Cooldown** | 160 t |
| **Range / positioning** | centred on her |
| **Telegraph** | 30 t: she rises on the volva, arms overhead (anim `slam_windup`), `target_mark` rings at 8.5 and 4 (every 5 t), action bar *"Bloom slam. Jump the ring; floor lights will scatter."*, sound `slam.windup` |
| **Wind-up** | 30 t, rooted, burst can cancel |
| **Active** | 16 t: a ring expands r = 1 + 0.5 t (1 → 8.5), hitting in the band [r-0.9, r+0.3]; screen shake within 20 |
| **Recovery** | 30 t; she stays where she slammed (if she slammed in light, she is exposed for all of it) |
| **Hit resolution** | 9 melee to grounded players in the band (once), push 0.8 / 0.6; breaks gill shelves in the band; knocks over (breaks, with drops) floor-standing lights in the band and puts out floor braziers; wall torches and hanging lanterns survive |
| **Projectile / AoE specs** | expanding ring, 0.5 b/t, jumpable (only grounded players are hit) |
| **Interruptibility** | telegraph: burst |
| **Counterplay** | jump the ring as it reaches you; mount lights on walls and columns rather than the floor; hit her in the 30 t recovery |
| **Multiplayer notes** | everyone in the band |
| **Failure / edge cases** | players in the air when the band passes are missed |

#### Attack: `amanita:light_seeker_dash` (P3)

Identical to P1 `light_seeker_dash`, with these overrides: weight 4 (x2 exposed).

### P4 Deep bloom

Enter: stall (closed 900 t straight in P1, or 400 t in P3). She is sealed and casts `deep_bloom`. T = 10. Bag: `shadow_lash` 4, `hollow_bolt` 4, `spore_veil` 2, `snuff_pulse` 5, `darkness_howl` 3, `bloom_slam` 3, `light_seeker_dash` 3. Gap 16 t. Lamp-eaters every 300 t while lit (max 3); no hollow-spawn.

#### Attack: `amanita:deep_bloom`

| Field | Content |
|---|---|
| **Id** | `deep_bloom` |
| **Display name** | Deep Bloom |
| **Phases** | P4 (scripted on entry) |
| **Unlock condition** | P1/P3 stall |
| **Weight / priority** | scripted |
| **Cooldown** | none |
| **Range / positioning** | in place; she roots |
| **Telegraph** | 40 t: the petals are flung wide and the gills blaze (anim `deep_bloom`), an expanding `hollow_spore` ring, title *"Deep bloom: left in the dark too long. She is rooting."*, sound `deep.windup` / `deep.bloom` |
| **Wind-up** | 40 t, sealed |
| **Active** | 1 t: all hollow-spawn sink back into the loam; two lamp-eaters climb out |
| **Recovery** | 20 t |
| **Hit resolution** | no damage |
| **Projectile / AoE specs** | none |
| **Interruptibility** | never |
| **Counterplay** | have lights ready: from here on one torch in range is not enough; get light 10+ on her for 80 ticks in total (a lantern or brazier within about 5 blocks, or several torches) |
| **Multiplayer notes** | n/a |
| **Failure / edge cases** | reload during it: dropped (P4 continues without the add swap) |

#### Attack: `amanita:full_snuff` (P4 closing snuff)

Identical to P2 `full_snuff`, with these overrides: fires when P4 has lasted 900 t without being forced open; warning *"Closing snuff. Force her open before it lands."*; no add wave; when it lands P4 ends (back to P1 before the first snuff, P3 after) with the stall counter reset. Chat: "[CASE FILE] Closing snuff: N lights out. Deep bloom ends."

#### Attack: `amanita:shadow_lash` (P4)

Identical to P3 `shadow_lash` (double swing), with these overrides: weight 4.

#### Attack: `amanita:hollow_bolt` (P4)

Identical to P3 `hollow_bolt` (three-bolt fan), with these overrides: weight 4; bolts lead the target's horizontal velocity by 0.5 x flight time.

#### Attack: `amanita:spore_veil` (P4)

Identical to P3 `spore_veil` (cluster-seeking, snuffs at the end), with these overrides: weight 2.

#### Attack: `amanita:snuff_pulse` (P4)

Identical to P3 `snuff_pulse`, with these overrides: weight 5 (x2 exposed); radius 12; cooldown 200 t.

#### Attack: `amanita:darkness_howl` (P4)

Identical to P3 `darkness_howl`, with these overrides: weight 3.

#### Attack: `amanita:bloom_slam` (P4)

Identical to P3 `bloom_slam`, with these overrides: weight 3.

#### Attack: `amanita:light_seeker_dash` (P4)

Identical to P3 `light_seeker_dash`, with these overrides: weight 3.

### Phase transition matrix

| From | To | Trigger (predicate, ticks) | Timer / counter reset rules |
|---|---|---|---|
| DORMANT | P0 | participant in `trigger` box (checked every 10 t) or knocker, and `START_CHECK` not false | all fight counters, ledger, braziers unlit, silent full snuff, membrane closed, chunks force-loaded |
| P0 | P1 | `phaseTicks >= 160` | `darkTicks = 0` |
| P1 | P2 | `hp <= 0.5 * max` and `!snuffedOnce` (in `hurt`, and checked each tick) | `snuffedOnce = true`, `fullSnuffDone = false`, her action cancelled, `full_snuff` scripted |
| P1 | P4 | `darkTicks >= 900` (consecutive closed ticks; not counted while sealed) | `deepExposed = 0`, `deepHealed = 0`, `darkTicks = 0`, `deep_bloom` scripted |
| P2 | P3 | `fullSnuffDone && census >= 3` (relit, awards `relit`) | `darkTicks`, add clocks = 0 |
| P2 | P3 | `phaseTicks >= 600` and she is not sealed | same |
| P3 | P4 | `darkTicks >= 400` | as P1 → P4 |
| P4 | P1 / P3 | `deepExposed >= 80` (cumulative exposed ticks at T = 10): forced open, bared 100 t, stagger 60 t | `darkTicks`, `deepExposed`, add clocks = 0 |
| P4 | P1 / P3 | `phaseTicks >= 900`: closing `full_snuff` scripted; transition when it lands | same; she keeps her HP |
| P1-P4 | DEFEATED | HP 0 (only possible while exposed) | adds sink away, loot, hooks |
| P0-P4 | DORMANT | no participant for 1200 t, or `/skyloreamanita reset` | full reset |
| DEFEATED | CLEARED | 160 t | membrane open, chunks released |
| CLEARED | DORMANT | `rematch` and 6000 t since the kill | full reset |

"P1 / P3" means P1 when `snuffedOnce` is false (P2 has not happened), otherwise P3. P2 happens once per fight.

---

## 10. Adds and environmental hazards

### Lamp-eater (`skylore_bosses:lamp_eater`)

A pale moth-grub with a lantern jaw; its belly glows with what it ate.

| Field | Content |
|---|---|
| HP / speed / damage | 12 (**TUNE ME**) / 0.23 / 2 nip |
| Spawn | P1: every 500 t counted only while a light burns (first 100 t after the first light), max 2 alive. P2: one with the snuff wave. P3/P4: every 300 t while lit, max 3. P4 entry: two. At the vents, rising out of the loam (20 t, harmless). |
| AI priority | 1) a reachable light within 10 blocks of Amanita (she stays dark) 2) the nearest reachable light 3) no light: nip the nearest player (every 30 t). Re-targets every 20 t. |
| Reach | lights whose block is at most 2 blocks above the floor standing level and within 2.6 of its centre. Lights mounted 4+ blocks up are out of reach. |
| Eating | 30 t chew (**TUNE ME**), telegraphed by the chewing animation and ember particles at the light; then the light goes out with no drop (braziers go unlit). Heals 2. |
| Sated | after 3 lights (**TUNE ME**) it burrows away |
| Stuck | no progress for 100 t: that light is blacklisted for 200 t |
| Despawn | burrows on victory, reset, or when its hollow stops fighting |
| Counterplay | kill it during the 1.5 s chew (it is fragile and slow); mount lights high; defend the light nearest her |

### Hollow-spawn (`skylore_bosses:hollow_spawn`)

A spore-husk thrall under a small cap.

| Field | Content |
|---|---|
| HP / speed / damage | 20 / 0.26 / 4 melee (**TUNE ME**) |
| Spawn | P2 snuff wave: 1 + participants (max 5). P3: one every 400 t, max 3. None in P1 or P4. |
| AI | vanilla melee: nearest player |
| Photophobia | takes x1.5 damage standing in light 7+; burns for 2 per second (**TUNE ME**) in light 12+ |
| Despawn | sink into the loam at P4 entry (deep bloom calls them back), on victory, on reset |
| Counterplay | fight them in the light you just made; relighting the hollow thins them |

Neither add can hurt Amanita, and her attacks and bolts pass through them.

### Environmental hazards

- The dark itself (Darkness from snuffs, howls, veils and bolts).
- Spore veils (§9).
- The void under the hollow (rescued to the entry pad, 4 damage).

---

## 11. Death, loot, trophy and pack events API

**Kill criteria.** HP 0 inside a fighting hollow. Because the gate multiplies dark damage by 0 (and a soft dark DR can never kill), she dies only while exposed. `/kill` and other `bypasses_invulnerability` damage ignore the gate (designer and pack tool only).

**On the kill** (`Hollow.onAmanitaDied`), for each participant (players in the hollow volume):
1. advancements `bloom_wilted`, and `no_deep_bloom` if she never deep-bloomed this fight
2. trophy `skylore_bosses:hollow_bloom_cap` if `grantTrophy` (true)
3. one roll of `artifactLootTable` (default `skylore_bosses:gameplay/amanita/shadow_artifacts`, loot context `minecraft:gift` with the player as `this`), then `AmanitaEvents.ARTIFACTS_ROLLED(level, origin, player, drops)`: the pack may add to, remove from or replace `drops` before they are given
4. `victoryFunction` run as the player (permission 2), if set

Then `AmanitaEvents.VICTORY(level, origin, participants, stats)` and `BossEvents.DEFEATED(level, "amanita", origin, participants)`. The entity's own loot table `skylore_bosses:entities/amanita` drops mundane Act I goods (mushrooms, ink sacs, phantom membranes, experience bottles) at her body.

**Shadow artifacts.** The mod bakes no unique artifact items. The pack owns them by overriding `data/skylore_bosses/loot_table/gameplay/amanita/shadow_artifacts.json` in its datapack (or by listening to `ARTIFACTS_ROLLED`, e.g. to give different artifacts by origin). The shipped table is a placeholder (ink sacs, 50% ender pearl).

**Events (`net.teamaof.skylorebosses.bosses.amanita.api.AmanitaEvents`, Architectury events, server side).**

| Event | Signature | Use |
|---|---|---|
| `START_CHECK` | `(level, origin, trigger) -> EventResult` | gate on `met_amanita`; return `interruptFalse()` to refuse |
| `ENCOUNTER_STARTED` | `(level, origin, players)` | |
| `PHASE_CHANGED` | `(level, origin, from, to)` | |
| `LIGHT_PLACED` | `(level, origin, pos, player, emission)` | tutorial hints, stats |
| `LIGHTS_SNUFFED` | `(level, origin, cause, count)` | cause: snuff_pulse, full_snuff, closing_snuff, lamp_eater, hollow_bolt, spore_veil, bloom_slam, light_seeker_dash, command |
| `EXPOSED` | `(level, origin, light)` | closed → exposed |
| `DEEP_BLOOM` | `(level, origin, count)` | |
| `VICTORY` | `(level, origin, participants, EncounterStats)` | grant `amanita_bested` |
| `ARTIFACTS_ROLLED` | `(level, origin, player, drops)` | attach shadow artifacts |
| `RESET` | `(level, origin, reason)` | reason: command, abandoned, rematch, created, rebuild, removed, skip |
| `registerOriginResolver(Function<ServerPlayer, String>)` | | §13 |

**Advancements** (`skylore_bosses:amanita/...`): `enter_hollow` (root), `first_light` (first exposed hit of the fight), `relit` (P2 ended by relighting), `deep_bloom`, `forced_open`, `bloom_wilted` (challenge; also a `player_killed_entity` criterion), `no_deep_bloom` (hidden challenge).

---

## 12. Quest, stage and chapter wiring

| Hook | Owner | How |
|---|---|---|
| `met_amanita` gate | pack | `AmanitaEvents.START_CHECK`: refuse unless the trigger player has the `met_amanita` stage (Progressive Stages). The knocker shows *"The cap does not answer. Not yet."* |
| `tacz_authorized` gate | pack | not a fight gate. The fight works with any weapon; TACZ damage passes the same gate. If the pack wants the Act I combat spine to point here, make `amanita_stops_running` require `tacz_authorized` in QuestQueen. |
| `named_contacts` chapter, tile `amanita_stops_running` | pack | complete on `AmanitaEvents.VICTORY` (or the `bloom_wilted` advancement) |
| `defense` chapter | pack | tiles can hang off `first_light` ("light as a weapon"), `relit`, `forced_open` |
| Stage `amanita_bested` | pack | grant from `BossEvents.DEFEATED` with boss id `amanita`, or from `AmanitaEvents.VICTORY`, or with `victoryFunction` |
| Shadow artifacts | pack | loot table override or `ARTIFACTS_ROLLED` |
| Origin classification | pack | `registerOriginResolver` (reads Origins) or the scoreboard tags |

Example (KubeJS, server script):

```js
const AmanitaEvents = Java.loadClass('net.teamaof.skylorebosses.bosses.amanita.api.AmanitaEvents')
const EventResult = Java.loadClass('dev.architectury.event.EventResult')
AmanitaEvents.START_CHECK.register((level, origin, player) =>
  player.stages.has('met_amanita') ? EventResult.pass() : EventResult.interruptFalse())
AmanitaEvents.VICTORY.register((level, origin, players, stats) =>
  players.forEach(p => p.stages.add('amanita_bested')))
```

---

## 13. Origin asymmetry

The mod never reads Origins directly. A player is **Tenebris** or **Laevis** if a resolver registered with `AmanitaEvents.registerOriginResolver` returns an origin id listed in `tenebrisOrigins` / `laevisOrigins` (defaults `skylore:tenebris`, `skylore:laevis`), or if they carry the scoreboard tag `skylore_origin_tenebris` / `skylore_origin_laevis` (**TUNE ME**, config). Everyone else is "other". The light gate is identical for every origin: no origin skips it, and no origin needs a special item to satisfy it.

| Origin | Design pressure (implemented) | Forbidden solutions (respected) |
|---|---|---|
| **Tenebris** | The dark is home, and that is the problem: **Hollow Call.** Every second a Tenebris player spends in the dark (light < 7 at their eyes) within 16 blocks of her adds a stack (*"The hollow calls you (3 of 5). Stand in the light."*); light clears it; 5 stacks (**TUNE ME**) root them (Slowness III, 40 t), make them her priority `hollow_bolt` target, and add 60 threat. **Chill:** every snuff that reaches them also gives Weakness I for 100 t (Darkness alone would barely bother them). Pack photophobia synergy: a Tenebris player is pushed to stand in the light that hurts them under the pack's photophobia power (the "bleach" pressure), exactly when the fight wants them near her. Social pressure: in co-op, the Tenebris player is naturally the one who can see in the snuffed dark and relight braziers, while their allies' lights are what her adds and dashes hunt. | no removal of the gate, no damage in the dark, no auto-win in the dark, no skipping the encounter |
| **Laevis** | Sunlight fantasy inverted: the hollow is hostile and light economy is the whole fight. **Bare-handed ignition:** a Laevis player can light any brazier with an empty hand (*"The coal takes from your hand."*), so a Laevis player with nothing in the inventory can still relight the room after every snuff. The torch count is fair: six relightable braziers are always there, and snuffed torches drop back. | no soft-lock without Act I lights (the braziers and bare-hand ignition guarantee light) |
| **Others** | Baseline loop: torches, Spectrum lamps, Ars light glyphs, flint and steel for the braziers. | no origin required to win |

Enoki collectivism vs Tenebris solitude colours copy only (the pack's quest text), never mechanics.

---

## 14. Java implementation outline

**Key classes** are listed in §2. Tick cadence:

| Every | What |
|---|---|
| 1 t | `Hollow.tick`: phase logic, gate sample (`tickGate`), stall / relight / deep counters, add clocks; `AmanitaEntity.tick`: movement, facing, action slot, veils; adds' AI |
| 5 t | boss bar update |
| 10 t | dormant trigger check; deep-bloom regen |
| 20 t | census refresh (unless dirtied earlier), Tenebris call, cover regrowth, bar player sync |
| 30 t | Amanita stance re-pick (sooner when exposed) |
| 40 t | brazier integrity |

**Networking.** No new packets. Gate state for animation rides on synched entity data (`EXPOSED`, `STAGGERED`, `DEEP`, `ACTION`, `STAGE`); boss bars, titles, Darkness and particles are vanilla; screen shake and the "taken" fade use the core `SBNetwork.ScreenFx`. GeckoLib syncs triggered animations.

**Save / reload.** `Hollows` is SavedData (always dirty while any hollow exists). A `Hollow` saves phase, phase ticks, every counter (dark, deep exposed, deep healed, stall, clocks), flags (snuffed once, full snuff done, closing pending, relit, any deep, first light), hush / bared deadlines, stats, the Amanita UUID and HP, and the light ledger. Fighting hollows force-load their chunks, so a fight keeps running when players log out or walk off. Amanita saves her hollow origin; after a reload she resumes idle and `Hollow.resumeScripts` re-casts a full snuff (P2 or the P4 closer) that was interrupted by the save. If her entity is missing for 40 t she is respawned at the bloom bed with her saved HP. Adds save their hollow origin and bite count. Veils are not saved.

**Designer commands** (permission 2; `/skylorebosses amanita ...` or `/skyloreamanita ...`):

| Command | Does |
|---|---|
| `build [pos]` | place the test hollow (default 30 blocks above you) and register it |
| `register [pos]` / `unregister` | adopt an existing structure at an origin / forget the nearest hollow |
| `start` / `reset` | start the fight / reset to dormant |
| `skipphase` | P0 → P1; P1 → P2 (sets HP to 50%); P2 → P3; P3 → P4; P4 → forced open |
| `snuff [radius]` | snuff within radius of her (no radius: the whole hollow) + Darkness |
| `setlight <-1..15>` | override the light the gate reads (-1 = real light) |
| `spawnservants <eater\|spawn> [n]` | spawn adds at the vents |
| `attack <id>` | force an action now (telegraph kept) |
| `hold <true\|false>` | freeze her attack choice, the phase clocks and the add clocks |
| `lights` / `status` / `tp` | list the census with owners / describe the hollow and her / go to the entry pad |

### Verification

`tools/testing/t_amanita.py` drives a dev client through Marionette in a fresh void world: 85 checks, all passing. It covers the build and dormant state, walk-in start and lockdown, P0 immunity and teaching lines, real block light at her (glowstone beside her: light 14, x1.22, a 20-point hit took 24), the afterglow, the override, torch placement with the owner recorded, braziers (bare hand refused, Laevis bare hand, flint and steel), `snuff_pulse` on braziers and torches with drops and Darkness, lamp-eaters eating a reachable torch and ignoring a high one, hollow-spawn burning in light, the P1 → P2 clamp at exactly 50%, the sealed full snuff and its wave, the hush, the P2 relight, save and reload mid-fight, P4 threshold 10, deep regen, force-open and bared damage (x1.45: 28 from 20), the Tenebris call and root, the leash and the void rescue, every action's telegraph → active, measured lash and bloom_open damage, victory with trophy, artifact roll and advancements, adds sinking away, CLEARED, and the abandon reset. A Marionette namespace sweep checks every block, item, model and translation key.

---

## 15. Assets list

All placeholder-first: procedural atlas fallback, Codex CLI paintings, synthesized sounds. Everything regenerates from `bosses/amanita/tools`.

| Asset | Path | Notes |
|---|---|---|
| Models | `geo/entity/amanita/{amanita,lamp_eater,hollow_spawn,hollow_bolt}.geo.json` | Blockbench via the MCP; 193 / 46 / 35 / 7 cubes |
| Animations | `animations/entity/amanita/*.animation.json` | Amanita 27: idle (bloom open), wilt (lit), walk, dash, deep_idle, stagger, rise, bloom_open, lash_windup, lash, bolt_cast, veil_cast, snuff_windup, snuff, full_snuff_windup, full_snuff, howl_windup, howl, slam_windup, slam, dash_windup, smother, deep_bloom, hurt, recoil, bared, death. Lamp-eater 7, hollow-spawn 5, bolt 1 |
| Entity textures | `textures/entity/amanita/*.png` + `_glowmask.png` | 16-material `hollow` palette painted by Codex (cap, gills, stipe, volva, hyphae, skin, eye/gill/spore/ember glow...) |
| Block textures | `textures/block/amanita/` | wall, loam, ceiling, column side/top, gill shelf, brazier side/top lit and unlit, membrane (cutout), knocker |
| Item | `textures/item/amanita/hollow_bloom_cap.png` | Codex icon |
| Particles | `particles/{hollow_spore,gill_glow,snuff_smoke,veil_mist}.json` | shared sprite families; styles in `SBParticle` |
| Sounds | `sounds/amanita/*.ogg`, 45 ids | synthesized (`tools/blockbench/sounds.py`); subtitles in lang |
| Lang | `lang/en_us.json` | 136 keys: blocks, items, entities, bars, statuses, titles, case-file lines, gate lines, warnings, origin lines, knocker, advancements, subtitles |
| Data | blockstates, block/item models, loot tables, tags (`igniters`, `snuff_immune`, `mineable/axe`), 7 advancements | `gen_data.py` |

---

## 16. Balance sheet

Every value is in `[amanita]` of `skylore_bosses-server.toml` unless marked "code".

| Key | Default | Meaning | **TUNE ME** |
|---|---|---|---|
| `amanita.hp` | 600 | solo max HP | yes |
| `amanita.mpHpPerPlayer` / `mpHpCap` | 0.5 / 2.5 | co-op HP scaling | yes |
| `amanita.attackSpeed` | 1.0 | divides gaps and cooldowns | yes |
| `lightGate.threshold` | 7 | light at her to expose her | yes |
| `lightGate.deepThreshold` | 10 | P4 threshold | yes |
| `lightGate.countSkyLight` | false | count sky light | yes |
| `lightGate.darkMult` | 0.0 | dark damage multiplier | yes |
| `lightGate.brightBonus` | 0.25 | extra damage at light 15 | yes |
| `lightGate.baredMult` | 1.25 | forced-open multiplier | yes |
| `lightGate.afterglowTicks` | 10 | exposure after the light drops | yes |
| `lightGate.sourceMinEmission` | 6 | what counts as a source | yes |
| `lightGate.burstFraction` | 0.08 | burst interrupt | yes |
| `lightGate.recoilTicks` | 20 | sudden-light stagger | yes |
| `phases.p0Ticks` | 160 | P0 length | yes |
| `phases.snuffHp` | 0.5 | P2 HP threshold | yes |
| `phases.relightSources` | 3 | P2 → P3 relight target | yes |
| `phases.p2MaxTicks` | 600 | P2 timeout | yes |
| `phases.stallP1Ticks` / `stallP3Ticks` | 900 / 400 | dark stall before P4 | yes |
| `deepBloom.maxTicks` | 900 | closing snuff | yes |
| `deepBloom.breakTicks` | 80 | exposed ticks to force open | yes |
| `deepBloom.regen` / `regenCap` | 0.005 / 0.10 | regen per second / per bloom | yes |
| `deepBloom.baredTicks` | 100 | bared length | yes |
| `snuff.radius` / `deepRadius` | 8 / 12 | snuff_pulse radius | yes |
| `snuff.darknessTicks` / `fullDarknessTicks` | 160 / 200 | Darkness | yes |
| `snuff.drops` | true | snuffed lights drop | yes |
| `snuff.hushTicks` | 60 | howl hush | yes |
| `adds.eaterP1Ticks` / `eaterP3Ticks` | 500 / 300 | lamp-eater cadence | yes |
| `adds.eaterMaxP1` / `eaterMaxP3` | 2 / 3 | lamp-eater cap | yes |
| `adds.eaterBites` / `eaterChewTicks` / `eaterHp` | 3 / 30 / 12 | lamp-eater | yes |
| `adds.spawnP3Ticks` / `spawnMax` / `spawnHp` / `spawnDamage` / `spawnLightBurn` | 400 / 3 / 20 / 4 / 2 | hollow-spawn | yes |
| `attacks.lashDamage` | 7 | | yes |
| `attacks.boltDamage` | 6 | | yes |
| `attacks.veilDamage` | 1 / s | | yes |
| `attacks.snuffDamage` | 3 (P3/P4) | | yes |
| `attacks.howlDamage` | 2 (within 6) | | yes |
| `attacks.slamDamage` | 9 | | yes |
| `attacks.dashDamage` | 8 | | yes |
| `origins.callStacks` / `rootedTicks` / `chillTicks` | 5 / 40 / 100 | Tenebris | yes |
| `origins.laevisHandIgnite` | true | Laevis | yes |
| `hollow.dormantTimeoutTicks` | 1200 | abandon reset | yes |
| `hollow.coverRegenTicks` | 1200 | gill shelf regrowth | yes |
| `hollow.rematch` / `rematchDelayTicks` | true / 6000 | | yes |
| code: action timings, weights, cooldowns, gaps | §9 | `AmanitaAction`, `AmanitaEntity.finish` | yes |
| code: armour 4, speed 0.24, sizes | §5 | `AmanitaEntity.createAttributes`, `AmanitaEntities` | yes |

### Playtest watchlist

- **Torch economy.** Does a player who arrives with 16 torches run dry? Count torches lost to lamp-eaters (no drop) vs picked back up after snuffs. If players stop picking them up in the dark, consider `snuff.drops` going straight to the placer.
- **Room lighting cheese.** If a group lights all 27 x 27 to 7+ before the first snuff, she has nowhere dark to go. That is intended ("light the room" is a valid plan), but check that the 8-block snuff and the eaters keep it contested. Raise `snuff.radius` or eater cadence if it is trivial.
- **Co-op light coverage.** Is one player on lights and one on damage the natural split? Carrier-first dashes should pull the light carrier; check it doesn't feel unfair to them.
- **Snuff panic.** Full snuff + 200 t Darkness + a wave: does the relight feel tense or hopeless? Watch time-to-3-sources (target 10-20 s). The 600 t P2 timeout is the safety net.
- **Stall timer.** 45 s of P1 dark before deep bloom. New players still hunting for braziers may hit it; that is the lesson, but it should not happen twice to the same group.
- **Deep bloom exit.** 80 ticks at light 10+: is it readable that "more light" is the answer? The bar shows the force-open percentage and the needed light.
- **Origin splits.** Tenebris: is Hollow Call a nudge or a lockdown? (1 stack/s, 5 to root.) Laevis: does bare-hand ignition trivialise P2 (six braziers for free)? If so, cap it per snuff.
- **Dash reads.** Is the dash telegraph (20 t line) enough in Darkness?
- **TACZ.** Guns at range mean players may never need to be near her: the gate still needs the light on her, which keeps the lesson; check burst interrupt rates with automatic fire.

---

## 17. Canon and text rules checklist

- [x] Player-facing name is always **Amanita, the Hollow Bloom** (entity name, bossbar, advancements). Never Archevoker, never a generic "shadow mage".
- [x] No Calyx / infection / parasite / "the path" / Act V language anywhere (lang, titles, lore, subtitles).
- [x] Tenebris voice: dry, quiet, case-file notation ("[CASE FILE] Subject is untouchable in darkness."), terse action-bar facts. No cartoon-witch cackling, no taunting the player.
- [x] Sass targets the hollow cult and Tenebris isolationism, never the player (trophy lore: "Folded shut. It opens a little in the dark.").
- [x] The gate is never removed for Tenebris: their pressure lines are about the hollow calling them, not immunity.
- [x] Enoki flavour stays in the pack's copy; nothing requires Enoki.
- [x] Every teaching line says what to do: "Bring light to her", "Place your light before it lands", "Stand in the light".
- [x] No infection clock on this boss.

---

## 18. Open risks

| Risk | Mitigation / status |
|---|---|
| Client dynamic-lights mods breaking the gate | The gate reads only the server light engine; client light cannot count. Held lights instead make the player a dash target. Risk: players think their held torch lights her. The bossbar's "light N of 7 at her" corrects them. |
| Server dynamic-lights mods (if any write real light blocks) | would count, since they would be real light. Add their light block to `snuff_immune` only if it misbehaves. |
| Glow item frames / glowing ink cheese | emission 0 in vanilla: they do nothing. Modded "glowing decor" with real emission counts as light, which is fine. |
| Chunk unload | fighting hollows force-load their chunks; SavedData persists every counter; the entity is re-spawned from state if missing |
| Flight above the hollow | the hollow is roofed at +11 and leashes her to the interior; flying players inside are limited to the upper band, which the snuff still reaches |
| Multiplayer light desync | none possible: lights are blocks, the census and gate are server-side; the ledger is informational |
| Light engine lag | light updates settle within a tick or two; the 10-tick afterglow smooths a flicker at the threshold. A huge modded light update storm could delay exposure by a few ticks. |
| Structure ownership with the Islands agent | `HollowLayout` is the contract; `register` adopts a placed structure. The shipped structure must contain no emitting blocks and must keep the brazier offsets. Coordinate before final terrain. |
| Soft-lock with no lights | six relightable braziers with the igniter tag including the torch, and Laevis bare-hand ignition. A player with no igniter and no torch at all is in trouble: the pack's Act I loadout advice should say "bring flint and steel". Consider adding `minecraft:stick`-style ignition for all origins if playtests show it. |
| Snuff breaking pack blocks | anything emitting light in the interior can be broken; fill `#skylore_bosses:amanita/snuff_immune` for pack-critical light blocks |
| Veils not saved | a reload mid-veil removes it; acceptable (a 5 s effect) |

---

## Implementation order

1. **Void-box controller + block-light sampler + immunity gate + bossbar.** `Hollows`/`Hollow`, `HollowBuilder` (sealed dark box), `Lights.sample`, `damageMultiplier`, `AmanitaBossBar` with "light N of 7". Done.
2. **Amanita entity idle + path-to-dark + one attack (`hollow_bolt`) with full telegraph.** Stance scoring on the 2-block grid, leash, bolt with outline collision. Done.
3. **Light placement detection + vulnerability windows + P0-P1.** Place hook and ledger, afterglow, sudden-light interrupt, P0 script, teaching lines. Done.
4. **Snuff pulse + P2 → P3 loop + Darkness.** `Lights.snuffOne`, radius purge, full snuff, relight census, clamp at 50%. Done.
5. **Lamp-eater / hollow-spawn AI.** Eater targeting and reach, chew, burrow; spawn photophobia; cadences. Done.
6. **Full attack roster + P4 deep bloom.** All 10 actions, veils, slam band, dash, stall, deep regen, force-open, closing snuff, hush. Done.
7. **Arena template / structure hook + lockdown.** Braziers, columns, cover regrowth, membrane, knocker, register. Done; the shipped structure is the Islands agent's.
8. **Pack API, advancements, quest/stage wiring + origin playtests.** `AmanitaEvents`, origin resolver and tags, loot hooks, advancements. Done in code; origin playtests with real Origins powers are still owed (the Marionette test uses the scoreboard tags).
9. **Art / sound pass.** Blockbench models and animations, Codex textures, synthesized sounds are in as placeholders-plus; a hand-authored sound pass and a proper spore-glow core texture are the obvious next upgrades.
