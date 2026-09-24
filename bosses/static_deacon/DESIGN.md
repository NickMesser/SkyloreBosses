# The Static Deacon: design package

Skylore Act III boss (Church of Ender). Ships as the `static_deacon` module of the `skylore_bosses` mod, custom Java, NeoForge 21.1.x on Minecraft 1.21.1, Java 21. Cataclysm's Ender Guardian was tonal reference only. Nothing here summons, subclasses, patches or needs Cataclysm.

Every number marked **TUNE ME** is a first-pass value. All of them live in the `[static_deacon]` section of `skylore_bosses-server.toml` (§15 lists each key), so playtest tuning needs no rebuild.

Status: the module described here is implemented in `src/main/java/net/teamaof/skylorebosses/bosses/staticdeacon/` and was exercised in a dev client through Marionette (`tools/testing/t_static_deacon.py`, §13 "Verification"). Where the code and this document disagree, the code is the bug.

> *"Communion quorum: 48 of 48 flagstones. The floor is in good standing."*

---

## 1. One-page fight bible

**Fantasy.** Under every Church of Ender altar there is an undercroft, and in the undercroft there is a caretaker. The congregation calls it a caretaker. Academy files call it The Static Deacon: a Beryl, crystal grown out of static dust, never ordained, never asked to leave. It keeps the floor holy. The floor is consecrated endstone, and the Beryl's affinity for endstone is doctrine: while the Deacon stands on a consecrated flagstone it is in communion with the whole nave, and the nave heals it.

**Player fantasy.** Demolition of communion. You do not out-damage a thing that the building itself is healing. You take the building apart: break a flagstone and it dies, lay a block on it and it dies, blow it up and it dies. Every flagstone you kill weakens the heal and hardening for all of them, and the Deacon has to walk to what is left. When there is nothing left but the altar, it kneels there and keeps vigil, and that is when you kill it.

**Win condition.** The Deacon's HP reaches 0. In practice: strip the nave below the heal curve, strand it, spend the plinth window, and finish it before the reseed rite puts the floor back.

**Fail states.**
- Player death. Normal respawn. The crypt stays sealed; ringing the sacristy bell outside the door re-admits a participant to the entry pad. The fight continues for everyone else.
- Everyone leaves or dies and nobody is in the crypt for 60 s (**TUNE ME**): the crypt resets to dormant. Floor re-laid, pews replaced, Deacon gone, door open. No partial progress carries over.
- Stalling. Not a fail state, a punishment. Sitting in P3 without finishing the kill starts the reseed rite, and every later rite restores more of the floor.

**Tone.** Quiet church violence with bureaucratic liturgy. The Deacon never raises its voice and never mocks you. Its lines are register entries, quorum counts, standing orders and requisitions ("By standing order. No quorum required."). The dryness lands on the Church's paperwork and hollow rite, never on the player. No Calyx, infection, parasite or "the path" language anywhere in this fight.

**Pedagogy.** "Kill the floor, then the Deacon." The first hit a player lands while it communes tells them: *"Communion absorbed 50% and the floor is healing it. Break the flagstones."* The bossbar's second bar is the floor itself, notched. The P0 vesting chime draws a light outline around the flagstone under each player and says *"You are standing on communion. The floor heals the Deacon."* Overhead taught arena weak points (pylons); this fight teaches ground control. Null Router and Matris Calyx reuse both lessons later.

---

## 2. Mod architecture

The Deacon is a `BossModule` inside the existing mod, the same way Overhead is. A sibling jar was considered and rejected: the shared core (particles, sounds, `AnimFx`, `Telegraph`, screen shake, `BossEvents`, config sections, `/skylorebosses`) already exists and one jar is simpler for the pack to list.

| Item | Value |
|---|---|
| Mod id | `skylore_bosses` (module id `static_deacon`) |
| Package | `net.teamaof.skylorebosses.bosses.staticdeacon` |
| Hard deps | NeoForge, Architectury API 13, GeckoLib 4.7 (already required by the mod) |
| Soft deps | none. Iron's Spells, Ars Nouveau, Neo Vitae, Progressive Stages, QuestQueen and Cataclysm are touched only by the pack, through events and datapacks. Spell damage and spell explosions work because the fight reads vanilla `DamageSource`s and `ExplosionEvent`s. |
| License | MIT, same as the mod |

```
bosses/staticdeacon/
├─ StaticDeaconBoss.java       module entry: registries, config, events, client, commands (+ /skyloredeacon alias)
├─ DeaconConfig.java           every TUNE ME value (server config)
├─ DeaconLocators.java         GENERATED: model locator table (core, head, halo, hand_l, hand_r, censer, feet ...)
├─ DeaconSoundIds.java         GENERATED: 36 sound ids
├─ api/DeaconEvents.java       stable pack hooks (§11)
├─ registry/                   DeaconBlocks, DeaconEntities, DeaconItems
├─ block/                      ConsecratedEndstoneBlock (LIT), CryptPewBlock (stall shape), SacristyBellBlock
├─ encounter/
│  ├─ Crypts.java              SavedData: every undercroft in a level, keyed by origin; ticks them
│  ├─ Crypt.java               one crypt + its fight: phase machine, floor, heal/damage gate, reseed schedule, bar, saves
│  ├─ Phase, Flagstone         state types
│  ├─ CryptLayout.java         geometry (slot grid, plinth, pillars, pews, door, volumes, leash, fall zone)
│  ├─ CryptBuilder.java        the mod-placed test undercroft, floor re-lay, pew replacement, door grate
│  ├─ DeaconBossBar.java       Deacon bar + Communion bar
│  └─ DeaconCommonEvents.java  level tick, deaths, block break / place / explosion hooks
├─ entity/
│  ├─ StaticDeaconEntity.java  the Deacon: stance AI, static step, action slot, every attack, vigil break, damage hook
│  ├─ DeaconAction.java        attack roster data (ticks, cooldowns, weights, interrupt rules)
│  └─ DeaconProjectile.java    static bolts and homing shards (one class, synced kind)
├─ client/                     DeaconClient, DeaconRenderer, DeaconProjectileRenderer
└─ command/DeaconCommands.java
```

Shared-core changes made for this boss: `Telegraph` moved from Overhead into `core/fx` (both bosses use it); four particles added to the shared library (`static_dust`, `communion_mote`, `beryl_glint`, `litany_light`); the Blockbench texture pipeline gained a `liturgical` palette and per-tile override swatches; the sound synthesizer gained `bell`, `crystal`, `crackle` and `chant` voices.

**Server vs client.** All logic is server-side. The client gets synced entity data on the Deacon (`ACTION`, `STAGE`, `COMMUNE`, `STAGGERED`, `STEPPING`, `TETHER`), the floor's own block states (`consecrated_endstone[lit]` and `desecrated_endstone` are the visible truth, so even LOD renderers show the floor state), two vanilla boss bars, titles and action-bar lines, server-sent telegraph particles and client animation keyframes, and the shared `SBNetwork.ScreenFx` shake. No Deacon-specific custom packets.

**What the pack still owns** (datapack or KubeJS, never Java):
- The `church_of_ender` start gate and the `static_deacon_cleared` stage grant (§12).
- The `forbidden_knowledge` quest tile (§12).
- Loot override: `data/skylore_bosses/loot_table/entities/static_deacon.json` ships vanilla End materials; the pack replaces it with Iron's / Ars / Vitae reagents.
- Tags: `#skylore_bosses:static_deacon/communion_substrate` (what counts as an intact heal block) and `#skylore_bosses:static_deacon/breakable_cover` (what nave_shatter may break).
- `victoryFunction` in config: an mcfunction run as each participant on the kill.
- Structure placement of the real undercroft (§3).

---

## 3. Arena: the undercroft

### Footprint

Offsets are from the crypt **origin**: the centre block of the floor layer, directly under the altar plinth. Players stand at `origin.y + 1`. North is −Z. All numbers are in `CryptLayout.java`.

The floor is a grid of 3×3 **flagstone slots**: columns `cx = −3..3` (x = 3cx−1 .. 3cx+1) and rows `cz = −5..5`. That makes a 21 × 33 floor inside a 23 × 35 wall ring.

```
          x: -10 ................ 0 ................ +10
 z=-17  ██████████████████████ wall ██████████████████████
 cz=-5   [st] [N ] [N ] [N ] [N ] [N ] [st]      st = aisle slot with a choir stall (pew)
 cz=-4   [st] [N ] (P) [N ] (P) [N ] [st]        N  = nave flagstone (heal substrate)
 cz=-3   [st] [N ] [N ] [N ] [N ] [N ] [st]      (P)= pillar slot: floor-to-ceiling column, tiled
 cz=-2   [st] [N ] (P) [N ] (P) [N ] [st]        ▣  = altar plinth: 3x3, two blocks high, unbreakable
 cz=-1   [st] [N ] [N ] [N ] [N ] [N ] [st]
 cz= 0   [N ] [N ] [N ]  ▣  [N ] [N ] [N ]       transept row: the aisles are nave here
 cz= 1   [st] [N ] [N ] [N ] [N ] [N ] [st]
 cz= 2   [st] [N ] (P) [N ] (P) [N ] [st]
 cz= 3   [st] [N ] [N ] [N ] [N ] [N ] [st]
 cz= 4   [st] [N ] (P) [N ] (P) [N ] [st]
 cz= 5   [st] [N ] [N ] [e ] [N ] [N ] [st]      e = entry pad (0, +1, 14), on a nave flagstone
 z=+17  ███████████████████ ▒door▒ ████████████████████  3 wide, 3 high; grate in lockdown
         landing z 18..23, sacristy bell at (0, +1, 21)
```

| Element | Spec |
|---|---|
| Nave flagstones | **48** 3×3 slots of `consecrated_endstone` (432 blocks). Kinds by slot: plinth (0,0); pillars (±2, ±2) and (±2, ±4); aisles cx = ±3 except the transept row cz = 0; everything else is nave |
| Subfloor | `crypt_subfloor` under the whole crypt (y −1), unbreakable. A mined flagstone block leaves a 1-deep hole, never a shaft |
| Plinth | `altar_plinth` 3×3×2 at the origin (top surface y +2), unbreakable. Nothing may be placed in the 3×3×4 column above it during a fight (placement cancelled; anything else cleared every 10 ticks) |
| Pillars | 8 `crypt_pillar` columns, floor to ceiling, at the pillar slot centres. Hard line-of-sight cover (breaks the plinth_pull chain, stops bolts and shards) |
| Aisles | `crypt_tile` floor, a 3-block choir stall (`crypt_pew`) in the middle row of each aisle slot, facing the nave. 60 stall blocks. Soft cover: blocks shards and bolts at seat height, broken by nave_shatter, replaced 60 s later |
| Walls, ceiling | `crypt_wall` ring and ceiling at y +9 (air +1 .. +8), unbreakable. `beryl_lamp` light sources set into the ceiling |
| Door | south wall, `|x| ≤ 1`, y +1 .. +3; filled with `crypt_grate` during a fight |
| Entry pad | (0, +1, 14): where the bell and the rescue put players |
| Bell | `sacristy_bell` (0, +1, 21) on the landing outside the door |

### Height bands

| Band | y above floor surface | Used by |
|---|---|---|
| Floor | 0 | nave flagstones; communion requires the Deacon's feet on a live one (within 0.6 of the surface) |
| Plinth top | +1 | vigil (P3), the reseed rite (P4) |
| Stall seat / back | +0.5 / +1 | soft cover height |
| Deacon | 0 .. +3.4 | hitbox 1.2 × 3.4; the halo reads up to about +3.8 |
| Ceiling | +8 | no flight above the fight inside the crypt |

### Entry and lockdown

Walking into the nave's interior box (the trigger excludes the last row by the door) starts the fight, as does ringing the bell. Both go through `DeaconEvents.START_CHECK` first, so the pack can refuse (`church_of_ender` gate). On start the floor is re-laid, the door fills with `crypt_grate`, the Deacon vests up out of the plinth, and chunks covering the crypt plus 8 blocks are force-loaded until the fight ends. During a fight the bell re-admits a player to the entry pad. The door opens on reset and on victory.

### Void fall

Anyone in the **fall zone** (under the crypt footprint plus 6 blocks, from 3 below the floor down 128 blocks) during a fight is put back on the entry pad with 4 damage, Slow Falling 3 s and *"Returned to the nave. Your absence has been noted in the register."* The Deacon cannot be taken out of the nave: outside the leash box (the interior) or 2 blocks under the floor it is put back on the plinth that tick.

### Distant Horizons silhouette notes

- The floor state is block state, not an entity or BE, so LODs show live flagstones (lit, pale with violet glyphs) against dead ones (grey rubble). A stalled team sees the floor come back even from far away.
- The Deacon is a GeckoLib entity and is invisible in LODs. The Church above should carry a vanilla-block landmark (tall end-rod or amethyst spire over the altar) so the crypt entrance reads from a distance.
- The undercroft is underground (under the Church); only the stair entrance needs a silhouette.

### Ownership handoff (Skylore Islands)

| Piece | Owner |
|---|---|
| Worldgen placement of the Church island, the church above, the stair down, loot around it | **Skylore Islands** agent (structure NBT + placement) |
| Encounter blocks, entity, the fight, the crypt registry | this mod |
| Test undercroft for AI bring-up | this mod: `/skyloredeacon build [pos]` |

The contract: the structure places the same blocks at the same offsets as `CryptBuilder` (at minimum the 48 flagstones as `consecrated_endstone`, the 3×3×2 `altar_plinth`, the pillar columns, the unbreakable shell and a `sacristy_bell` near the door; stalls, lamps and wall styling may change as long as stalls stay in `#…/breakable_cover`). The mod adopts it with `/skyloredeacon register <origin>` (no blocks placed). An automatic hook on structure placement is open (§17): the Islands agent has to confirm the structure id.

The fallback test box is the mod-placed crypt (about 14k blocks, one tick).

---

## 4. Phase flowchart

```
DORMANT ──(player in the nave trigger, or bell; START_CHECK allows)──► P0 VESTING
P0 ──(phaseTicks ≥ 200)──► P1 COMMUNION (C ≥ 0.70)  or P2 if already stripped below
P1 ──(C < 0.70)──► P2 PATCHWORK
P2 ──(C ≥ 0.70; only via reseed or setfloor)──► P1
P1/P2 ──(live nave = 0)──► P3 VIGIL (exposeTicks = 900)
P3 ──(exposeTicks reaches 0)──► P4 RESEED (rite k, target K)
P4 ──(live nave ≥ K)──► P2 (P1 if C ≥ 0.70)
P4 ──(schedule exhausted, nothing growing, or phaseTicks ≥ 1200) and live ≥ 1──► P2 / P1
P4 ──(same, live = 0: every reseed denied)──► P3 (exposeTicks = 600)
P3 ──(live nave > 0: a late reseed matured)──► P2
any fighting phase ──(Deacon HP ≤ 0)──► DEFEATED ──(160 ticks)──► CLEARED ──(rematch, 6000 ticks)──► DORMANT
any fighting phase ──(no participants for 1200 ticks, or /reset)──► DORMANT (full reset)
```

**Coverage.** `C = live nave flagstones / 48`. A flagstone is **live** while its slot state is LIVE; the slot goes DESECRATED the moment any of its nine blocks stops being intact. **Intact** = the block is in `#skylore_bosses:static_deacon/communion_substrate` (default: `consecrated_endstone` only) **and** the block above has an empty collision shape. That is checked by event (mine, place, explosion) and by a full scan of every live or growing slot every 10 ticks (catches pistons, drills, fluids turning solid, `/setblock`). K (rite target) = min(36, 12 + 6 × (rite − 1)): 12, 18, 24, 30, 36 (**TUNE ME**).

| Phase | Floor | DR while communing | Regen while communing | Deacon intent |
|---|---|---|---|---|
| P0 | intact, coming online ring by ring | 100% (immune) | none | vests up out of the altar; chime marks the players standing on communion |
| P1 | C ≥ 0.70 (34–48 live) | 50% × C | 4% max HP/s × C | stands on the live flagstone nearest its target; small attack set |
| P2 | 1–33 live | 50% × C | 4% × C | walks to remaining patches; denser attacks; litany and shatter |
| P3 | plinth only | plinth: 25%; off: 0% | plinth: 0.6%/s; off: 0 | vigil on the altar; pulls players in; knocked off by burst |
| P4 | regrowing | rite channel 60%, else as P3 | plinth 0.6%/s | channels the reseed rite from the altar; fights between channels |

Timers are in ticks and count only while at least one participant is in the crypt.

---

## 5. Entity and component design

| Component | Kind | Why |
|---|---|---|
| `Crypt` in `Crypts` | SavedData (per level, map of crypts) | source of truth for phase, all 77 slot states, timers, rite schedule, participants, Deacon UUID and HP. Survives relog, restart, chunk unload |
| `StaticDeaconEntity` | `Monster` + `GeoEntity`, `skylore_bosses:static_deacon`, 1.2 × 3.4, step height 1.1 | the Deacon. Walks with vanilla ground navigation (no custom pathfinder): through the 2-wide gaps, up the 1-block dais. One action slot. Persistent |
| `DeaconProjectile` | `Projectile` + `GeoEntity`, `skylore_bosses:deacon_projectile` | static bolts and homing shards, one class, synced `Kind`. Shards are pickable (any player hit breaks one) |
| Flagstones | plain blocks, no BE | 432 blocks; a BE each would cost far more than the 10-tick scan. `ConsecratedEndstoneBlock` only carries `lit` |
| `altar_plinth` | plain unbreakable block | the one heal tile that always remains. Not in the substrate tag: the plinth is its own communion state (vigil) |
| `CryptPewBlock` | directional block with a stall-shaped collision | soft cover that nave_shatter breaks and the crypt replaces |
| `SacristyBellBlock` | directional block | start / re-admit / cleared message |
| Boss bars | two `ServerBossEvent`s owned by the crypt | Deacon HP and floor integrity |
| Telegraphs | server → per-player long-range particles (`core.fx.Telegraph`) | outlines, rings, lanes still render across the nave |
| VFX on the model | GeckoLib particle and sound keyframes | dust, motes, glints, litany light per animation |

Sub-parts (lattice arm, censer on its chain, halo, cowl, vestments) are **bones of one model**, not separate entities or hitboxes. Nothing in the fight needs them hit separately, and multi-part entities would multiply sync and hitbox cost. The vestments do not change the hitbox.

The Deacon is a view like the floor is: missing for 40 ticks (discarded, unloaded) means it is respawned on the plinth with its saved HP. `/kill` counts as a kill.

---

## 6. Heal-floor and plinth system card

Registry ids: `skylore_bosses:consecrated_endstone` (heal substrate, `lit` true/false), `skylore_bosses:desecrated_endstone` (dead rubble), `skylore_bosses:altar_plinth`.

### What kills a flagstone (any one block of its nine)

| Verb | Detection | Attribution | Measured |
|---|---|---|---|
| Mine it (any pickaxe; hardness 3, same as end stone) | `BlockEvent.BreakEvent` | the miner | survival diamond pickaxe on one block: whole slot turns to rubble, floor 48 → 47 |
| Cover it: place any block with a collision shape on top (cobblestone, a slab, a carpet) | `BlockEvent.EntityPlaceEvent` | the placer | cobblestone on one block: 47 → 46 |
| Blow it up (TNT, creepers, spell explosions, Create cannons) | `ExplosionEvent.Detonate` affected blocks | the explosion's indirect source if a player | one TNT: at least one slot |
| Anything else (pistons, machines, fluids, commands) | 10-tick scan | none | `/setblock … air`: caught within 10 ticks |

When a slot dies, its remaining endstone becomes `desecrated_endstone` (grey, cracked, breakable, drops nothing) so the dead ground reads at a glance. Consecrated endstone drops nothing either: communion cannot be carried out in a pocket.

Rule of thumb for players: **one block per flagstone.** Mine the edge block of a live flagstone from a dead one next to it.

### New endstone laid mid-fight

Player-placed endstone (vanilla or picked consecrated blocks) never makes a flagstone live. It is not in the substrate tag, and even if the pack adds it, a DESECRATED slot only comes back through the reseed rite. The first time a participant lays a block into a dead flagstone they read *"Endstone laid without rite is only stone."* The rite re-lays the whole slot as fresh consecrated endstone and **sweeps the block layer above it** (anything placed on top is broken).

### Plinth rules

- Unbreakable (hardness −1, blast resistance 3.6M), re-placed if something removes it, nothing may be placed in the 3×3×4 column above it during a fight (the placement is cancelled with *"The altar takes no offerings during a rite."*).
- Always communion ground for the Deacon: **vigil** = 25% DR and 0.6% max HP/s regen, in every fighting phase.
- The Deacon can be knocked off it (§7 vigil break).

### Deacon pathing to the floor

- **P1/P2 stance.** Every 40 ticks, or immediately when its stance flagstone dies, it picks the live flagstone minimising `dist(flagstone, target) + 0.5 × dist(flagstone, self)` and walks there. It stands still once its feet are on that flagstone. With no live flagstone it heads for the plinth.
- **Stranded priority.** While off communion and on its way back (P1/P2 with live flagstones left, or P3/P4 not on the plinth and not locked out of it), its only legal attack is `lattice_lash` at whoever is in reach. Walking back comes first. (This was a bug found in testing: before this rule it only moved in the gaps between attacks and never got back.)
- **Final approach.** Within 3 blocks it steers directly with its move control, because vanilla paths finish within a block of the goal and left it on the dead flagstone next door.
- **Stuck.** No 0.6-block progress for 80 ticks with the goal over 2 blocks away: **static step** (20-tick dissolve with dust at both ends, then teleport).
- **P4.** Not on the plinth 60 ticks after wanting it: static step to the plinth.
- **Stumble.** When the flagstone it stands on dies, it drops any telegraphing attack that is interruptible, staggers 20 ticks (**TUNE ME**) and re-picks a stance. *Mine under it* is real counterplay.

### Multiplayer ownership

Every desecration names its player in `DeaconEvents.FLAGSTONE_DESECRATED` (slot, player, cause, remaining), and adds 30 threat (**TUNE ME**) to that player, so strippers draw bolts and shards. Co-op play is one player stripping while a partner out-threats them with damage. No per-player floor ownership beyond that: the floor is shared.

---

## 7. Heal and damage math

```
C       = liveNave / 48
commune = PLINTH  if the Deacon stands on the plinth top
          NAVE    if its feet are on a LIVE flagstone (within 0.6 of the floor surface)
          NONE    otherwise

DR:   P0, DORMANT, CLEARED                  1.00
      channelling the reseed rite           RITE_DR   = 0.60
      NAVE                                  NAVE_DR   × C = 0.50 × C
      PLINTH                                PLINTH_DR = 0.25
      NONE, DEFEATED                        0

regen (fraction of max HP per second, applied every 10 ticks as half of it; not in P0):
      NAVE    NAVE_REGEN × C   = 0.04 × C
      PLINTH  PLINTH_REGEN     = 0.006
      NONE    0

applied = incoming × (1 − DR) × (VULNERABLE_MULT = 1.25 if desecrated (vigil broken) or in a vented recovery)
          then vanilla armour (Deacon armour 6)
```

Code: `Crypt.damageReduction`, `Crypt.regenPerSecond`, `StaticDeaconEntity.hurt`. `BYPASSES_INVULNERABILITY` sources (`/kill`, void) skip the gate.

**Break-even DPS** (incoming damage per second needed just to stop it healing, solo, HP 800):

| Floor | DR | Regen | Break-even raw DPS |
|---|---|---|---|
| 48/48 | 50% | 32.0 HP/s | 64 |
| 34/48 (P1 floor) | 35% | 22.7 | 35 |
| 24/48 | 25% | 16.0 | 21 |
| 12/48 | 12.5% | 8.0 | 9.1 |
| 8/48 | 8% | 5.3 | 5.8 |
| plinth (vigil) | 25% | 4.8 | 6.4 |
| stranded | 0 | 0 | 0 |

A well-geared Act III melee or Iron's caster sustains roughly 12–25 DPS, so the full nave is not viable, half a nave is a stalemate, and a stripped nave is a kill.

**Vigil break (plinth hold DPS check).** While the Deacon is on the plinth, damage *after DR* inside a 60-tick window (**TUNE ME**) reaching 8% of max HP (64 solo) knocks it off: pushed 1.1 blocks/tick away from the breaker, 60-tick stagger, **desecrated** (×1.25 damage) for 100 ticks, locked out of the plinth for 100 ticks (**TUNE ME** all). It holds ground about 4 blocks off the dais and fights until the lock ends, then walks back. While channelling the rite the threshold is 10% (80 solo) and it also cannot recast for 200 ticks.

**Burst stagger.** 10% of max HP inside 40 ticks during an interruptible telegraph cancels it, 30-tick stagger, then 200 ticks before that can happen again.

**Measured in game** (20 raw damage per hit, armour 6, normal difficulty):

| State | Expected | Measured |
|---|---|---|
| P0 | 0 | 0 |
| communing, 48/48 | 20 × 0.5 → 9.5 after armour | 10.0 |
| regen at 48/48 | 16 HP per 10-tick pulse | 64–80 HP over 40 ticks |
| 8/48 | DR 8%, 5.3 HP/s | DR 8%, 5.33/s |
| stranded | 19 | 19 |
| vigil | 15 → 14.3 after armour, minus a regen pulse | 12–14 |
| desecrated after a vigil break | 25 → 23.8 | 24 |
| rite channel | DR 60% | 60% |

**Throughput sketch (solo, TUNE ME watch).** HP 800. P1/P2: every time you kill the flagstone it stands on it takes 2–4 s to reach the next live one, so each strand is about 40 free damage at 15 DPS, and the regen you face keeps shrinking. P3 45 s: the vigil cycle is roughly 5 s of burst to break it (64 after DR) then 5 s of ×1.25 damage while it is locked out, about 160 damage per 11 s, or about 650 over the P3 clock. A competent solo player kills in the first P3 or after one rite. A player who ignores the floor out-damages nothing.

---

## 8. Bossbar and scoring

Two bars, shown to every participant, refreshed every 5 ticks, membership synced every second.

| Bar | Progress | Colour | Title |
|---|---|---|---|
| Deacon | HP / max | white P0; purple P1 communing; pink P2 communing; red plinth or stranded; yellow reseed | `The Static Deacon  \|  <status>` |
| Communion | live nave / 48, `NOTCHED_12` (4 flagstones a notch) | white | `Communion: N of 48 flagstones consecrated` |

Status strings carry the live numbers: *"In communion. Absorbing 35%, restoring 22.7 HP/s"*, *"Stranded off consecrated ground. Full damage"*, *"Vigil on the altar. Reseed rite in 31s"*, *"Reseed rite: 7 of 12 flagstones"*, *"Post vacant"*. Titles only change when the text changes. The Deacon bar darkens the screen (crypt mood); boss music on.

Co-op players see the same bars. The Communion bar is how the player drawing fire knows the stripper is making progress, and the colour change to red is the call to burst. HP scales × (1 + 0.5 per extra participant), cap × 2.5 (**TUNE ME**), fixed at spawn. Regen and the vigil threshold are fractions of max HP, so they scale with it.

---

## 9. Per-phase action catalogs

### How the AI picks

One action slot: IDLE → TELEGRAPH → ACTIVE → RECOVERY → IDLE. Nothing overlaps; the Deacon stands still for the whole action. After RECOVERY it waits a **global gap**: P1 30, P2 20, P3 15, P4 25 ticks ÷ `attackSpeed` (**TUNE ME**). Then it builds a weighted bag from every attack whose phase weight > 0, whose own cooldown has expired and whose condition holds, and draws one. An empty bag retries in 10 ticks. **Stranded rule** (§6): while walking back to communion, only `lattice_lash` is legal. **P4 rule**: whenever the rite has work left and is off its recast cooldown, `reseed_rite` is forced before the bag.

| Attack | P0 | P1 | P2 | P3 | P4 | Own cooldown | Condition |
|---|---|---|---|---|---|---|---|
| `vesting_chime` | script | | | | | none | P0 tick 120 |
| `communion_pulse` | | 3 | 4 | | 3 | 200 | a live nave flagstone exists and the Deacon is communing |
| `lattice_lash` | | 6 | 5 | 5 | 3 | 40 | nearest target within 4.5 horizontal, 3 vertical; weight × 2 |
| `static_bolt` | | 5 | 4 | 3 | 3 | 50 | a target |
| `nave_shatter` | | | 3 | 4 | 2 | 160 | nearest target within 11 |
| `homing_shard` | | | 3 | 3 | 3 | 180 | a target |
| `plinth_pull` | | | | 6 | 3 | 140 | on the plinth, a target 3.5–16 away with line of sight from the censer; × 2 if nobody is in reach |
| `litany_beam` | | | 3 | 3 | | 220 | a target on the nave floor |
| `reseed_rite` | | | | | script | recast 200 after a break | P4 with schedule left |

**Targets** are participants who are alive, not spectators, not creative. **Threat**: +1 per point of damage dealt (after DR), +30 per flagstone desecrated; decays 2% per second.

**Idle and reposition, per phase.**
- P0: stands on the plinth (vesting animation), no movement.
- P1/P2: stance on live flagstones (§6), walking speed modifier 1.0 / 1.1; faces its target at 12°/tick when not walking.
- P3: walks to the plinth (1.2) and kneels (vigil). Knocked off: holds a point 4 blocks off the dais toward the breaker until the lock ends.
- P4: to the plinth, static step after 60 ticks; channels; between channels it fights from the plinth.
- All phases: leash to the nave interior (outside it, or 2 blocks under the floor: back to the plinth at once); stuck 80 ticks: static step. It never leaves the nave and cannot reach the void.

**Interrupt rules.** Each attack is NEVER, TELEGRAPH (cancelled only before it fires) or ANY. Interrupt sources: the burst stagger (telegraph only), the flagstone under it dying (stumble; telegraph-interruptible attacks), and the vigil break (cancels anything, including the rite). Cancelling puts the attack on half its cooldown and sets a 20-tick gap.

---

### P0 Awaken / Vesting

Legal actions: `vesting_chime` only (scripted). The Deacon is immune and rises out of the altar (`vesting` animation, 6 s). Flagstones come online ring by ring from the plinth (rings 1–5 at ticks 20, 36, 52, 68, 84: mote bursts and a rising crystal chime). Tick 120 the chime is scripted. Tick 200 → P1.

#### Attack: vesting_chime

| Field | Content |
|---|---|
| Id | `static_deacon:vesting_chime` (`DeaconAction.VESTING_CHIME`) |
| Display name | Vesting chime |
| Phases | P0 |
| Unlock condition | scripted by the crypt at P0 tick 120 |
| Weight / priority | forced; not in the bag |
| Cooldown | none; once per fight |
| Range / positioning | radius 12 horizontal from the Deacon's core, on the plinth; no facing needed |
| Telegraph | 40 ticks: `chime` animation (censer raised overhead); `chime.windup` swell; a mote ring grows from r 2 to r 12 under the Deacon every 5 ticks; **every 10 ticks the flagstone under each player is outlined in litany light** |
| Wind-up | the same 40 ticks; cannot move or cancel |
| Active | 1 tick: the chime (a church bell), 80 motes from the core |
| Recovery | 20 ticks; immune anyway |
| Hit resolution | 0 damage. Push 1.0 away horizontally + 0.4 up, only with line of sight from the core (pillars and stalls shelter). Anyone standing on a live flagstone reads *"You are standing on communion. The floor heals the Deacon."* |
| Projectile / AoE specs | instant radial r 12, LOS-gated |
| Interruptibility | never |
| Counterplay | nothing to survive; it teaches two things safely: the floor matters, and pillars stop the Deacon's effects |
| Multiplayer notes | every participant in radius; every participant on a live flagstone gets the outline and the line |
| Failure / edge cases | no one in radius: plays anyway. Deacon missing at tick 120: skipped (respawn is on the plinth before P1) |

---

### P1 Full nave heal

Legal actions: `communion_pulse`, `lattice_lash`, `static_bolt`. Gap 30. Communion 35–50% DR, 22.7–32 HP/s regen. Exit: C < 0.70 → P2 (15 flagstones stripped).

#### Attack: communion_pulse

| Field | Content |
|---|---|
| Id | `static_deacon:communion_pulse` |
| Display name | Communion pulse |
| Phases | P1, P2, P4 |
| Unlock condition | P1 entry; a live nave flagstone exists and the Deacon is communing (nave or plinth) |
| Weight / priority | P1 3 |
| Cooldown | 200 own; gap 30 |
| Range / positioning | the whole floor; fires from wherever it stands |
| Telegraph | 40 ticks: `pulse_windup` (palms down, core swelling), `pulse.charge` swell; **every live flagstone** shows four orbiting motes every 8 ticks; everyone reads *"Communion pulse. Get off the lit flagstones."* |
| Wind-up | 40 ticks; anchored; cannot cancel into another attack |
| Active | 1 tick: the pulse (low bell), motes burst from every live flagstone |
| Recovery | 20 ticks; not vulnerable |
| Hit resolution | every player whose feet are on a LIVE or RESEEDING flagstone, or the plinth: 3 indirect magic (**TUNE ME**), Slowness II 60 ticks. The Deacon heals 2% max HP per player hit (**TUNE ME**) |
| Projectile / AoE specs | instant; area = the live floor itself |
| Interruptibility | telegraph (burst stagger, stumble). If the Deacon loses its flagstone during the telegraph the pulse is cancelled |
| Counterplay | step onto dead ground (rubble, aisles, pillar slots) for the 2 s. It teaches "stand on what you have already stripped" |
| Multiplayer notes | hits everyone on live ground; each hit heals it, so a group standing on communion feeds it |
| Failure / edge cases | no live nave flagstones: never chosen. Everyone on dead ground: plays, no heal |

Measured: exactly 3 to a player on a live flagstone.

#### Attack: lattice_lash

| Field | Content |
|---|---|
| Id | `static_deacon:lattice_lash` |
| Display name | Lattice lash |
| Phases | P1, P2, P3, P4 (and the only legal attack while stranded) |
| Unlock condition | nearest target within 4.5 horizontal and 3 vertical |
| Weight / priority | P1 6, doubled when legal (in reach it would rather hit you) |
| Cooldown | 40 own; gap 30 |
| Range / positioning | melee sector: 110° (±55°) ahead, radius 4.5; facing **locks** at telegraph start |
| Telegraph | 16 ticks: `lash_windup` (the crystal lattice arm draws back across the body), `lash.windup` glass note; red marks on the floor along the sector at r 2.25 and r 4.5 every 4 ticks |
| Wind-up | 16 ticks; anchored; locked facing (does not track you) |
| Active | 4 ticks; the sweep lands at tick 1 (`lash`, `lash.swing`, glints along the arc) |
| Recovery | 16 ticks |
| Hit resolution | mob attack 9 (**TUNE ME**, scales with difficulty), push 1.0 away + 0.35 up, once per player per sweep |
| Projectile / AoE specs | sector hit test, players within 1.2 of the Deacon always hit |
| Interruptibility | telegraph (burst stagger, stumble) |
| Counterplay | sidestep out of the marked arc, or step behind it (the facing is locked), then punish the 16-tick recovery |
| Multiplayer notes | nearest player; the sector hits everyone in it |
| Failure / edge cases | target leaves reach during the wind-up: the sweep still fires where it was aimed |

Measured: exactly 9 to an unarmoured player in the sector.

#### Attack: static_bolt

| Field | Content |
|---|---|
| Id | `static_deacon:static_bolt` |
| Display name | Static bolt |
| Phases | P1, P2, P3, P4 |
| Unlock condition | P1 entry; any target |
| Weight / priority | P1 5 |
| Cooldown | 50 own; gap 30 |
| Range / positioning | any distance; fired from the lattice hand (`hand_l`); prefers a target in line of sight |
| Telegraph | 20 ticks: `bolt_cast` (lattice arm points), `bolt.windup` crackle, glints gather at the hand every 4 ticks |
| Wind-up | 20 ticks; anchored |
| Active | 4 ticks: **one bolt** at tick 0, `bolt.cast` |
| Recovery | 14 ticks |
| Hit resolution | projectile 7 (**TUNE ME**) and **static**: Mining Fatigue I for 80 ticks (**TUNE ME**) |
| Projectile / AoE specs | speed 1.1 blocks/tick, no gravity, straight, no lead in P1, 60-tick life, shatters on any block |
| Interruptibility | telegraph |
| Counterplay | break line of sight with a pillar; strafe sideways (no lead in P1). Getting hit while mining slows the strip, which is the point |
| Multiplayer notes | highest threat 70% of the time (the stripper), random 30%, among targets with line of sight if any |
| Failure / edge cases | target dead at fire: no bolt |

Measured: 7 per bolt to an unarmoured player.

---

### P2 Patchwork communion

Legal actions: `communion_pulse`, `lattice_lash`, `static_bolt`, `nave_shatter`, `homing_shard`, `litany_beam`. Gap 20. Communion DR and regen scale down with C. Exit: live = 0 → P3.

#### Attack: communion_pulse

Identical to P1 `communion_pulse`, with these overrides: weight 4, gap after 20. With fewer live flagstones there is more dead ground to stand on, but the Deacon heals just as much per player hit.

#### Attack: lattice_lash

Identical to P1 `lattice_lash`, with these overrides: weight 5, gap after 20.

#### Attack: static_bolt

Identical to P1 `static_bolt`, with these overrides: weight 4, gap after 20, active 10 ticks: **two bolts** at ticks 0 and 8, aim leads **50%** of the target's horizontal velocity × flight time.

#### Attack: nave_shatter

| Field | Content |
|---|---|
| Id | `static_deacon:nave_shatter` |
| Display name | Nave shatter |
| Phases | P2, P3, P4 |
| Unlock condition | P2 entry; nearest target within 11 |
| Weight / priority | P2 3 |
| Cooldown | 160 own; gap 20 |
| Range / positioning | a ground ring expanding from the Deacon's feet to r 12 |
| Telegraph | 30 ticks: `shatter_windup` (both arms up, it rises on the static), `shatter.windup` crackle; red rings on the floor at r 6 and r 12 every 5 ticks; *"Nave shatter. Jump the ring."* |
| Wind-up | 30 ticks; anchored |
| Active | 22 ticks: slam at tick 0 (`shatter`, screen shake within 20 blocks); ring radius r = 1 + 0.55 t, drawn in static dust every tick, `shatter.crack` every 4 ticks |
| Recovery | 30 ticks; **vulnerable, damage × 1.25** |
| Hit resolution | a player **on the ground** with horizontal distance in [r − 0.9, r + 0.3] when the ring passes: mob attack 8 (**TUNE ME**), push 0.8 out + 0.6 up, once per cast. Choir stalls the ring passes are broken (replaced 60 s later) |
| Projectile / AoE specs | expanding ground ring, 12 blocks in 22 ticks; not blocked by pillars |
| Interruptibility | telegraph |
| Counterplay | jump as the ring reaches you; or be more than 12 away. Punish the vent |
| Multiplayer notes | everyone the ring meets; chosen when someone is within 11 |
| Failure / edge cases | players in the air or on a pillar ledge are skipped; stalls outside the crypt layout are never touched |

#### Attack: homing_shard

| Field | Content |
|---|---|
| Id | `static_deacon:homing_shard` |
| Display name | Homing shard |
| Phases | P2, P3, P4 |
| Unlock condition | P2 entry |
| Weight / priority | P2 3 |
| Cooldown | 180 own; gap 20 |
| Range / positioning | any target; shards leave the halo |
| Telegraph | 30 ticks: `shard_cast` (halo flares to 1.45×, spins), `shard.windup`, glints at the halo every 5 ticks |
| Wind-up | 30 ticks; anchored |
| Active | shard k at tick 6k: **2 shards** (+1 below 50% HP), `shard.cast` |
| Recovery | 16 ticks |
| Hit resolution | projectile 5 (**TUNE ME**) and static (Mining Fatigue I 80 ticks) |
| Projectile / AoE specs | leaves at 0.35 blocks/tick drifting up for 8 ticks, then homes at 3°/tick at 0.35/tick; 200-tick life; shatters on any block; **any player hit breaks it** (melee, arrow, spell) |
| Interruptibility | telegraph; shards already out keep flying |
| Counterplay | put a pillar or stall between you and it (they are slow and turn wide), or swat them. A second player can clear shards off the stripper |
| Multiplayer notes | highest threat, which is usually whoever is stripping |
| Failure / edge cases | target dies: shards fly straight and expire |

#### Attack: litany_beam

| Field | Content |
|---|---|
| Id | `static_deacon:litany_beam` |
| Display name | Litany |
| Phases | P2, P3 |
| Unlock condition | P2 entry; a target standing on the nave floor |
| Weight / priority | P2 3 |
| Cooldown | 220 own; gap 20 |
| Range / positioning | a lane 3 wide (one slot column, x = lane centre ± 1.5) running the full length of the nave, chosen as the column the target stands in |
| Telegraph | 40 ticks: `beam_windup` (hands together, lifted slowly), `beam.windup` chant; the lane's two edges drawn in red along the floor every 4 ticks; *"Litany. Step out of the lit aisle."* |
| Wind-up | 40 ticks; anchored |
| Active | 30 ticks: the lane fills with litany light at two heights (`beam` loop, `beam.loop` chant); every 5 ticks each player inside the lane box (±1.5 of the lane centre, feet from −0.8 to +2.8 of the floor) takes the hit |
| Recovery | 30 ticks; **vulnerable, damage × 1.25** |
| Hit resolution | 4 indirect magic per contact (**TUNE ME**) and static. Standing in it the whole time: 6 contacts |
| Projectile / AoE specs | lane AoE, not blocked by anything (the aisle itself reads the litany) |
| Interruptibility | any time (burst stagger during the wind-up, stumble) |
| Counterplay | move one slot sideways (3 blocks in 2 s). Then punish the vent |
| Multiplayer notes | random target on the floor; the lane hits everyone in it |
| Failure / edge cases | target leaves the lane: it still fires where telegraphed |

Measured: 12 to a player who stood in the lane for three contacts.

---

### P3 Plinth hold

Legal actions: `lattice_lash`, `static_bolt`, `nave_shatter`, `homing_shard`, `plinth_pull`, `litany_beam`. Gap 15. On the plinth: 25% DR, 0.6%/s regen; off it: nothing. 900-tick clock → P4. Titles *"Vigil / Only the altar remains. Knock it off."*

#### Attack: lattice_lash

Identical to P1 `lattice_lash`, with these overrides: weight 5, gap after 15, **double lash**: active 14 ticks, a second sweep at tick 11 over the same sector (hit list cleared between). The combo lash after `plinth_pull` uses a 10-tick telegraph.

#### Attack: static_bolt

Identical to P1 `static_bolt`, with these overrides: weight 3, gap after 15, active 12 ticks: **three bolts** at ticks 0, 5, 10, lead **75%**.

#### Attack: nave_shatter

Identical to P2 `nave_shatter`, with these overrides: weight 4, gap after 15. From the plinth top the ring still travels along the floor (it is drawn at the Deacon's feet height, so jumping off the dais edge is enough).

#### Attack: homing_shard

Identical to P2 `homing_shard`, with these overrides: **3 shards** (+1 below 50% HP), homing **4°/tick**, gap after 15.

#### Attack: plinth_pull

| Field | Content |
|---|---|
| Id | `static_deacon:plinth_pull` |
| Display name | Censer pull |
| Phases | P3, P4 |
| Unlock condition | the Deacon is on the plinth and a target is 3.5–16 away horizontally with a clear line from the censer to their eyes |
| Weight / priority | P3 6 (the P3 priority), × 2 when nobody is within 4.5 |
| Cooldown | 140 own; gap 15 |
| Range / positioning | 3.5–16; from the plinth |
| Telegraph | 25 ticks: `pull_windup` (the censer whirled on its chain); a litany-light chain drawn from the censer to the target every 2 ticks and rendered client-side from the synced tether; the target alone hears `pull.windup` and reads *"The censer chain is on you. Break line of sight."* |
| Wind-up | 25 ticks; anchored |
| Active | 10 ticks. Tick 0: line-of-sight check from the censer to the target's eyes. Blocked: *"The chain caught on stone."*, nothing happens. Clear: the target is yanked toward a point 2.4 blocks from the plinth centre on their side (velocity 0.32 × (dest − pos) + 0.45 up, capped 2.6; player drag eats the rest), Slowness III 20 ticks, `pull.yank` |
| Recovery | 10 ticks, then a **combo `lattice_lash`** with a 10-tick telegraph if the target landed within 5 |
| Hit resolution | the pull itself does no damage; the lash that follows does 9 (§P1 lash) |
| Projectile / AoE specs | tether, single target, instant at fire |
| Interruptibility | any: a pillar or stall between the censer and you at tick 0 snaps it; burst stagger cancels the wind-up |
| Counterplay | break line of sight when the chain appears. Or accept the pull and sidestep the locked lash, then you are at the plinth where the vigil break happens anyway |
| Multiplayer notes | the nearest valid target. Partners cannot body-block it: the chain checks blocks, not entities, so only terrain snaps it |
| Failure / edge cases | target dies or leaves before tick 0: nothing; the Deacon knocked off the plinth during the wind-up: cancelled |

Measured (pull): a player 10 blocks out lands 4 blocks from the altar, inside the combo lash; from 16 out, about 6 blocks closer.

#### Attack: litany_beam

Identical to P2 `litany_beam`, with these overrides: gap after 15; **litany of the cross**: a second lane along X through the target's row (z = row centre ± 1.5) fires with it. *"Litany of the cross. Clear the lit aisle and the lit row."* Safe ground is anything outside both lanes.

---

### P4 Reseed rite

Legal actions: `reseed_rite` (forced whenever it has work and is off cooldown), `communion_pulse`, `lattice_lash`, `static_bolt`, `nave_shatter`, `homing_shard`, `plinth_pull`. Gap 25. Titles *"Reseed rite / By standing order. No quorum required."*

#### Attack: reseed_rite

| Field | Content |
|---|---|
| Id | `static_deacon:reseed_rite` |
| Display name | Reseed rite |
| Phases | P4 |
| Unlock condition | scripted on P4 entry; re-forced whenever the schedule has work, the recast cooldown (200 after a break) is over and it is not locked out of the plinth |
| Weight / priority | forced; not in the bag |
| Cooldown | none while unbroken; 200 recast after a rite break |
| Range / positioning | must be on the plinth: if not, it static-steps there first (20-tick dissolve), then begins |
| Telegraph | 40 ticks: `reseed_begin` (kneels, palms on the altar), `reseed.begin` chant; mote ring r 3.5 every 5 ticks; *"Reseed rite. Break its focus, or break the new flagstones."* The crypt also tolls `reseed.toll` crypt-wide on P4 entry |
| Wind-up | 40 ticks; kneeling; cannot move |
| Active | channel (`reseed` loop, halo spinning): every 10 ticks (**TUNE ME**) the next scheduled flagstone is re-laid as **unlit** consecrated endstone (the block layer above it swept) and turns **RESEEDING**; 40 ticks later (**TUNE ME**) it turns LIVE if still intact. The channel ends when the schedule is exhausted or live ≥ K (cap 600 ticks). **Rite DR 60%** throughout |
| Recovery | 30 ticks |
| Hit resolution | no damage; restores floor. Schedule = desecrated flagstones sorted by ring distance from the plinth (spread outward), shuffled within a ring, K × 1.5 long (slack for denials) |
| Projectile / AoE specs | floor pattern spreading from the plinth |
| Interruptibility | **rite break**: 10% max HP after DR inside 60 ticks knocks it off the plinth (as the vigil break), stops the channel and blocks a recast for 200 ticks. Flagstones already laid keep growing. Also: any growing flagstone can be broken or covered before it matures (**denied**; counted, and it does not come back this rite) |
| Counterplay | two answers. Burst the kneeling Deacon through 60% DR (hard), or run the spreading ring and break each flagstone as it appears (one block each; the unlit ones are the targets). A team does both |
| Multiplayer notes | co-op splits naturally: one bursts, one denies |
| Failure / edge cases | nothing desecrated to reseed: P4 ends at once back to the evaluated phase. Every scheduled flagstone denied: P3 with a 600-tick clock (*"Rite unfulfilled / No flagstone took. Vigil resumes."*). Deacon missing at entry: the respawned Deacon is forced into the rite |

Measured: target 12 on the first rite; channelling at DR 60%; a flagstone covered while growing is denied; 12 live after the rite, then P2.

#### Attack: communion_pulse

Identical to P1 `communion_pulse`, with these overrides: weight 3, gap after 25; **RESEEDING flagstones count as live ground** for who gets hit (standing on a growing flagstone to deny it costs you).

#### Attack: lattice_lash

Identical to P1 `lattice_lash`, with these overrides: weight 3, gap after 25.

#### Attack: static_bolt

Identical to P3 `static_bolt` (three bolts, 75% lead), with these overrides: weight 3, gap after 25.

#### Attack: nave_shatter

Identical to P2 `nave_shatter`, with these overrides: weight 2, gap after 25.

#### Attack: homing_shard

Identical to P2 `homing_shard` (2 shards, +1 below 50%, 3°/tick), with this override: gap after 25. Shards chase the highest-threat player, who in P4 is usually the one denying flagstones.

#### Attack: plinth_pull

Identical to P3 `plinth_pull`, with these overrides: weight 3, gap after 25. Pulling the denier back to the altar is its defence of the rite.

---

### Phase transition matrix

| From | To | Trigger (predicate, ticks) | Timer and state rules |
|---|---|---|---|
| DORMANT | P0 | a non-spectator player inside `CryptLayout.trigger` (checked every 10 ticks) or bell use, and `START_CHECK` is not `interruptFalse` | phaseTicks = 0; floor re-laid, stalls replaced, plinth checked; door grated; chunks forced; Deacon spawned on the plinth with HP from participant count; `ENCOUNTER_STARTED`, `BossEvents.STARTED`, advancement `enter_crypt` |
| P0 | P1 | phaseTicks ≥ 200 | then re-evaluated at once (a floor stripped during P0 can land straight in P2 or P3) |
| P1 | P2 | C < 0.70 | none |
| P2 | P1 | C ≥ 0.70 | only reachable through a rite or `setfloor` |
| P1, P2 | P3 | live nave = 0 | exposeTicks = 900; Deacon action cancelled; advancement `stranded` if no rite has happened |
| P3 | P4 | exposeTicks reaches 0 | rite k + 1, K = min(36, 12 + 6(k − 1)); schedule built; action cancelled; rite forced; `RESEED_STARTED` |
| P4 | P1, P2 | live nave ≥ K; or schedule exhausted with nothing growing; or phaseTicks ≥ 1200, with live ≥ 1 | P1 if C ≥ 0.70 else P2; action cancelled |
| P4 | P3 | the same end conditions with live = 0 | exposeTicks = 600 |
| P3 | P2 | live nave > 0 (a growing flagstone matured after the rite ended) | action cancelled |
| P1 – P4 | DEFEATED | Deacon HP ≤ 0 | victory hooks (§11) |
| DEFEATED | CLEARED | 160 ticks | door open, bars cleared, chunks released |
| CLEARED | DORMANT | `rematch` and 6000 ticks since the kill | full reset |
| P0 – P4 | DORMANT | no participants for 1200 consecutive ticks, or `/skyloredeacon reset` | full reset; `RESET` |
| any | any | `phaseTicks` resets on every phase change; `exposeTicks` counts only in P3; growing flagstones keep maturing across phase changes; every timer pauses while nobody is in the crypt | |

---

## 10. Adds and environmental hazards

**No adds.** The floor is the second combatant. Adds would split attention away from the lesson.

| Hazard | Cadence | Counterplay |
|---|---|---|
| Live floor | always; `communion_pulse` punishes standing on it | stand on rubble and aisles; mine live flagstones from dead ones |
| Choir stalls | soft cover; nave_shatter breaks them; replaced 60 s later (**TUNE ME**) if nobody stands in the spot | rotate between stall rows |
| Pillars | permanent hard cover | the answer to bolts, shards and the censer chain |
| Reseeding flagstones | P4 only; a spreading ring from the altar | break them as they appear |
| Void | the crypt is sealed; the fall zone returns you to the entry pad | none needed |

---

## 11. Death, loot, trophy and pack events API

**Kill criteria.** HP reaches 0 in any fighting phase (gate applied), or a gate-bypassing source. DEFEATED plays the 7 s `death` animation (kneels, the halo drops, the core goes out, the vestments empty) with static dust every 4 ticks and a final burst at 140 ticks; the crypt clears at 160.

**Rewards on the kill**, per participant:
- `skylore_bosses:static_thurible` (epic trophy, stack 1, lore *"Still warm from a rite nobody finished."*).
- Advancements `skylore_bosses:static_deacon/deacon_silenced`; hidden `no_reseed` if no rite ever started.
- `victoryFunction` from config, run as the player at permission 2.

**Loot table** `skylore_bosses:entities/static_deacon`: 4–8 ender pearls, 6–12 amethyst shards, 4–10 chorus fruit, 3–6 experience bottles, 16–32 end stone bricks. Vanilla only so the mod never names a missing item; the pack overrides the file.

**Advancements** (`skylore_bosses:static_deacon/...`): `enter_crypt` "Unscheduled Visitation", `first_flagstone` "Profane", `stranded` "Nothing Left to Kneel On" (P3 before any rite), `vigil_broken` "Kneel Elsewhere", `deacon_silenced` "Post Vacant" (also a `player_killed_entity` criterion), `no_reseed` "Lapsed" (hidden).

**Events** (`net.teamaof.skylorebosses.bosses.staticdeacon.api.DeaconEvents`, Architectury events, logical server):

| Event | Signature | Use |
|---|---|---|
| `START_CHECK` | `(level, origin, triggerPlayer) → EventResult` | refuse the start (`interruptFalse()`): the `church_of_ender` gate |
| `ENCOUNTER_STARTED` | `(level, origin, players)` | chapter text, music |
| `PHASE_CHANGED` | `(level, origin, from, to)` | analytics, pack VO |
| `FLAGSTONE_DESECRATED` | `(level, origin, slot, player?, cause, remaining)` | co-op credit, quest sub-tasks ("desecrate 10 flagstones") |
| `VIGIL_BROKEN` | `(level, origin, breaker?, rite)` | stats, John the Woken barks |
| `RESEED_STARTED` | `(level, origin, count, target)` | stall telemetry |
| `VICTORY` | `(level, origin, participants, stats{ticks, reseeds, deaths, flagstonesDesecrated, vigilBreaks, anyReseed})` | stage grant, quest completion |
| `RESET` | `(level, origin, reason)` | `abandoned`, `command`, `rematch`, `created`, `rebuild`, `removed`, `skip` |

Every kill also fires `BossEvents.DEFEATED(level, "static_deacon", origin, participants)`, and every start `BossEvents.STARTED`.

---

## 12. Quest, stage and chapter wiring

The mod exposes events, advancements and a config hook. The pack owns every id below. **The pack repository is not on this machine**, so no pack files were edited; the ids come from the Act III brief.

| Item | Owner | Value |
|---|---|---|
| Gate | pack | stage `church_of_ender` (START_CHECK) |
| Stage on kill | pack | `static_deacon_cleared` |
| Chapter | pack | `forbidden_knowledge` (John the Woken's spine). A small Church boss leaf is fine if the chapter is crowded |
| Quest | pack | new tile "The Undercroft" (or retarget of any Ender Guardian checkmark): task = advancement `skylore_bosses:static_deacon/deacon_silenced`. **Not** a `cataclysm:ender_guardian` kill. Optional sub-tasks: `first_flagstone`, `vigil_broken`. Vitae follow-ups may hang off it as flavour, never as a hard dependency |

Suggested pack glue (KubeJS server script; adapt the stage calls to Progressive Stages' API):

```js
// kubejs/server_scripts/skylore/static_deacon.js
const DeaconEvents = Java.loadClass('net.teamaof.skylorebosses.bosses.staticdeacon.api.DeaconEvents')
const BossEvents = Java.loadClass('net.teamaof.skylorebosses.core.api.BossEvents')
const EventResult = Java.loadClass('dev.architectury.event.EventResult')

DeaconEvents.START_CHECK.register((level, origin, player) => {
  if (!player.stages.has('church_of_ender')) {
    player.tell('The bell does not ring for the unconfirmed.')
    return EventResult.interruptFalse()
  }
  return EventResult.pass()
})

BossEvents.DEFEATED.register((level, bossId, origin, players) => {
  if (bossId == 'static_deacon') players.forEach(p => p.stages.add('static_deacon_cleared'))
})
```

Alternative without KubeJS: `victoryFunction = "skylore:act3/static_deacon_cleared"` and grant the stage from that mcfunction.

QuestQueen: an advancement task on `skylore_bosses:static_deacon/deacon_silenced` completes for every participant, because the mod awards it to all of them.

---

## 13. Java implementation outline

**Tick cadence.**

| What | Cadence |
|---|---|
| `Crypts.tickLevel` | every server level tick; skips crypts whose origin chunk is unloaded unless mid-fight (then chunks are forced) |
| Dormant trigger scan | every 10 ticks |
| Fall-zone rescue | every tick during a fight |
| Floor integrity scan (live + growing slots, 9 blocks + 9 above each) | every 10 ticks (at most 864 block reads) |
| Growing flagstones (mature / outline) | every tick / every 4 |
| Regen | every 10 ticks |
| Plinth column clear, plinth re-place | 10 / 40 ticks |
| Stall replacement, bar membership | 20 ticks |
| Bars | 5 ticks |
| Deacon: movement, facing, action slot | every tick; path refresh every 10 ticks |
| Deacon: threat decay | every 20 ticks |

**Networking.** No custom payloads. Deacon `SynchedEntityData`: `ACTION`, `STAGE`, `COMMUNE`, `STAGGERED`, `STEPPING`, `TETHER` (entity id of the pulled player). Floor state travels as block updates (9 per flagstone change). Telegraphs use `ServerLevel.sendParticles(player, type, longDistance = true, …)` for players within 128 blocks.

**Save and reload.** `Crypt.save()` writes phase, phaseTicks, fightTicks, exposeTicks, rite count and target, schedule and cursor, counters, every nave slot (state, maturity time, last desecrator), Deacon UUID and HP snapshot, participants, forced-chunk flag. The Deacon saves its crypt origin and home. On load the Deacon resumes idle (40-tick gap); in P4 the rite is re-forced by the IDLE rule. Missing for 40 ticks: respawned on the plinth with the saved HP. The periodic scan reconciles the saved floor with the blocks.

**Designer commands** (`/skylorebosses static_deacon …` or `/skyloredeacon …`, op level 2; they act on the crypt nearest the source, falling back to the nearest in the dimension):

| Command | Effect |
|---|---|
| `build [pos]` | place the test undercroft (default 30 above you) and register it |
| `register [pos]` | adopt an existing structure at pos without placing blocks |
| `unregister` | forget the nearest crypt |
| `start` / `reset` | start (runs `START_CHECK` with you) / reset to dormant |
| `skipphase` | P0 → P1; P1 → strip to 32/48 (P2); P2 → strip all (P3); P3 → end the clock (P4); P4 → complete the rite; dormant/cleared → start |
| `setfloor <n>` | force the live count (restores nearest-first, strips farthest-first) and re-evaluate the phase |
| `strip` | desecrate the live flagstone nearest you (fires the event with you as the player) |
| `breakplinthlock` | break the plinth hold: the vigil, or the rite if channelling, as if the burst landed |
| `reseed` | start the P4 rite now |
| `attack <id>` | force an attack now, telegraph included (works on a bench-test Deacon too) |
| `status` | phase, floor, growing, clock, rites, denials, vigil breaks, commune, DR, regen, and the Deacon's action/stage/timers/position/stance/path |
| `tp` | teleport to the entry pad |

**Bench-test mode.** A Deacon from the spawn egg (no crypt) stays near where it spawned, uses the P2 table with no floor rules, and accepts `attack <id>`. This is the void box for AI work.

**Verification (done).** `tools/testing/t_static_deacon.py` drives a dev client through Marionette: **75 checks, all passing**. Also run: Marionette's namespace sweep over `skylore_bosses` (28 blocks, 43 items, 71 models, 71 lang keys, 0 failures).

| Area | Result |
|---|---|
| Build | plinth 3×3×2, lit flagstones, pillars, stalls, bell, open door, 48 flagstones |
| P0 | walking in starts it; grate; Deacon on the plinth; immune; `enter_crypt`; chime scripted; P1 at 200 |
| Gate | numbers in §7 |
| Verbs | survival pickaxe mining, cobblestone placement, `/setblock` (scan) and TNT each desecrate; `first_flagstone`; action-bar feedback |
| Thresholds | 34/48 stays P1, 33/48 is P2, 0 is P3 |
| Stance | at 8/48 it walks onto a live flagstone; DR/regen scale; stripping its flagstone strands it (full damage) |
| Vigil | returns to the plinth, 25% DR, burst breaks it, `vigil_broken`, ×1.25 while desecrated, walks back after the lock |
| Rite | P3 clock → P4 target 12, channel at 60% DR, flagstones regrow outward, covering a growing one is denied, 12 live → P2 |
| Persistence | leave and reopen mid-fight: phase and floor identical, Deacon present |
| Anti-soft-lock | Deacon teleported out returns to the altar; removed plinth block restored; blocks on the altar cleared; a player dropped under the crypt is rescued to the entry pad |
| Attacks | all nine run telegraph → active → recovery; lash 9, pulse 3, bolt 7, litany 12 over three contacts; censer pull moves the player at least 4 blocks toward the altar |
| Victory | DEFEATED → trophy, `deacon_silenced`, `no_reseed` withheld after a rite → CLEARED at 160, door open |
| Abandon | nobody in the crypt for the timeout: dormant, floor re-laid |

Bugs the test found and that are fixed: the censer pull only moved players 3 blocks (drag), now over-driven; the stranded Deacon only walked between attacks (stranded priority rule); vanilla path tolerance left it one block short on a dead flagstone (final-approach steering); a player below the crypt was outside the participant box and never rescued (fall zone); designer commands failed when the command source sat far from the crypt (nearest-in-dimension fallback).

---

## 14. Assets list

Generated by `bosses/static_deacon/tools`. Textures were painted with the Codex CLI's image generator (`codex_textures.py`) and downsampled to game resolution; the model was built in Blockbench through the Blockbench MCP (`build_one.py`); sounds are synthesized placeholders.

**Models** (Blockbench sources in `bosses/static_deacon/models/<model>/`, Bedrock geometry read by GeckoLib):

| Model | Bones / locators | Animations |
|---|---|---|
| `static_deacon` (137 cubes) | static_deacon → hem; body → torso → core, stole, head → cowl, halo; arm_l → forearm_l → hand_l (lattice); arm_r → forearm_r → hand_r → chain_0 → chain_1 → censer. Locators: core, head, halo, hand_l, hand_r, censer, feet | loops: idle, walk, commune, vigil, reseed, stagger, beam, static_step. One-shots: vesting, chime, pulse_windup, pulse, lash_windup, lash, bolt_cast, shatter_windup, shatter, shard_cast, pull_windup, pull, beam_windup, reseed_begin, hurt, knockoff, step_out, step_in, death (hold) |
| `static_bolt` | bolt; trail | fly |
| `homing_shard` | shard; trail | fly |

Renders: `models/static_deacon/renders/` (front34, side, posed vigil, lash wind-up, litany, shatter wind-up, reseed, death).

**Textures.** Entity atlas from 16 Codex swatches (`textures/src/`: beryl, beryl_dark, alb, dalmatic, trim, stole, static, glyph_glow, core_glow, chain, brass, endstone, obsidian, shadow, halo, ender_flame) with a glowmask for the glow materials. Blocks (16×16): consecrated_endstone (+ derived `_unlit`), desecrated_endstone, altar_plinth_top/side, crypt_tile, crypt_subfloor, crypt_wall, crypt_pillar_side/top, beryl_lamp, crypt_pew, sacristy_bell, crypt_grate (cutout). Item: static_thurible (32×32).

**Particles** (shared registry): `static_dust`, `communion_mote`, `beryl_glint`, `litany_light`.

**Sounds** (36, `skylore_bosses:static_deacon.<id>`): absorb, beam.loop, beam.windup, bell, bolt.cast, bolt.impact, bolt.windup, chime, chime.windup, death, flagstone.desecrate, flagstone.live, flagstone.reseed, hurt, idle.hum, lash.swing, lash.windup, lockdown, pull.snap, pull.windup, pull.yank, pulse.charge, pulse.release, reseed.begin, reseed.chant, reseed.toll, shard.break, shard.cast, shard.windup, shatter.crack, shatter.windup, step, vesting.rise, victory, vigil.begin, vigil.break. Every one has a subtitle.

**Lang keys** (`static_deacon.*`): bossbar (name, title, floor, floor_count), status (p0, communion, plinth, stranded, p3_vigil, p3_stranded, p4, down), titles (vesting, vigil, reseed, rite_denied, victory + subs), log (p1, p2, desecrated, denied, reseed_done, vigil_broken, rite_broken, plinth_refused, unconsecrated, rescue), gate (communion, vigil, rite, stranded, desecrated, immune), warn (pulse, shatter, pull, pull_snapped, litany, litany_cross, reseed, on_communion), bell (denied, readmit, cleared, unregistered), advancements × 6, subtitles × 36, block/item/entity names, trophy lore.

**Placeholder-first path.** 1) Codex-painted swatches and procedural assembly (this). 2) Art pass: repaint `textures/src/*.png` (or the atlas directly) and re-run `export_to_mod.py`; bone names and locators are the contract with the Java code. 3) Sound pass: drop real OGGs over `sounds/static_deacon/*.ogg` with the same names.

Rebuild: Blockbench open with the MCP plugin, then in `bosses/static_deacon/tools`: `python codex_textures.py` (only regenerates missing textures), `python build_all.py`, `python export_to_mod.py`, `python gen_data.py`.

---

## 15. Balance sheet

Every value is **TUNE ME** and lives in `skylore_bosses-server.toml` under `[static_deacon.*]`.

| Key | Default | Meaning |
|---|---|---|
| `deacon.hp` | 800 | solo max HP |
| `deacon.mpHpPerPlayer` / `mpHpCap` | 0.5 / 2.5 | HP × (1 + 0.5 per extra), cap |
| `deacon.attackSpeed` | 1.0 | divides every gap and cooldown |
| `communion.naveRegen` | 0.04 | max HP/s regen on a live flagstone at C = 1 (× C) |
| `communion.naveDr` | 0.5 | DR on a live flagstone at C = 1 (× C) |
| `communion.plinthRegen` | 0.006 | vigil regen |
| `communion.plinthDr` | 0.25 | vigil DR |
| `communion.riteDr` | 0.6 | DR while channelling |
| `communion.p2Threshold` | 0.70 | P1 → P2 |
| `communion.vulnerableMult` | 1.25 | desecrated / vented recoveries |
| `communion.desecrationThreat` | 30 | threat per flagstone |
| `communion.stumbleTicks` | 20 | stagger when its flagstone dies |
| `vigil.exposeTicks` | 900 | P3 clock |
| `vigil.vigilBreakFraction` | 0.08 | burst (after DR) that knocks it off the plinth |
| `vigil.vigilBreakWindowTicks` | 60 | burst window |
| `vigil.knockoffStaggerTicks` | 60 | stagger after a break |
| `vigil.desecratedTicks` | 100 | ×1.25 after a break |
| `vigil.plinthLockTicks` | 100 | cannot re-mount |
| `reseed.baseFlagstones` / `perRite` / `maxFlagstones` | 12 / 6 / 36 | rite target K |
| `reseed.scheduleSlack` | 0.5 | schedule = K × 1.5 |
| `reseed.stepTicks` | 10 | between flagstones |
| `reseed.growTicks` | 40 | growing → live |
| `reseed.recastTicks` | 200 | after a rite break |
| `reseed.riteBreakFraction` | 0.10 | burst that breaks the rite |
| `reseed.timeoutTicks` | 1200 | P4 give-up |
| `reseed.abortExposeTicks` | 600 | P3 clock after a fully denied rite |
| `attacks.lashDamage` | 9 | |
| `attacks.boltDamage` | 7 | |
| `attacks.pulseDamage` / `pulseHeal` | 3 / 0.02 | per player hit |
| `attacks.shatterDamage` | 8 | |
| `attacks.shardDamage` | 5 | |
| `attacks.beamDamage` | 4 | per 5-tick contact |
| `attacks.staticTicks` | 80 | Mining Fatigue I |
| `crypt.dormantTimeoutTicks` | 1200 | abandon reset |
| `crypt.coverRegenTicks` | 1200 | stall replacement |
| `crypt.rematch` / `rematchDelayTicks` | true / 6000 | re-fight after a kill |
| `crypt.victoryFunction` | "" | pack hook |

Values in code (change in `DeaconAction.java` / `StaticDeaconEntity.java` if playtests demand): attack ticks and weights (§9), bolt speed 1.1, shard speed 0.35 and turn 3–4°/tick, lash 4.5 × 110°, shatter ring 12 in 22 ticks, litany lane width 3, pull range 3.5–16, stance re-pick 40 ticks, stuck 80 ticks, global gaps.

### Playtest watchlist

- **Mining speed vs regen.** The whole fight rests on "one block kills a flagstone". Efficiency V netherite strips a flagstone in about 3 ticks; an iron pickaxe with static on you takes about 50. If geared players strip the nave in under 40 s, raise `p2Threshold` pressure by raising `naveRegen` rather than making flagstones tougher (the verb must stay one block).
- **Co-op strip speed.** Four players with explosives can clear the nave in seconds. That is fine if the kill still needs the P3 window; watch `mpHpPerPlayer` first.
- **Plinth-hold DPS check.** If solo melee cannot break the vigil (64 after DR in 3 s = 28.4 raw DPS for 3 s), drop `vigilBreakFraction` to 0.06 before touching HP. Bows and spells must be able to do it too.
- **Stall vs reseed.** If teams deliberately let rites happen to farm P3 windows, raise `perRite`. If denial is too easy (running the ring), lower `stepTicks` to 6 so the ring outruns one player.
- **Communion pulse frustration.** Players mining a live flagstone are on dead ground if they mine from outside, so the pulse should only catch careless ones. If it catches careful players, shorten the pulse area to LIVE only in P4 too.
- **Stranded walk time.** The kill windows in P1/P2 are the walks between flagstones. If the Deacon's path is too short (dense patches), the stumble stagger (`stumbleTicks`) is the knob.
- **Ranged cheese from the door row or aisles.** Bolts and shards make it costly; if bow players sit behind stalls all fight, give `litany_beam` a lane in the aisles as well.
- **Static fatigue stacking.** Mining Fatigue I from several bolts does not stack in vanilla; if a pack effect makes it stack, cap it.

---

## 16. Canon and text rules checklist

- [x] Player-facing name is "The Static Deacon" everywhere (entity, bossbar, titles, advancements). "Reliquary Warden", "Sacristan" and "Ender Guardian" appear nowhere in lang or code strings.
- [x] No Calyx, infection, parasite, spore or "the path" language in any Deacon string or event.
- [x] Beryl crystalline affinity near endstone is the mechanic: communion is doctrine made mechanical ("Communion quorum", consecration, rite, vigil), not a random gimmick.
- [x] Voice: register entries, quorum counts, standing orders, requisitions ("By standing order. No quorum required.", "Post vacant. No replacement requisitioned.", "Your absence has been noted in the register."). The dryness lands on Church bureaucracy and hollow rite.
- [x] The Deacon never mocks the player. Warnings are instructions ("Get off the lit flagstones.", "Break line of sight.", "Jump the ring.").
- [x] No exclamation marks from the Deacon. `[DEACON]` prefix for its log lines.
- [x] The Deacon is a caretaker, not clergy: it is never called a priest; its "post" is a caretaker's post ("The Static Deacon takes its post.", "Post vacant.").
- [x] Titles fit the screen: the first in-game screenshots showed the long vesting and vigil titles overflowing; they were shortened to one word plus a short subtitle.
- Writers adding lines: keep lines under the action-bar width (about 60 characters for warnings); John the Woken and Beryl dialogue belongs to the pack's quest text, not the fight.

---

## 17. Open risks

| Risk | Status / mitigation |
|---|---|
| Creative-tier tools griefing the nave | intended: stripping is the verb. The shell (walls, ceiling, subfloor, pillars, plinth) is unbreakable, so tools can only remove what the fight wants removed. A player cannot dig down or out |
| Block-update lag from mass mining | each desecration writes 8 block states; a full strip is 432 updates over the fight. The 10-tick scan reads at most 864 states. TNT chains are the worst case (many slots at once); measured no hitch on one TNT |
| Chunk unload of floor state | slot state lives in SavedData, not in blocks; the fight force-loads the crypt's chunks; on load the scan reconciles |
| Flight cheesing above the crypt | the crypt is roofed (ceiling at +8); inside, elytra and creative flight gain nothing because heal and damage are about the floor. The Deacon's shards and bolts track flyers |
| Multiplayer desync | all state server-side; the floor is block state; the Deacon syncs flags only. The censer chain is drawn client-side from a synced entity id |
| Structure ownership with Skylore Islands | not solved in code: the real undercroft must be adopted with `register` or a pack script. Needed from the Islands agent: the Church structure id and whether it can fire a placement hook |
| Pathing edge cases | vanilla navigation for a 1.2 × 3.4 mob; holes left by mining are 1 deep; the stuck and leash rules static-step it if a layout traps it. A restyled structure must keep 2-wide gaps between stalls and pillars |
| Resistance-stacking players | pulse and litany are indirect magic (armour-ignoring, not resistance-ignoring); that is the pack's balance call |
| Several crypts in one dimension | supported (keyed by origin); the bell uses the nearest within 48, so keep crypts more than 96 apart |
| Stage and quest ids | taken from the brief (`church_of_ender`, `static_deacon_cleared`, `forbidden_knowledge`); the pack repo was not available to confirm |

---

## Implementation order

| # | Step | Status |
|---|---|---|
| 1 | Void-box controller + endstone heal tracker + plinth + bossbar | done: `Crypt`, `Flagstone`, scan + events, `damageReduction` / `regenPerSecond`, `DeaconBossBar` |
| 2 | Deacon entity idle + path-to-endstone + `static_bolt` with full telegraph | done |
| 3 | Floor strip detection + P0–P3 transitions + strand expose window | done, verified (mine, cover, explosion, scan; thresholds; vigil) |
| 4 | Full attack roster per phase | done: 9 actions, all forced and observed in game |
| 5 | P4 reseed loop | done, including denial, rite break and the fully-denied return to P3 |
| 6 | Arena template / structure hook + lockdown | test undercroft, grate, bell, rescue done; worldgen hook waits on Skylore Islands (§3, §17) |
| 7 | Pack API, advancements, quest/stage wiring | mod side done (§11); pack side documented (§12), not applied (pack repo not available) |
| 8 | Art / sound pass | Codex-painted textures, Blockbench model with 27 animations, synthesized sounds shipped; final sound pass pending |
