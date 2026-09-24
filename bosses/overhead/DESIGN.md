# Overhead, Noven's Decommissioned Prototype: design package

Skylore Act II boss (Teknari industry). Ships as the `overhead` module of the `skylore_bosses` mod, custom Java, NeoForge 21.1.x on Minecraft 1.21.1, Java 21. Cataclysm's Harbinger was tonal reference only. Nothing here summons, subclasses or needs Cataclysm.

Every number marked **TUNE ME** is a first-pass value. All of them live in the `[overhead]` section of `skylore_bosses-server.toml` (§15 has the key for each), so playtest tuning needs no rebuild.

Status of this document: the module described here is implemented in `src/main/java/net/teamaof/skylorebosses/bosses/overhead/` and was exercised in a dev client through Marionette (see §13 "Verification"). Where the code and this document disagree, the code is the bug.

> *"Asset N3-0417. Status: decommissioned. Power: on."*

---

## 1. One-page fight bible

**Fantasy.** Noven the Third commissioned an automated artillery platform to guard a Teknari fabrication yard. It came in over budget and failed compliance review, so Noven wrote it off. Nobody filed the power-down ticket. Overhead still patrols the yard, still opens "threat neutralization" tickets against anything that walks in, and still logs every shot politely.

**Player fantasy.** Demolition under fire. You are not out-trading a boss; you are taking its yard apart while it shells you. You read the ground (red rings, amber rails, marked lanes), cross open concrete between crates, and put the four generator pylons out of service. Each pylon that dies drops the chassis into reach for ten seconds. When the grid is gone, it is a DPS race against the emergency re-arm.

**Win condition.** Overhead's HP reaches 0. Its HP only moves meaningfully while pylons are down, so the real win condition is "break pylons, spend the windows, finish it before it re-arms (or survive the re-arm and do it again)".

**Fail states.**
- Player death. Normal respawn. The yard stays locked; the gate console outside the south gate re-admits a participant to the entry pad. The fight continues for everyone else.
- Everyone leaves or dies and nobody is inside the yard for 60 s (**TUNE ME**). The yard resets to dormant: chassis despawned, HP restored, pylons restored, crates reprinted, gates open. Act II players should be able to walk away and retry cleanly; there is no partial-progress carry-over.
- Stalling. Not a fail state, a punishment. Sitting at 0 pylons without killing it triggers the emergency re-arm, and every later re-arm restores more pylons.

**Tone.** Dry corporate violence. The machine never insults you. Its lines are ticket numbers, incident reports, budget codes and warranty notes. The joke is always on Noven's paperwork and Teknari process, never on the player. No Calyx, no infection, no parasite language anywhere in this fight.

**Pedagogy.** First clear should teach "the towers matter" without a wiki: the chassis literally tells you on your first hit ("Grid shield absorbed 90% of that. Break the pylons."), the bossbar shows the grid, and the first break drops the chassis to the ground in front of you. The later "break the environment" bosses (Null Router, Act IV; Matris Calyx, Act V) reuse this language.

---

## 2. Mod architecture

Overhead is a `BossModule` inside the existing mod. A sibling jar was considered and rejected: the core (shared particles, sounds, `AnimFx`, screen shake, `BossEvents`, config sections, `/skylorebosses`) is already built for more bosses, and one jar is simpler for the pack to list.

| Item | Value |
|---|---|
| Mod id | `skylore_bosses` (module id `overhead`) |
| Package | `net.teamaof.skylorebosses.bosses.overhead` |
| Hard deps | NeoForge, Architectury API 13, GeckoLib 4.7 (already required by the mod) |
| Soft deps | none required. Immersive Aircraft ids appear in an optional tag. Mekanism, Create, Progressive Stages and QuestQueen are touched only by the pack, through events and datapacks. |
| License | MIT, same as the mod |

```
bosses/overhead/
├─ OverheadBoss.java            module entry: registries, config, events, client, commands (+ /skyloreoverhead alias)
├─ OverheadConfig.java          every TUNE ME value (server config)
├─ OverheadLocators.java        GENERATED: model locator table (muzzle, lens, bays, gatling, thrusters...)
├─ OverheadSoundIds.java        GENERATED: 36 sound ids
├─ api/OverheadEvents.java      stable pack hooks (§11)
├─ registry/                    OverheadBlocks, OverheadBlockEntities, OverheadEntities, OverheadItems
├─ block/                       GeneratorPylonBlock (+BE), PylonCasingBlock, YardConsoleBlock
├─ encounter/
│  ├─ OverheadYards.java        SavedData: every yard in a level, keyed by origin; ticks them
│  ├─ Yard.java                 one yard + its fight: phase machine, pylons, damage gate, re-arm, victory, bar
│  ├─ Phase, PylonState, Pylon  state types
│  ├─ YardLayout.java           geometry constants (pylon slots, leash box, lanes, cover)
│  ├─ YardBuilder.java          the mod-placed test yard, gates, pylon columns, crate reprint
│  ├─ OverheadBossBar.java      chassis bar + grid bar
│  └─ OverheadCommonEvents.java level tick, deaths, projectile and explosion hits on pylons
├─ entity/
│  ├─ OverheadEntity.java       the chassis: hover AI, target memory, action slot, every attack, damage hook
│  ├─ Action.java               attack roster data (ticks, cooldowns, weights, interrupt rules)
│  ├─ Ordnance.java             shells, missiles, bolts, flares, flak (one projectile class, synced kind)
│  ├─ Blast.java                Overhead's explosions (cover-aware, crate-only terrain damage, pylon friendly fire)
│  └─ Telegraph.java            long-range ground/air telegraph particles
├─ client/                      OverheadClient, OverheadRenderer, PylonRenderer, OrdnanceRenderer
└─ command/OverheadCommands.java
```

**Server vs client.** All logic is server-side. The client gets: synced entity data on the chassis (current action, stage, laser end point, brownout flag), block entity update packets for pylons (state + integrity fraction), the two vanilla boss bars, titles and action-bar lines, particles (server-sent telegraphs plus client animation keyframes), and the shared `SBNetwork.ScreenFx` shake. There are no Overhead-specific custom packets, which keeps the network surface small.

**What the pack still owns** (datapack or KubeJS, never Java):
- Stage grant `teknari_prototype_down` and the Mek-steel gate (§12).
- Quest retarget of `defeat_harbinger_optional` (§12).
- Loot override: `data/skylore_bosses/loot_table/entities/overhead.json` ships vanilla salvage; the pack replaces it with Mekanism/Create loot.
- Tags: `#skylore_bosses:overhead/breakable_cover` (blocks Overhead's blasts may destroy) and `#skylore_bosses:overhead/flight_vehicles` (vehicles that count as aircraft for flak and take double blast damage).
- `victoryFunction` in config: an mcfunction run as each participant on the kill.
- Structure placement of the real yard (§3).

---

## 3. Arena: the Teknari yard

### Footprint

Coordinates are offsets from the yard **origin**, the centre block of the floor. Players stand at `origin.y + 1`. North is −Z. All numbers are in `YardLayout.java`.

```
            z=-31  north wall, 5-wide gate (shut in lockdown)
     ┌──────────────────── ═════ ────────────────────┐
     │ P0(NW)             [cradle]             P1(NE) │   pylon cores at (±22, +1, ±22)
     │   ▣                                        ▣   │   5x5 hazard pads under each
     │        ▪▪                          ▪▪          │   ▪ approach crates (±14, ±14)
     │               ▪▪▪  stack  ▪▪▪                  │
     │  lane0  │  lane1   ┌─ring─┐   lane2  │  lane3  │   lane lines painted at x=-15, 0, +15
     │ stack   │         ▪│  ⊕   │▪         │   stack │   ⊕ origin; broken crate ring at r≈7
     │         │          └──────┘          │         │
     │        ▪▪          stack           ▪▪          │
     │   ▣                                        ▣   │
     │ P2(SW)           [entry pad]           P3(SE)  │   entry pad (0, +1, +26)
     └──────────────────── ═════ ────────────────────┘
            z=+31  south gate ── apron ── gate console (0, +1, +37)
```

| Element | Spec |
|---|---|
| Floor | 61 × 61 (`HALF = 30`), `yard_plating`, hazard border 2 wide, dashed hazard lane lines at x = −15, 0, +15 |
| Walls | ring at 31, 4 high, `yard_wall` (unbreakable). North and south gates 5 wide (`|x| ≤ 2`) |
| Pylons | cores at (±22, +1, ±22); slot 0 NW, 1 NE, 2 SW, 3 SE. Column = core + 4 invisible casings (5 tall) |
| Cover | 12 crate clusters (`YardLayout.COVER`): a broken ring around the centre, four 3-high stacks on the cardinal lanes, four 2×2 approach crates near the pylon pads. `yard_crate` only |
| Entry pad | (0, +1, +26), where re-admitted and rescued players land |
| Console | (0, +1, +37) outside the south gate, on a 7 × 7 apron |
| Cradle | open steel frame at the north end; the chassis rises out of it in P0 |
| Underside | 7 layers of tapered deepslate and stone so the yard reads as a sky island |

### Height bands

| Band | Height above floor | Used by |
|---|---|---|
| Brownout (window) | 2.5 (chassis bottom) | the 10 s after each pylon break; melee reach |
| P3 exposed | 4 – 8 | melee with a jump, easy ranged |
| P2 degraded | 11 – 16 | ranged |
| P1 shielded | 14 – 18 | ranged, mostly pointless (90% DR) |
| P4 re-arm | 16 – 20 | it retreats high so you deny pylons instead of DPSing |
| Leash | 2.5 – 30, `|x|,|z| ≤ 24` | hard clamp every tick |
| Ceiling | 32 | above this a player is in "airspace violation": only `flak_burst` targets them |

### Entry and lockdown

Walking into the trigger box (`|x|,|z| ≤ 28`, 1 to 20 above floor) starts the fight, as does using the console. Both go through `OverheadEvents.START_CHECK` first, so the pack can refuse (Mek-steel gate). On start the gates fill with `yard_shutter`, the chassis spawns in the cradle, pylons spin up, and chunks covering ±40 blocks are force-loaded until the fight ends.

A player outside the locked yard uses the console to be re-admitted to the entry pad. Nobody is locked *out* permanently, and nobody can be trapped: the gates open on reset and on victory.

### Void fall

A participant below `floor − 12` within 44 blocks of the origin is teleported to the entry pad, takes 4 damage, gets Slow Falling 3 s, and sees "Yard safety net engaged. Incident filed under 'weather'." The chassis cannot leave the leash box; if something carries it more than 60 blocks away it snaps back to the yard centre.

### Distant Horizons silhouette notes

- The walls (teal, 4 high) and the island underside are ordinary blocks, so DH LODs show them.
- Pylons and the chassis are GeckoLib renderers and are invisible in LODs. The structure should put a vanilla-block marker on each pad corner (iron bars mast with a lightning rod, 5 tall) so the four corners read from a distance. The test yard omits these to keep the pads clear for testing.
- Keep the yard on a clear sky island: a 61-block square with teal walls is a readable landmark on approach by aircraft.

### Ownership handoff (Skylore Islands)

| Piece | Owner |
|---|---|
| Worldgen placement of the real yard island, its terrain, approach bridges, loot chests around it | **Skylore Islands** agent (structure NBT + placement) |
| Encounter blocks, entities, the fight, the yard registry | this mod |
| Test yard for AI bring-up | this mod: `/skyloreoverhead build [pos]` |

The contract between them: the structure must place the same blocks at the same offsets as `YardBuilder` (at minimum the four pylon columns and a `yard_console`; floor, walls and crates may be restyled as long as crates stay in `#skylore_bosses:overhead/breakable_cover`). The mod then adopts it with `/skyloreoverhead register <origin>` (no blocks placed). A worldgen hook that auto-registers on structure placement is listed as an open item in §17; until the Islands agent confirms the structure id, registration is by command or by the pack's structure-placed script.

The fallback test box is the mod-placed yard itself; it is small enough (about 12k blocks) to build in one tick.

---

## 4. Phase flowchart

```
DORMANT ──(player enters trigger box, or console; START_CHECK allows)──► P0 LOCKDOWN
P0 ──(phaseTicks ≥ 160)──► P1 SHIELDED (4/4)
P1 ──(online < 4)──► P2 DEGRADED (1–3)
P2 ──(online = 0)──► P3 EXPOSED
P1 ──(online = 0, e.g. two simultaneous breaks)──► P3
P3 ──(exposeTicks reaches 0; 900 ticks)──► P4 EMERGENCY RE-ARM
P4 ──(online ≥ K)──► P2 (or P1 if K = 4)
P4 ──(1200 ticks and online < K)──► P3 (re-arm aborted, exposeTicks = 594)
any fighting phase ──(chassis HP ≤ 0)──► DEFEATED ──(140 ticks)──► CLEARED ──(rematch, 6000 ticks)──► DORMANT
any fighting phase ──(no participants for 1200 ticks, or /reset)──► DORMANT (full reset)
```

K (re-arm target) = min(4, 2 + rearmCount − 1): the first re-arm restores 2 pylons, the second 3, every later one all 4 (**TUNE ME**, `basePylons`).

| Phase | Pylons online | DR | Chassis height | Intent |
|---|---|---|---|---|
| P0 | spinning up 0 → 4 | 100% (immune) | rising 3 → 14 | yard seals, towers light up one by one, power-on pulse. "Those towers matter." |
| P1 | 4 | 90% | 14 – 18 | learn to break one pylon under light fire |
| P2 | 1 – 3 | 85 / 80 / 75%, 0% in a window | 11 – 16 | denser attacks, laser and strafe appear, windows after each break |
| P3 | 0 | 0% | 4 – 8 | full damage, desperation carpet, 45 s race |
| P4 | rebuilding | 100% for 3 s, then 50% | 16 – 20 | deny the rebuild or eat a new grid |

Timers are in ticks and count only while at least one participant is in the yard (the yard pauses when empty, then resets after the timeout).

---

## 5. Entity and component design

| Component | Kind | Why |
|---|---|---|
| `Yard` in `OverheadYards` | SavedData (per level, map of yards) | source of truth for phase, pylons, windows, timers, re-arm, participants. Survives relog, restart, chunk unload |
| `OverheadEntity` | `Monster` + `GeoEntity`, `skylore_bosses:overhead`, 6 × 3.5 hitbox, render scale 1.5 | the chassis. No gravity, no collision (`noPhysics`), leashed. One action slot. Persistent, never despawns |
| `GeneratorPylonBlock` + `GeneratorPylonBlockEntity` | block + GeckoLib BE, `skylore_bosses:generator_pylon` | the 3 × 3 × 5 tower model; mirrors the yard's pylon state for rendering; forwards melee hits |
| `PylonCasingBlock` | invisible full-collision block × 4 above each core | so the whole visible tower can be hit and shot |
| `Ordnance` | `Projectile` + `GeoEntity`, `skylore_bosses:overhead_ordnance` | shells, missiles, bolts, flares and flak share one class with a synced `Kind`. Own integration, no drag, so ballistic solves are exact |
| Carpet bomblets | scheduled impacts inside the chassis (no entities) | 24 – 36 impacts per carpet would be 36 entities; a list of (pos, tick) is cheaper |
| `Blast` | static helper | Overhead never calls vanilla explosions: no terrain grief, cover-aware damage |
| `YardConsoleBlock` | plain directional block | start / re-admit / cleared message |
| Boss bars | two `ServerBossEvent`s owned by the yard | chassis HP and grid integrity |
| Telegraph particles | server → per-player long-range particle packets | markers 60 blocks away still render |
| VFX on the model | GeckoLib particle and sound keyframes | muzzle flash, smoke, sparks, arcs, per animation |

Sub-parts (turret, howitzer, missile pods, gatling, lens, rotors) are **bones of one model**, not separate entities. The design never needs them to be hit separately, and multi-part entities would multiply sync and hitbox cost.

The chassis is a view like the pylons are: if it goes missing during a fight (killed by `/kill` counts as a kill; discarded, unloaded or lost counts as missing), the yard respawns it after 40 ticks with its saved HP.

---

## 6. Pylon system card

| | NW | NE | SW | SE |
|---|---|---|---|---|
| Slot | 0 | 1 | 2 | 3 |
| Core | (−22, +1, −22) | (+22, +1, −22) | (−22, +1, +22) | (+22, +1, +22) |
| Name in logs | Node NW | Node NE | Node SW | Node SE |

Registry ids: `skylore_bosses:generator_pylon` (core, block entity `skylore_bosses:generator_pylon`), `skylore_bosses:pylon_casing` (column).

### Break rules

| Property | Value |
|---|---|
| Hardness / blast resistance | −1 / 3,600,000 (unbreakable as a block). Pylons die by integrity, never by mining. Drills, Mekanism tools and vanilla explosions cannot remove the block. |
| Tool tier | none needed. Any hit counts. |
| Integrity | 150 per pylon solo, × (1 + 0.35 per extra participant), cap × 2.0 (**TUNE ME**). Fixed at fight start. |
| Melee | `max(1, attack_damage) × (0.2 + 0.8 × charge²)`, × 1.5 on a falling crit. Spam-clicking is heavily reduced, same curve as vanilla combat. Measured in game: a charged iron sword does 6, spam clicks 2 – 4. |
| Player projectile (arrow, trident, anything owned by a player) | 6 integrity per hit on the core or a casing (**TUNE ME**) |
| Player / environment explosion (TNT, creepers, Create cannons, anything firing `ExplosionEvent.Detonate`) | 30 at the core, falling to 30% at 5 blocks (**TUNE ME**) |
| Overhead's own blasts | 50% of their player damage (friendly fire, **TUNE ME**): a howitzer shell landing on a pad does about 7. Kiting shells onto pylons is legitimate play. |
| Overcharge repair | +20 when `pylon_overcharge` discharges on that pylon |

### States

| State | Meaning | Visual |
|---|---|---|
| ONLINE | counts toward the shield; takes integrity damage | `online` loop: spinning cyan core, pulsing copper coils, arcs |
| OFFLINE | destroyed; inert until a re-arm picks it | `break` then `offline` hold: core dropped and dim, cage bent, column leaning, smoke and sparks |
| REBUILDING | P0 spin-up and P4 re-arm. Integrity regenerates; damage pushes it back; reaching max makes it ONLINE | `rebuild` loop: blinking lamps; the core bone scales from 0.2 to 1 with integrity |

"Under attack" is not a state; it means integrity damage in the last 60 ticks. It doubles `pylon_overcharge`'s weight for that pylon and feeds threat to the attacker.

### Multiplayer ownership

Every player who damages a pylon joins its contributor set. The final blow is the "breaker". `OverheadEvents.PYLON_BROKEN` carries both, so the pack can credit anyone. Threat (targeting weight) gets 1.5 × integrity damage dealt, so the player breaking towers draws fire unless a partner out-threats them. That is the co-op play: one draws, one breaks.

### What Overhead does about it

- Any player within 4.5 horizontal blocks of an ONLINE or REBUILDING pylon unlocks `pylon_overcharge` on that pylon (weight 6, or 12 while it is under attack).
- Breaking a pylon interrupts any interruptible action, staggers the chassis for 40 ticks (**TUNE ME**) and drops it to the brownout band.

### Re-arm sequence

1. P3 expose timer ends. `rearmCount++`. K = min(4, 2 + rearmCount − 1).
2. OFFLINE pylons are sorted by break order (first broken first). The first K become REBUILDING at 0 integrity.
3. Pylon k starts regenerating at `now + 80 + k × 100` ticks (80 = shield pulse; 100 = stagger, **TUNE ME**).
4. Regeneration is `max / 300` per tick (15 s from empty, **TUNE ME**). Damage subtracts from it.
5. As soon as K pylons are ONLINE, the re-arm completes: leftover rebuilding pylons go back to OFFLINE; phase becomes P2 (P1 if K = 4).
6. If 1200 ticks pass first, the re-arm aborts: rebuilding pylons drop to OFFLINE and P3 resumes with 594 ticks on the clock. A team that denies the rebuild gets a second exposed window.

Route planning follows from rule 2: the first pylon you break is the first to come back. On a second clear, break the pylon you least want to walk to again last.

---

## 7. Damage-gate math

```
dr(phase, online, window):
    P0, DORMANT, CLEARED        -> 1.00
    P4 during shield pulse      -> 1.00                 (60 ticks after the pulse fires)
    P4                          -> REARM_DR     = 0.50
    P3, DEFEATED                -> 0.00
    P1/P2 with online = 0       -> 0.00
    P1/P2 with window > 0       -> WINDOW_DR    = 0.00
    P1/P2                       -> DR[online]   = {4: 0.90, 3: 0.85, 2: 0.80, 1: 0.75}

applied = incoming × (1 − dr) × (VULNERABLE_MULT = 1.25 if the chassis is venting)
```

All constants are **TUNE ME** (`[overhead.gate]`). Code: `Yard.damageReduction()` and `OverheadEntity.hurt()`. `DamageTypeTags.BYPASSES_INVULNERABILITY` sources (`/kill`, void) skip the gate so designers can always end a fight.

| Rule | Value |
|---|---|
| Window length | 200 ticks after each break (**TUNE ME**). A second break during a window resets it to 200; windows do not stack. |
| Window chassis | brownout: sags to 2.5 blocks, sparks, 40-tick stagger, attack gaps × 1.5, no laser or carpet |
| Full expose | 0 pylons: no DR from the grid, for 900 ticks (**TUNE ME**) |
| Vent bonus | +25% damage during `laser_sweep` and `desperation_carpet` recovery (30 and 60 ticks) |
| Damage stagger | 10% of max HP taken inside 40 ticks while an interruptible attack is still telegraphing cancels it and staggers 30 ticks, then 200 ticks of lockout |
| Feedback | a player whose hit is reduced by 50% or more sees "Grid shield absorbed N% of that. Break the pylons." (at most once per 5 s) and a cyan shield burst |

Verified in game (20 damage per hit, chassis armour 4): P1 took 2, window 19, 3 pylons after the window 3, P3 20, P4 during pulse 0, P4 after pulse 9 to 10.

**Throughput sketch (solo, TUNE ME watch).** HP 600. Four windows × 10 s at a sustained 10 DPS ≈ 400. P3 45 s at 10 DPS ≈ 450. A competent solo player kills in the first cycle with margin; a player who wastes windows sees at least one re-arm. Chip damage at 90% DR is about 1 DPS, so ignoring pylons takes ten minutes, which is the point.

---

## 8. Bossbar and scoring

Two bars, shown to every participant (players inside the yard volume), refreshed every 5 ticks, membership synced every second.

| Bar | Progress | Colour | Title |
|---|---|---|---|
| Chassis | Overhead HP / max | white P0, blue shielded, yellow window, red exposed, purple re-arm | `Overhead, Noven's Decommissioned Prototype  \|  <status>` |
| Yard Grid | Σ(integrity / max of non-offline pylons) / 4, `NOTCHED_20` (5 notches per pylon) | green | `Yard Grid` |

Status strings: "Powering on. Grid spinning up.", "Grid 3/4. Shield absorbing 85%", "GRID FAULT. Shield down for 7s", "Chassis exposed. Re-arm in 31s", "Emergency re-arm: 1/2 nodes", "Ticket closed". The title only changes when the text changes, so it does not spam packets.

Co-op players all see the same bars. The grid bar is how the player drawing fire knows the breaker is making progress. HP scales × (1 + 0.5 per extra participant), cap × 2.5 (**TUNE ME**), fixed at spawn.

---

## 9. Per-phase action catalogs

### How the AI picks

One action slot: IDLE → TELEGRAPH → ACTIVE → RECOVERY → IDLE. Nothing overlaps; there are no shared locks because only one attack can run. After RECOVERY the chassis waits a **global gap**: P1 40, P2 25, P3 15, P4 30 ticks, × 1.5 during a window, ÷ `attackSpeed` (**TUNE ME**). Then it builds a weighted bag from every attack whose phase weight > 0, whose own cooldown has expired and whose condition holds, and draws one. An empty bag retries in 10 ticks.

| Attack | P0 | P1 | P2 | P3 | P4 | Own cooldown | Condition |
|---|---|---|---|---|---|---|---|
| `power_on_pulse` | script | | | | | none | P0 tick 100 only |
| `howitzer_lob` | | 5 | 4 | 3 | 3 | 80 | a target below the ceiling |
| `missile_salvo` | | 3 | 3 | 3 | 2 | 160 | a target |
| `laser_sweep` | | | 3 | 3 | | 220 | a target, no window |
| `strafe_barrage` | | | 3 | 2 | | 200 | a target |
| `suppression_flare` | | 2 | 2 | | 2 | 400 | an unpainted target |
| `pylon_overcharge` | | 6 | 6 | | 4 | 240 | a player on an ONLINE/REBUILDING pad; × 2 if that pylon is under attack |
| `desperation_carpet` | | | | 4 | 3 | 400 | a target, no window; in P3 only after 200 ticks of P3 or below 50% HP |
| `rearm_shield_pulse` | | | | | script | none | P4 entry only |
| `flak_burst` | | 8 | 8 | 8 | 8 | 100 | an airborne target |

**Targets** are participants who are alive, not spectators and not in creative. A target above the ceiling (floor + 32) can only be picked by `flak_burst`. **Threat** per player: +1 per point of damage dealt to the chassis (after DR), +1.5 per integrity point dealt to pylons, +40 when painted by a flare; decays 2% per second.

**Airborne** means: off the ground and more than 10 blocks above the floor for 20+ ticks, or elytra flying, or riding an entity in `#skylore_bosses:overhead/flight_vehicles`.

**Idle and reposition, all phases.** Every 60 – 100 ticks the chassis picks a hover point on a ring 6 – 18 blocks from the yard centre, at a random height inside its phase band, and flies there at the phase speed (P0 0.15, P1 0.25, P2 0.3, P3 0.4, P4 0.3, brownout 0.35 blocks/tick). It yaws toward its current target at 6°/tick. Every tick the position is clamped to the leash box (§3); it never leaves the yard and never goes below 2.5 blocks over the floor, so it can never fall into the void.

**Interrupt rules.** Each attack is NEVER, TELEGRAPH (cancelled only before it fires) or ANY. Interrupt sources: a pylon breaking, and the 10% HP burst stagger (telegraph only). Cancelling puts the attack on half its cooldown and sets a 20-tick gap.

---

### P0 Approach / Lockdown

Legal actions: `power_on_pulse` only (scripted). The chassis is immune and rises from the cradle toward the centre (3 → 14 blocks at 0.1/tick, reaching height by tick 120). Pylons spin up at ticks 20, 50, 80 and 110 (30 ticks each). At tick 160 → P1.

#### Attack: power_on_pulse

| Field | Content |
|---|---|
| Id | `overhead:power_on_pulse` (`Action.POWER_ON_PULSE`) |
| Display name | Power-on pulse |
| Phases | P0 |
| Unlock condition | scripted by the yard at P0 tick 100 |
| Weight / priority | not in the bag; forced |
| Cooldown | none; fires once per fight |
| Range / positioning | radius 20 horizontal from the chassis core; chassis wherever its rise has reached (about 13 blocks up); no facing needed |
| Telegraph | 30 ticks: cyan arc ring on the floor under the chassis, radius growing 4 → 20 (redrawn every 5 ticks); `overhead.power.charge` whine; title "OVERHEAD / Status: decommissioned. Power: on." already on screen from tick 0 |
| Wind-up | the same 30 ticks; chassis keeps rising; cannot cancel |
| Active | 1 tick: pulse |
| Recovery | 30 ticks; not vulnerable (immune in P0 anyway) |
| Hit resolution | 0 damage. Push 1.2 away horizontally + 0.4 up. Players with a solid block between the chassis core and their eyes are not pushed. `power_pulse` animation, shield particle burst |
| Projectile / AoE specs | instant radial AoE, r 20, line-of-sight gated |
| Interruptibility | never |
| Counterplay | nothing to survive. It exists to show, safely, that crates stop Overhead's effects. Players behind the entry-pad crates feel nothing. |
| Multiplayer notes | affects every participant in radius |
| Failure / edge cases | no participants in radius: plays anyway. Chassis missing at tick 100: skipped (the respawn happens by tick 140 and P1 starts on time) |

---

### P1 Shielded artillery

Legal actions: `howitzer_lob`, `missile_salvo`, `suppression_flare`, `pylon_overcharge`, `flak_burst`. Hover 14 – 18, gap 40. DR 90%. Exit: any pylon break → P2.

#### Attack: howitzer_lob

| Field | Content |
|---|---|
| Id | `overhead:howitzer_lob` |
| Display name | Howitzer lob |
| Phases | P1, P2, P3, P4 |
| Unlock condition | P1 entry |
| Weight / priority | P1 5 (highest non-conditional weight in P1) |
| Cooldown | 80 own; global gap 40 after recovery |
| Range / positioning | any target in the yard below the ceiling; fires from the `muzzle` locator on the underslung turret; hover band 14 – 18; yaws toward the target but does not need to face it |
| Telegraph | 20 ticks: `howitzer_load` animation (barrel elevates, recoil back), `overhead.howitzer.load` clank; red double ring (r 4 and r 2) on the ground under the target, redrawn every 4 ticks. Then the shell itself draws a red r 4 ring at its impact point every 4 ticks for its 40-tick flight. Total ground warning: 60 ticks |
| Wind-up | 20 ticks; chassis keeps its hover; cannot cancel into another attack |
| Active | 10 ticks. Tick 0: one shell. `howitzer_fire` animation, muzzle flash, `overhead.howitzer.fire` |
| Recovery | 20 ticks; not vulnerable |
| Hit resolution | explosion damage (source: Overhead). 14 at the centre, linear falloff to 30% (4.2) at r 4. × 0.25 if a solid block is between the blast (+0.6 up) and the target's centre. Push 0.6 × falloff horizontal + 0.25 up. Destroys `breakable_cover` blocks within 2.5. Pylons within r 5 take 50% as integrity damage |
| Projectile / AoE specs | 1 shell, gravity 0.05/tick, no drag, exact ballistic solve for a 40-tick flight to the aim point. Aim = target's feet, **no lead** in P1, snapped to the first solid block below (30-block scan), clamped to the floor. Blast r 4 |
| Interruptibility | telegraph only (pylon break, 10% burst). Shells already fired always land |
| Counterplay | leave the ring during the 2 s flight (sprinting covers 11 blocks). Stand next to a crate so the crate is between you and the impact for 25% damage |
| Multiplayer notes | highest threat 70% of the time, random participant 30% |
| Failure / edge cases | target dead or gone when the shot fires: no shell. Target on a crate: ring and impact on the crate top. Target airborne: the shell goes to the ground below (flak handles air). Chunk edge: yard chunks are force-loaded |

Measured: a stationary player one block off the impact point took 11.8 (formula: 14 × (1 − 0.7 × 0.9 / 4) = 11.8).

#### Attack: missile_salvo

| Field | Content |
|---|---|
| Id | `overhead:missile_salvo` |
| Display name | Missile salvo |
| Phases | P1, P2, P3, P4 |
| Unlock condition | P1 entry |
| Weight / priority | P1 3 |
| Cooldown | 160 own; gap 40 |
| Range / positioning | any target; launches from `bay_l` / `bay_r` on the side pods; hover band 14 – 18 |
| Telegraph | 30 ticks: `missile_open` (bay doors swing open, pods lift), `overhead.missile.open`; the **targeted player alone** hears a lock beep (`overhead.missile.lock`) and gets "MISSILE LOCK. Get behind cover." in red on the action bar |
| Wind-up | 30 ticks; keeps hovering; cannot cancel |
| Active | 4 missiles × 4 ticks = 16 ticks: missile k at tick 4k, alternating left and right bay, `overhead.missile.launch` each |
| Recovery | 20 ticks; `missile_close` animation; not vulnerable |
| Hit resolution | explosion, 6 at centre, falloff to 30% at r 2.2, cover × 0.25, destroys cover within 1.5, 50% friendly fire to pylons |
| Projectile / AoE specs | launch velocity 0.3 outward + 0.6 up ± 0.1 random; unguided for 10 ticks, then homes at **4°/tick** on the target's centre at 0.9 blocks/tick (turn circle radius about 13 blocks); explodes on any block or entity (never Overhead or other ordnance); self-detonates at 100 ticks. Smoke trail |
| Interruptibility | telegraph only; launched missiles fly on |
| Counterplay | put a crate between you and the pod before the boost ends. At 4°/tick a missile cannot turn inside a crate-hugging sidestep. Moving perpendicular at the last second also works |
| Multiplayer notes | a painted player first; otherwise highest threat. All four missiles chase one target |
| Failure / edge cases | target dies mid-flight: missiles continue straight and detonate on terrain or timeout. Target leaves the yard: same. Missiles cannot hit the chassis |

Measured: 8.6 total to a stationary player in the open (some misses and edge hits of a possible 24).

#### Attack: suppression_flare

| Field | Content |
|---|---|
| Id | `overhead:suppression_flare` |
| Display name | Suppression flare |
| Phases | P1, P2, P4 |
| Unlock condition | P1 entry; needs at least one unpainted target |
| Weight / priority | P1 2 |
| Cooldown | 400 own; gap 40 |
| Range / positioning | any target; fired from the top `flare` rack |
| Telegraph | 20 ticks: `overhead.flare.launch`. The flare is a bright ember-and-red streak during its flight |
| Wind-up | 20 ticks |
| Active | 30 ticks: tick 0 fires the flare (`flare_fire` animation) on a 30-tick arc to 8 blocks above the target; it bursts at fuse |
| Recovery | 10 ticks |
| Hit resolution | no damage. Every participant within 12 of the burst with line of sight gets Glowing 160 ticks and is **painted** for 160 ticks (+40 threat, missiles target painted players first and turn 2°/tick harder at them). Anyone looking at the burst (look · direction > 0.6) also gets Blindness 30 ticks. "You are painted. Overhead is aiming at you." |
| Projectile / AoE specs | flare gravity 0.03/tick, exact 30-tick solve; burst sphere r 12, LOS-gated |
| Interruptibility | telegraph only |
| Counterplay | look away from the streak and stay under a crate lip. In co-op the painted player becomes the bait while the other breaks a pylon |
| Multiplayer notes | aims at the highest-threat unpainted player; the burst paints everyone near them |
| Failure / edge cases | target gone at fire time: no flare. Flare hitting a block early bursts there |

#### Attack: pylon_overcharge

| Field | Content |
|---|---|
| Id | `overhead:pylon_overcharge` |
| Display name | Pylon overcharge |
| Phases | P1, P2, P4 |
| Unlock condition | a player within 4.5 horizontal blocks of an ONLINE (P1, P2) or ONLINE/REBUILDING (P4) pylon core, between 1.5 below and 4 above its base |
| Weight / priority | 6; × 2 when that pylon took damage in the last 60 ticks. It is the most likely response to someone working on a pylon |
| Cooldown | 240 own; gap 40 |
| Range / positioning | any distance; the lens links to the chosen pylon's top |
| Telegraph | 40 ticks: cyan arc line from the chassis lens to the pylon top every 2 ticks; cyan r 4.5 ring on the pad every 4 ticks; the pylon plays `overcharge` (lamps flash, core swells); `overhead.overcharge.charge`; pad players get "Node overcharging. Get off the pad!" |
| Wind-up | 40 ticks; chassis keeps hovering |
| Active | 1 tick: discharge (`overhead.overcharge.discharge`, red beam lens → pylon, arc burst on the pad) |
| Recovery | 20 ticks; not vulnerable |
| Hit resolution | every player on that pad at discharge: 10 indirect magic damage (ignores armour), push 1.5 away from the core + 0.5 up, Slowness II 40 ticks. The pylon regains 20 integrity (up to its max) |
| Projectile / AoE specs | instant cylinder, r 4.5, height −1.5 to +4 |
| Interruptibility | any time: if the pylon breaks during the telegraph the overcharge is cancelled |
| Counterplay | the arc is the cue to step off the pad for 2 s. Or finish the pylon inside those 40 ticks. Hitting from 4.5+ blocks away with a bow avoids it entirely |
| Multiplayer notes | picks the pad with someone on it, preferring the one under attack; hits everyone on that pad |
| Failure / edge cases | pad empty at discharge: no damage, repair still applies. Picked pylon broken before discharge: cancelled |

Measured: exactly 10 damage and Slowness II to a player on the pad.

#### Attack: flak_burst

| Field | Content |
|---|---|
| Id | `overhead:flak_burst` |
| Display name | Flak burst |
| Phases | P1, P2, P3, P4 |
| Unlock condition | an airborne target (§9 intro) |
| Weight / priority | 8. With an aircraft in the yard it dominates the bag |
| Cooldown | 100 own; gap 40 |
| Range / positioning | any airborne participant, including above the ceiling; fired from the nose gatling |
| Telegraph | 20 ticks: lock beep to the target (higher pitch than missiles) |
| Wind-up | 20 ticks |
| Active | 13 ticks: rounds at ticks 0, 6, 12 (`overhead.flak.fire`) |
| Recovery | 10 ticks |
| Hit resolution | explosion r 3, 6 at centre, falloff, × 2 against `flight_vehicles` entities; no cover destruction |
| Projectile / AoE specs | speed 1.6/tick, no gravity, aimed at target centre + target velocity × flight time (linear lead); proximity fuse 3 blocks from the target, time fuse at flight + 4 ticks |
| Interruptibility | never |
| Counterplay | land, or change heading hard during the 20-tick lock; the lead assumes straight-line motion |
| Multiplayer notes | the first airborne participant |
| Failure / edge cases | target lands during the lock: rounds still fire at the predicted point. Target dismounts: rounds keep their fuse |

---

### P2 Degraded grid

Legal actions: `howitzer_lob`, `missile_salvo`, `laser_sweep`, `strafe_barrage`, `suppression_flare`, `pylon_overcharge`, `flak_burst`. Hover 11 – 16, gap 25. DR by count, 0 during windows. Exits: 0 online → P3.

#### Attack: howitzer_lob

Identical to P1 `howitzer_lob`, with these overrides:
- Weight 4. Gap after 25.
- Active 20 ticks: **two shells**, at ticks 0 and 10.
- Aim **leads 50%** of the target's horizontal velocity × 40 ticks, capped at 6 blocks.
- Shell k lands at aim + (k − 0.5) × 3 blocks along the chassis → target line (a short walking pair).

#### Attack: missile_salvo

Identical to P1 `missile_salvo`, with these overrides: weight 3, **6 missiles** (active 24 ticks), homing **5°/tick** (7° at a painted target).

#### Attack: laser_sweep

| Field | Content |
|---|---|
| Id | `overhead:laser_sweep` |
| Display name | Laser sweep |
| Phases | P2, P3 |
| Unlock condition | P2 entry; not during a window |
| Weight / priority | 3 |
| Cooldown | 220 own; gap 25 |
| Range / positioning | the sweep line is 36 blocks long, centred on the target, perpendicular to the chassis → target direction, clamped to the floor. The chassis **anchors** (stops moving) for telegraph and active and pitches toward the line |
| Telegraph | 40 ticks: `laser_charge` (lens swells, embers), `overhead.laser.charge`; a red dotted line on the floor from end A to end B (1-block spacing, redrawn every 4 ticks) and a faint red guide from the lens to A; everyone reads "Sensor lens charging. Watch the red line." |
| Wind-up | 40 ticks; anchored; cannot cancel into another attack |
| Active | 50 ticks: the beam's ground point moves linearly A → B. The beam is drawn client-side from the lens to its synced end point; embers at the end |
| Recovery | 30 ticks: `laser_vent` (smoke from the lens); **vulnerable, damage × 1.25** |
| Hit resolution | every 5 ticks, any player whose box (inflated 0.6) touches the lens → end segment takes 4 indirect magic and burns 3 s. Typical contact is 2 – 3 ticks: 8 – 12 + fire |
| Projectile / AoE specs | hitscan segment; stops at the first solid block along it, so crates block it |
| Interruptibility | any time (pylon break cancels mid-sweep) |
| Counterplay | step off the line (it does not track after the telegraph), or crouch behind a crate on the chassis side. Then punish the vent |
| Multiplayer notes | random target, preferring players with line of sight |
| Failure / edge cases | target leaves the line: the sweep still runs where it was telegraphed. Line clamped at the walls. No target at pick time: not chosen |

Measured: 14.6 to a player standing on the line (contacts plus burning).

#### Attack: strafe_barrage

| Field | Content |
|---|---|
| Id | `overhead:strafe_barrage` |
| Display name | Strafe barrage |
| Phases | P2, P3 |
| Unlock condition | P2 entry |
| Weight / priority | 3 |
| Cooldown | 200 own; gap 25 |
| Range / positioning | a straight rail across the whole leash box (48 blocks), along X or Z (random), passing over the target's line; the chassis flies it at up to 0.9 blocks/tick, 3 blocks below its current height (never under floor + 6), starting from the nearer end |
| Telegraph | 30 ticks: `strafe_spinup` (gatling barrels spin up, nose dips), `overhead.strafe.spinup`; an **amber** dotted rail on the floor under the flight path (redrawn every 3 ticks) |
| Wind-up | 30 ticks; still hovering |
| Active | 60 ticks: flies the rail; a bolt every 3 ticks at the target (20 bolts), `overhead.strafe.fire`, muzzle flashes |
| Recovery | 20 ticks |
| Hit resolution | 3 per bolt (mob projectile), no blast, no cover damage |
| Projectile / AoE specs | bolt speed 2.2/tick, no gravity, ± 3° spread, 40-tick life |
| Interruptibility | any time |
| Counterplay | move perpendicular to the rail and keep a crate between you and the pass. The chassis passes low and close, which is a free ranged window |
| Multiplayer notes | nearest player (horizontal) |
| Failure / edge cases | target dies: the chassis still flies the rail without firing. Rail clamped to the leash |

Measured: 15 (five bolts) to a player standing in the open.

#### Attack: suppression_flare

Identical to P1 `suppression_flare`, with these overrides: weight 2, gap after 25.

#### Attack: pylon_overcharge

Identical to P1 `pylon_overcharge`, with these overrides: gap after 25; only ONLINE pylons qualify (OFFLINE ones cannot be overcharged).

#### Attack: flak_burst

Identical to P1 `flak_burst`, with this override: gap after 25.

---

### P3 Exposed chassis

Legal actions: `howitzer_lob`, `missile_salvo`, `laser_sweep`, `strafe_barrage`, `desperation_carpet`, `flak_burst`. Hover **4 – 8** at 0.4/tick, gap 15. DR 0. 900-tick clock → P4. Title "All nodes offline / Chassis continuing under protest."

#### Attack: howitzer_lob

Identical to P1 `howitzer_lob`, with these overrides:
- Weight 3. Gap after 15.
- Active 20 ticks: **three shells** at ticks 0, 8 and 16.
- Aim **leads 100%** (cap 6 blocks); shells land at −3, 0, +3 blocks along the chassis → target line, a walking barrage through the target.
- Fired from 4 – 8 blocks up, so flight arcs are flat; the 40-tick flight and rings are unchanged.

#### Attack: missile_salvo

Identical to P1 `missile_salvo`, with these overrides: **8 missiles** (active 32 ticks), homing **6°/tick** (8° at a painted target), gap after 15. From low altitude the boost arc is short, so cover must be used earlier.

#### Attack: laser_sweep

Identical to P2 `laser_sweep`, with these overrides: active **60** ticks (slower sweep, longer exposure), gap after 15. From 4 – 8 blocks up the beam is almost horizontal near the chassis, so crate cover works across more of the yard.

#### Attack: strafe_barrage

Identical to P2 `strafe_barrage`, with these overrides: weight 2, gap after 15. The rail height floor is still floor + 6, so from the P3 band it flies at 6 – 8 blocks.

#### Attack: desperation_carpet

| Field | Content |
|---|---|
| Id | `overhead:desperation_carpet` |
| Display name | Desperation carpet |
| Phases | P3, P4 |
| Unlock condition | P3 after 200 ticks in P3, or at once below 50% HP |
| Weight / priority | 4 |
| Cooldown | 400 own; gap 15 |
| Range / positioning | the whole floor, split into 4 lanes 15 blocks wide along Z (x = −30..−16, −15..−1, 0..14, 15..30; the painted hazard lines mark the boundaries) |
| Telegraph | 60 ticks: `carpet` animation (bay doors fully open, nose up), `overhead.carpet.klaxon`; the marked lanes fill with red dotted lines every 4 blocks (2-block spacing, every 5 ticks); "Carpet bombing. Get into an unmarked lane." |
| Wind-up | 60 ticks; keeps its hover; cannot cancel |
| Active | 40 ticks. Tick 0 schedules 12 impacts per marked lane at random points, each landing 10 – 40 ticks later. Each impact shows a 10-tick falling streak (embers and smoke) before it lands |
| Recovery | 60 ticks; **vulnerable, damage × 1.25** |
| Hit resolution | per impact: explosion r 2.5, 8 at centre, falloff, cover × 0.25, destroys cover within 1.5 |
| Projectile / AoE specs | 2 marked lanes (24 impacts); **3 lanes** (36) when P3 HP is below 25%. Lanes with the most players are marked first. Never all 4: one lane is always safe |
| Interruptibility | never |
| Counterplay | read the lanes and cross a painted line into an unmarked lane during the 3 s. Then spend the 3 s vent on damage |
| Multiplayer notes | spreading out does not help: occupied lanes are marked first. Group in the safe lane |
| Failure / edge cases | a player on a crate in a marked lane is still hit (impacts land on the floor; blasts reach 2.5). The chassis dying mid-carpet clears pending impacts |

#### Attack: flak_burst

Identical to P1 `flak_burst`, with this override: gap after 15.

---

### P4 Emergency re-arm

Legal actions: `rearm_shield_pulse` (scripted at entry), `howitzer_lob`, `missile_salvo`, `suppression_flare`, `pylon_overcharge`, `desperation_carpet`, `flak_burst`. Hover **16 – 20**, gap 30. DR 100% for the pulse, then 50%. Titles "Emergency re-arm / Authorized. Budget code: none."

#### Attack: rearm_shield_pulse

| Field | Content |
|---|---|
| Id | `overhead:rearm_shield_pulse` |
| Display name | Re-arm shield pulse |
| Phases | P4 |
| Unlock condition | scripted on P4 entry |
| Weight / priority | forced; not in the bag |
| Cooldown | none; once per re-arm |
| Range / positioning | radius 12 from the chassis core |
| Telegraph | 20 ticks: `overhead.rearm.siren` (audible yard-wide), cyan r 12 ring on the floor under the chassis every 5 ticks, screen shake 20 ticks |
| Wind-up | 20 ticks |
| Active | 60 ticks. Tick 0: pulse (`shield_pulse` animation, shield burst). For all 60 ticks the chassis is **immune** |
| Recovery | 0 |
| Hit resolution | 4 indirect magic + push 1.2 out + 0.4 up to players inside r 12 with line of sight from the core |
| Projectile / AoE specs | instant radial, LOS-gated |
| Interruptibility | never |
| Counterplay | do not stand under it when the siren starts; use the 4 s to run to the first rebuilding pylon (it starts regenerating at tick 80) |
| Multiplayer notes | everyone in radius |
| Failure / edge cases | chassis missing at P4 entry: no pulse; the respawned chassis still gets 50% DR from the phase |

#### Attack: howitzer_lob

Identical to P3 `howitzer_lob`, with these overrides: weight 3, lead **50%**, gap after 30, fired from 16 – 20 blocks up (steeper arcs; rings and flight time unchanged).

#### Attack: missile_salvo

Identical to P1 `missile_salvo`, with these overrides: weight 2, 4 missiles, 4°/tick, gap after 30.

#### Attack: suppression_flare

Identical to P1 `suppression_flare`, with this override: gap after 30. In P4 painting mostly exists to pull missiles off whoever is denying a rebuild.

#### Attack: pylon_overcharge

Identical to P1 `pylon_overcharge`, with these overrides: weight 4; **REBUILDING pylons qualify** and the +20 repair counts as rebuild progress; gap after 30. This is the re-arm's defence against pylon denial.

#### Attack: desperation_carpet

Identical to P3 `desperation_carpet`, with these overrides: weight 3, always 2 lanes, no 200-tick delay, gap after 30.

#### Attack: flak_burst

Identical to P1 `flak_burst`, with this override: gap after 30.

---

### Phase transition matrix

| From | To | Trigger (predicate, ticks) | Timer and state rules |
|---|---|---|---|
| DORMANT | P0 | a non-spectator player inside `YardLayout.trigger` (checked every 10 ticks) or console use, and `START_CHECK` is not `interruptFalse` | phaseTicks = 0. Pylon max integrity fixed from participant count; all pylons REBUILDING at 0 with starts at +20/+50/+80/+110. Chassis spawned in the cradle with HP from participant count. Crates reprinted, gates shut, chunks forced. `ENCOUNTER_STARTED`, `BossEvents.STARTED`, advancement `enter_yard` |
| P0 | P1 | phaseTicks ≥ 160 | all pylons forced ONLINE at max |
| P1 | P2 | online < 4 | the breaking event set window = 200 |
| P2 | P2 | another break with online ≥ 1 | window reset to 200 (not added) |
| P1 or P2 | P3 | online = 0 | exposeTicks = 900, window = 0, chassis action cancelled, advancement `demolition_crew` if no re-arm has happened yet |
| P3 | P4 | exposeTicks reaches 0 | rearmCount + 1, K = min(4, 1 + rearmCount), rebuild schedule per §6, window = 0, action cancelled, shield pulse scripted, `REARM_STARTED` |
| P4 | P1 / P2 | online ≥ K | leftover REBUILDING → OFFLINE; P1 if K = 4 else P2; no window; action cancelled |
| P4 | P3 | phaseTicks ≥ 1200 and online < K | REBUILDING → OFFLINE at 0; exposeTicks = 594 |
| P1 – P4 | DEFEATED | chassis HP ≤ 0 | victory hooks (§11), bar at 0 "Ticket closed" |
| DEFEATED | CLEARED | 140 ticks | gates open, bars cleared, chunks released |
| CLEARED | DORMANT | `rematch = true` and 6000 ticks since the kill | full reset |
| P0 – P4 | DORMANT | no participants for 1200 consecutive ticks, or `/skyloreoverhead reset` | full reset: chassis removed, pylons restored at base integrity, crates reprinted, gates open, chunks released, `RESET` |
| any | any | `phaseTicks` | resets to 0 on every phase change. `window` only counts in P1/P2. `exposeTicks` only counts in P3. Every timer pauses while the yard has no participants |

---

## 10. Adds and environmental hazards

**No adds.** A shielded boss plus adds turns into an add-clearing fight, which buries the lesson. The pressure comes from the yard itself:

| Hazard | Cadence | Counterplay |
|---|---|---|
| Destructible cover | Overhead's blasts delete crates within 1.5 – 2.5 blocks; each missing crate block is reprinted 60 s later (**TUNE ME**) if nobody stands in it | rotate between crate clusters; do not camp one stack all fight |
| Pylon pads | standing on a pad invites `pylon_overcharge` | work from the pad edge or with a bow |
| Lanes | the painted lane lines are the carpet's grammar | learn them in P1, use them in P3 |
| Island edge | falling off returns you to the entry pad for 4 damage | none needed; it is a safety net |

---

## 11. Death, loot, trophy and pack events API

**Kill criteria.** The chassis's HP reaches 0 in any fighting phase, by any source (gate applied), or a gate-bypassing source (`/kill`, void). DEFEATED plays the 6 s death animation: rotors wind down, it spirals to the floor, explosions every 8 ticks, final blast at 120 ticks.

**Rewards on the kill**, per participant (anyone inside the yard volume at that moment):
- `skylore_bosses:targeting_core` (epic trophy item, stack 1).
- Advancements `skylore_bosses:overhead/prototype_down`, and the hidden `no_rearm` if no re-arm happened.
- `victoryFunction` from config, run as the player at permission 2.

**Loot table** `skylore_bosses:entities/overhead`: 2 – 4 iron blocks, 3 – 6 copper blocks, 1 – 3 redstone blocks, 4 – 9 gold ingots. Vanilla only so the mod never names a missing item. The pack overrides this file.

**Advancements** (`skylore_bosses:overhead/...`): `enter_yard` (fight start), `first_pylon` (first break of the fight, to all participants), `demolition_crew` (reach P3 before any re-arm), `prototype_down` (kill; also a `player_killed_entity` criterion), `no_rearm` (hidden; kill with zero re-arms).

**Events** (`net.teamaof.skylorebosses.bosses.overhead.api.OverheadEvents`, Architectury events, logical server):

| Event | Signature | Use |
|---|---|---|
| `START_CHECK` | `(level, origin, triggerPlayer) → EventResult` | refuse the start (`interruptFalse()`), e.g. Mek-steel gate |
| `ENCOUNTER_STARTED` | `(level, origin, players)` | chapter text, music |
| `PHASE_CHANGED` | `(level, origin, from, to)` | analytics, pack VO |
| `PYLON_BROKEN` | `(level, origin, index, breaker, contributors, remaining)` | co-op credit, quest sub-tasks |
| `REARM_STARTED` | `(level, origin, count, target)` | stall telemetry |
| `VICTORY` | `(level, origin, participants, stats{ticks, rearms, deaths, pylonsBroken, anyRearm})` | stage grant, quest completion |
| `RESET` | `(level, origin, reason)` | reason is `abandoned`, `command`, `rematch`, `created`, `rebuild`, `removed` |

Every kill also fires the boss-agnostic `BossEvents.DEFEATED(level, "overhead", origin, participants)`, and every start `BossEvents.STARTED`.

---

## 12. Quest, stage and chapter wiring

The mod exposes events, advancements and a config hook. The pack owns every id below. **The pack repository is not on this machine**, so none of the pack-side ids could be confirmed and no pack files were edited. The assumed ids are marked.

| Item | Owner | Value |
|---|---|---|
| Gate | pack | stage **`mek_steel` (UNCONFIRMED)**: the Mek steel band. Check the Progressive Stages config for the real id before wiring |
| Stage on kill | pack | `teknari_prototype_down` |
| Chapters | pack | `smoke_and_steel` (tile) or `novens_skyborn_engineering`. Suggest `smoke_and_steel`, right after the steel-tier quests |
| Quest retarget | pack | move `defeat_harbinger_optional` out of `sealed_beyond` into the Act II chapter. Task becomes advancement `skylore_bosses:overhead/prototype_down` (not a `cataclysm:the_harbinger` kill). Rename the title to "Decommission Overhead". Any `sealed_beyond` quest that depended on it (the Ignis path tiles) must drop that dependency and point at its previous prerequisite instead |

Suggested pack glue (KubeJS, server script; adapt stage calls to the pack's stage mod API):

```js
// kubejs/server_scripts/skylore/overhead.js
const OverheadEvents = Java.loadClass('net.teamaof.skylorebosses.bosses.overhead.api.OverheadEvents')
const BossEvents = Java.loadClass('net.teamaof.skylorebosses.core.api.BossEvents')
const EventResult = Java.loadClass('dev.architectury.event.EventResult')

OverheadEvents.START_CHECK.register((level, origin, player) => {
  if (!player.stages.has('mek_steel')) {        // UNCONFIRMED stage id
    player.tell('The console wants a steel-grade clearance badge.')
    return EventResult.interruptFalse()
  }
  return EventResult.pass()
})

BossEvents.DEFEATED.register((level, bossId, origin, players) => {
  if (bossId == 'overhead') players.forEach(p => p.stages.add('teknari_prototype_down'))
})
```

Alternative with no KubeJS: set `victoryFunction = "skylore:act2/prototype_down"` and grant the stage from that mcfunction; gate the start by having the Islands structure only place the console after the stage (weaker, since walking in also starts the fight).

QuestQueen: an advancement task on `skylore_bosses:overhead/prototype_down` completes the tile for every participant, because the mod awards it to all of them.

---

## 13. Java implementation outline

**Tick cadence.**

| What | Cadence |
|---|---|
| `OverheadYards.tickLevel` | every server level tick; skips yards whose origin chunk is unloaded unless mid-fight (then chunks are forced) |
| Yard: dormant trigger scan | every 10 ticks |
| Yard: pylon column repair (`ensurePylon`) | every 40 ticks |
| Yard: pylon mirroring + bars | every 5 ticks |
| Yard: bar membership, cover scan, chassis HP snapshot | every 20 ticks |
| Chassis: hover, facing, action slot, carpet impacts | every tick |
| Chassis: threat decay | every 20 ticks |
| Telegraph particles | every 2 – 6 ticks depending on the attack |

**Networking.** No custom payloads. Chassis `SynchedEntityData`: `ACTION`, `STAGE`, `BEAM_ON`, `BEAM` (Vector3f end point), `BROWNOUT`. Pylon block entity update packets carry `State`, `Fraction`, `Index` and are sent only when the state changes or the fraction moves by more than 2%. Telegraphs use `ServerLevel.sendParticles(player, type, longDistance = true, ...)` for players within 128 blocks.

**Save and reload of a fight.** `Yard.save()` writes phase, phaseTicks, fightTicks, window, expose, rearm count and target, break counter, pylon states (state, integrity, max, rebuildAt, brokenOrder, contributors), chassis UUID and HP snapshot, participants, forced-chunk flag. The chassis saves its yard origin. On load, the chassis resumes idle (whatever it was doing is dropped, 40-tick gap). If the chassis UUID is not found for 40 ticks it is respawned with the saved HP. Verified: leave and reopen mid-P2 kept phase, pylons, HP and re-arm count.

**Designer commands** (`/skylorebosses overhead ...` or `/skyloreoverhead ...`, op level 2):

| Command | Effect |
|---|---|
| `build [pos]` | place the test yard (default 40 above you) and register it |
| `register [pos]` | adopt an existing yard structure at pos without placing blocks |
| `unregister` | forget the nearest yard |
| `start` | start the nearest dormant yard (runs `START_CHECK` with you as trigger) |
| `reset` | reset to dormant |
| `skipphase` | P0 → P1; P1/P2 break the next pylon; P3 end the expose clock; P4 finish the rebuild; dormant/cleared start |
| `breakpylon <0..3>` | break one pylon (window, events, advancements) |
| `setpylons <0..4>` | force the live count without windows, re-evaluate the phase |
| `attack <id>` | force an attack now, telegraph included (works on bench-test chassis too) |
| `status` | phase, DR, window, expose, re-arms, pylon states, chassis action/stage/HP |
| `tp` | teleport to the entry pad |

**Bench-test mode.** A chassis from the spawn egg (no yard) hovers around where it spawned, uses the P2 table with no gate, and accepts `attack <id>`. This is the "void box" for AI work.

**Verification (done).** `tools/testing/t_overhead.py` drives a dev client through Marionette and asserts the whole fight. What was checked in game during development:

| Area | Result |
|---|---|
| Namespace sweep (`/test/sweep skylore_bosses`) | 17 blocks, 30 items, 47 models, 47 lang keys, 0 failures |
| P0 | pylons spin up 20/50/80/110, pulse at 100 with 30-tick telegraph, P1 at 160, gates shut, chassis immune |
| Gate | 2 / 19 / 3 / 20 damage from 20 in P1 / window / 3-pylon / P3; P4 0 during pulse then 9 – 10 |
| Pylons | charged sword 6 integrity, spam 2 – 4, arrows 6, casing hits forward to the core, overcharge repair observed |
| Re-arm | first-broken-first, 2 pylons, P2 at 80% after about 15 s |
| Persistence | save/reload mid-fight identical |
| Anti-soft-lock | chassis pushed out comes back; a removed pylon column is re-placed; void fall rescued |
| Attacks | all ten run telegraph → active → recovery; damage matches config (overcharge 10, howitzer 11.8 off-centre, laser 14.6 on the line, strafe 15) |
| Victory | DEFEATED → trophy, `prototype_down`, `no_rearm`, `demolition_crew` → CLEARED at 140, gates open |

---

## 14. Assets list

All assets are generated by `bosses/overhead/tools` and are placeholder-quality but shippable (procedural pixel textures, synthesized sounds).

**Models** (Blockbench sources in `bosses/overhead/models/<model>/`, Bedrock geometry read by GeckoLib):

| Model | Bones / locators | Animations |
|---|---|---|
| `overhead` (183 cubes) | body, lens_mount, mast, flare_rack, nacelle_0..3 + rotor_0..3, pod_l/r + door_l/r, turret → howitzer, gatling → gatling_barrels. Locators: core, lens, beacon, flare, thruster_0..3, bay_l, bay_r, muzzle, gatling | idle, brownout, laser_fire, strafe (loops); power_on, power_pulse, howitzer_load, howitzer_fire, missile_open (hold), missile_close, laser_charge, laser_vent, strafe_spinup, flare_fire, overcharge, carpet, shield_pulse, hurt, death (hold) |
| `generator_pylon` (block) | base, lamp_0..3, column, coil_0..5, cage, core. Locators: core, top, pad | online, rebuild, offline (loops); hurt, overcharge, break (hold) |
| `howitzer_shell` | shell, fins; trail | fly |
| `seeker_missile` | missile, fins; exhaust | fly |

**Textures.** Entity atlases + glowmasks (lens, beacon, thrusters, energy core, amber lamps glow) from the industrial palette in `tools/blockbench/texture.py` (painted steel, Noven teal, hazard stripes, copper, brass, rubber, grille, lens, amber, energy, concrete, warning red, glass). Blocks: `yard_plating`, `hazard_plating`, `yard_wall`, `yard_shutter`, `yard_crate`, `yard_console_front/side`, `pylon_casing`. Items: `targeting_core`, `generator_pylon`.

**Particles** (shared registry, usable by any boss): `smoke`, `spark`, `muzzle_flash`, `grid_arc`, `ember`, `shield`, `target_mark` (red ground telegraph), `rail_mark` (amber path telegraph), `red_beam`.

**Sounds** (36, `skylore_bosses:overhead.<id>`): rotor.loop, power.charge, power.pulse, howitzer.load, howitzer.fire, shell.impact, missile.open, missile.lock, missile.launch, missile.impact, laser.charge, laser.loop, laser.vent, strafe.spinup, strafe.fire, flare.launch, flare.burst, overcharge.charge, overcharge.discharge, carpet.klaxon, carpet.impact, shield.pulse, shield.deflect, rearm.siren, flak.fire, flak.burst, pylon.hum, pylon.hit, pylon.break, pylon.rebuild, pylon.online, lockdown.gate, brownout, hurt, death, victory.

**Lang keys** (`overhead.*`): bossbar (name, title, grid), status (p0, shielded, window, p3, p4, down), pylon names 0..3, titles (lockdown, exposed, rearm, rearm_aborted, victory + subs), log lines (p1, pylon_down, window_closed, pylon_online, rearm_done, rescue), gate feedback (absorbed, immune, pylon_spinning), warnings (missile_lock, laser, overcharge, carpet, painted), console (denied, readmit, cleared, unregistered), advancements × 5, subtitles for every sound, block/item/entity names.

**Placeholder-first path.** 1) procedural (this). 2) Art pass replaces the atlas PNGs and re-runs `export_to_mod.py`; bone names and locators are the contract with the Java code and must not change. 3) Sound pass drops real OGGs over `sounds/overhead/*.ogg` with the same names.

Rebuild: Blockbench open with the MCP plugin, then `python build_all.py`, `python export_to_mod.py`, `python gen_data.py` in `bosses/overhead/tools`.

---

## 15. Balance sheet

Every value below is **TUNE ME** and lives in `skylore_bosses-server.toml` under `[overhead.*]`.

| Key | Default | Meaning |
|---|---|---|
| `chassis.hp` | 600 | solo max HP |
| `chassis.mpHpPerPlayer` / `mpHpCap` | 0.5 / 2.5 | HP × (1 + 0.5 per extra), cap |
| `chassis.attackSpeed` | 1.0 | divides every gap and cooldown |
| `gate.drFourPylons` .. `drOnePylon` | 0.90 / 0.85 / 0.80 / 0.75 | DR by live count |
| `gate.windowDr` | 0.0 | DR during a window |
| `gate.rearmDr` | 0.5 | DR in P4 after the pulse |
| `gate.vulnerableMult` | 1.25 | vent bonus |
| `gate.windowTicks` | 200 | window after a break |
| `gate.staggerTicks` | 40 | brownout stagger |
| `gate.exposeTicks` | 900 | P3 clock |
| `pylons.integrity` | 150 | solo integrity |
| `pylons.mpIntegrityPerPlayer` / `mpIntegrityCap` | 0.35 / 2.0 | integrity scaling |
| `pylons.projectileDamage` | 6 | per player projectile hit |
| `pylons.explosionDamage` | 30 | player explosion at the core, falls off over 5 |
| `pylons.friendlyFire` | 0.5 | Overhead blast → pylon fraction |
| `pylons.overchargeRepair` | 20 | overcharge heal |
| `rearm.rebuildTicks` | 300 | 0 → full |
| `rearm.rebuildStaggerTicks` | 100 | between rebuild starts |
| `rearm.basePylons` | 2 | first re-arm target (+1 each later) |
| `rearm.abortTicks` | 1200 | re-arm give-up |
| `rearm.abortExposeFraction` | 0.66 | P3 clock after an abort |
| `attacks.howitzerDamage` | 14 | r 4 |
| `attacks.missileDamage` | 6 | r 2.2 |
| `attacks.laserDamage` | 4 | per 5-tick contact + 3 s fire |
| `attacks.boltDamage` | 3 | per strafe bolt |
| `attacks.overchargeDamage` | 10 | pad discharge |
| `attacks.carpetDamage` | 8 | r 2.5 per bomblet |
| `attacks.flakDamage` | 6 | r 3, × 2 vs vehicles |
| `attacks.shieldPulseDamage` | 4 | r 12 |
| `yard.dormantTimeoutTicks` | 1200 | abandon reset |
| `yard.coverRegenTicks` | 1200 | crate reprint |
| `yard.rematch` / `rematchDelayTicks` | true / 6000 | re-fight after a kill |
| `yard.victoryFunction` | "" | pack hook |

Values in code, not config (change in `Action.java` / `OverheadEntity.java` if playtests demand): attack ticks and weights (§9 tables), shell flight 40 ticks, missile speed 0.9 and boost 10, laser line 36 blocks, strafe rail 48 blocks and bolt speed 2.2, carpet 12 impacts per lane, hover bands and speeds, global gaps.

### Playtest watchlist

- **Solo DPS vs window length.** Log time-to-kill and window utilisation. If solo players with Mek-tier gear kill inside two windows, raise `hp` or drop `windowTicks` to 160. If most solo runs need two re-arms, raise `windowTicks`.
- **Co-op break speed.** Four players with explosives can drop a pylon in seconds. Watch `explosionDamage` and `mpIntegrityPerPlayer` first; do not touch solo numbers for this.
- **Stall vs re-arm.** If teams farm P4 aborts (deny rebuild, get a fresh P3), lower `abortExposeFraction` or raise `overchargeRepair`.
- **Melee viability.** Brownout band 2.5 and P3 band 4 – 8 exist so melee builds are not forced to bows. If melee players never land hits in P3, lower the P3 band.
- **Overcharge frustration.** If players get zapped without seeing the arc, lengthen the telegraph to 50 before changing damage.
- **Missile lethality from low altitude (P3).** Eight missiles at 6°/tick from 4 – 8 blocks up may be the deadliest thing in the fight. First knob: P3 count 8 → 6.
- **Aircraft cheese.** Watch whether flak alone makes flying unreasonable, or whether players circle above the ceiling. Flak ignores the ceiling on purpose.

---

## 16. Canon and text rules checklist

- [x] Player-facing names are "Overhead" and "Overhead, Noven's Decommissioned Prototype". "Harbinger" appears nowhere in lang.
- [x] No Calyx, infection, parasite, spore or "the path" language in any Overhead string or event.
- [x] Voice: logs, tickets, incident reports, budget codes, warranty. The sass lands on Noven and Teknari process ("Budget code: none", "Warranty status: void", "Noven has not been notified", "Incident filed under 'weather'").
- [x] The machine never mocks the player. Warnings are instructions ("Get off the pad!", "Get into an unmarked lane.").
- [x] Academy dryness allowed; meme voice not ("under protest" is as far as it goes).
- [x] Titles fit the screen: the lockdown and victory subtitles were shortened after in-game screenshots showed overflow.
- Writers adding lines: keep the `[OVERHEAD]` prefix for chassis log lines; no exclamation marks from the machine; the player is never "intruder", always a ticket or "personnel".

---

## 17. Open risks

| Risk | Status / mitigation |
|---|---|
| Artillery spam performance | Worst case is a P3 carpet (36 scheduled impacts, no entities) plus an 8-missile salvo. Ordnance ticks are O(1) with one hit scan each. Telegraph particle packets peak around 60 per tick per player during a carpet telegraph. Measure on a server with 4 players; if needed, thin the carpet lines to every 6 blocks |
| Chunk unload of pylons | chunks within ±40 blocks are force-loaded for the whole fight; pylon state lives in SavedData, not in the block entity; missing columns are re-placed every 40 ticks |
| Chassis loss | missing for 40 ticks → respawned with saved HP; leashed and snapped back if moved away; `/kill` counts as a kill by design |
| Flight and aircraft in the yard | flak targets aircraft and anyone above the ceiling; vehicles in the tag take double blast damage. Immersive Aircraft ids are in the tag as optional entries; **Create Aeronautics** contraption entity ids are unknown and not tagged yet |
| Multiplayer desync | all state server-side; the client only renders synced flags. Laser end point is synced every tick while firing. Pylon BE updates are throttled to real changes |
| Structure ownership with Skylore Islands | not solved in code: the real yard must be adopted with `register` or a pack script. Needed from the Islands agent: the structure id and whether it can fire a placement hook the mod can listen to |
| Pre-existing particle bug (fixed) | all `skylore_bosses` particles, Matris's included, were invisible because Architectury's deferred particle provider registration never delivered. `SBClient` now registers providers on NeoForge's `RegisterParticleProvidersEvent` directly. Matris visuals change as a side effect (they now show) |
| Stage and quest ids | `mek_steel` and the quest dependency graph are unconfirmed (§12) |
| Resistance-stacking players | overcharge and laser use indirect magic (armour-ignoring, not resistance-ignoring). A max-Resistance player ignores the fight; that is the pack's balance call |
| Several yards in one dimension | supported (map keyed by origin), but two yards within 64 blocks would confuse the console and explosion lookups. Keep them apart |

---

## Implementation order

| # | Step | Status |
|---|---|---|
| 1 | Void-box controller + one pylon BE + DR gate + bossbar | done: `Yard`, `GeneratorPylonBlockEntity`, `damageReduction`, `OverheadBossBar` |
| 2 | Overhead entity idle + `howitzer_lob` with full telegraph | done |
| 3 | All four pylons + break windows + P0 – P3 transitions | done, verified |
| 4 | Full attack roster per phase | done: 10 actions, all forced and observed in game |
| 5 | P4 re-arm loop | done, including abort |
| 6 | Arena template / structure hook + lockdown | test yard, gates, console, rescue done; worldgen hook waits on Skylore Islands (§3, §17) |
| 7 | Pack API, advancements, quest/stage wiring | mod side done (§11); pack side documented, not applied (pack repo not available) |
| 8 | Art / sound pass | placeholder procedural art and synthesized sounds shipped; final art pending |
