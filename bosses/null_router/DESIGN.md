# Null Router, the Unacked: design package

Skylore Act IV boss (Automaton network). Ships as the `null_router` module of the `skylore_bosses` mod: custom Java, NeoForge 21.1.x on Minecraft 1.21.1, Java 21. Iron's Spellbooks' citadel keeper and Cataclysm's netherite ministrosity were tonal reference only. Nothing here summons, subclasses, patches or needs Iron's, Cataclysm or AE2. The chassis is a first-class custom entity, and retries are custom packet entities, not reskinned zombies.

Every number marked **TUNE ME** is a first-pass value. All of them live in the `[null_router]` section of `skylore_bosses-server.toml` (§15 lists each key), so playtest tuning needs no rebuild.

Status: the module described here is implemented in `src/main/java/net/teamaof/skylorebosses/bosses/nullrouter/` and was exercised in a dev client through Marionette (`tools/testing/t_null_router.py`, 100 checks, §13 "Verification"). Where the code and this document disagree, the code is the bug.

> *"Request 404. Still serving."*

---

## 1. One-page fight bible

**Fantasy.** The Automaton guild built its network and then left it. One routing process never got the memo. Null Router is that process: an orphaned router rack in a clean-room vault, still accepting tickets, still queueing them, still waiting for someone to acknowledge the answer. Nobody has for a very long time. The queue is the fight, made physical: the boss bar is not its health, it is its backlog.

**Player fantasy.** You are the first technician through the door in years. You do not out-damage a queue. You **acknowledge** it: read the request on the board, set the three channel consoles to match, and in the few seconds the chassis stops being a hologram and docks, hit it hard enough to clear requests. Later the rack is rated for heat but not for immersion, so you learn to bring water to the window and keep it off the floor between windows.

**Win condition.** The unacked queue reaches 0. The chassis powers down on its pad.

**Fail states.**
- Player death: normal respawn. The vault stays sealed; the **service terminal** outside the door re-admits a participant to the entry pad. The fight continues for everyone else.
- Everyone leaves or dies and nobody is in the vault for 60 s (**TUNE ME**): the vault resets to dormant. Consoles, lamps, gates and water are reset, the chassis and retries vanish, the door opens. No partial progress carries over.
- Stalling: not a fail state, a punishment. Every stall period without an ACK retransmits a request (queue +1). A queue that climbs, or a P3 that never sees a wet ACK, calls the **retry storm** (P4).

**Tone.** Dry systems language, polite error strings, lethal routing. It sasses the abandoned bureaucracy, never the player: *"Request retransmitted. Queue: 19. Thank you for your patience."*, *"Your request has been routed to the service alcove. Please wait."*, *"Window quota reached. Further damage is logged and ignored."* No Calyx, infection, parasite, "the path" or Act V ending language anywhere in this fight.

**Pedagogy.** The verb is **acknowledge**: not demolish (Overhead's pylons), not strip (the Deacon's floor), not light (Amanita). The fight is the dress rehearsal for Matris Calyx's open windows, with gated damage **and** add pressure at once:
- The first hit on the ghost says *"NAK. Chassis unacknowledged. Match the request on the consoles first."*
- The P0 tutorial request is one glyph on all three channels with two consoles wrong.
- The boot chime beams from the lens to each console and to the board, and the second bar spells the request out in glyphs.
- Second clears reward wet timing and knowing which retry to kill first (the one uplinking to a console that already matches).

---

## 2. Mod architecture

Null Router is a `BossModule` inside the existing mod, like Overhead and the Deacon. A sibling jar was considered and rejected: the shared core (particles, sounds, `AnimFx`, `Telegraph`, screen shake, `BossEvents`, config sections, `/skylorebosses`) already exists, and one jar is simpler for the pack to list.

| Item | Value |
|---|---|
| Mod id | `skylore_bosses` (module id `null_router`) |
| Package | `net.teamaof.skylorebosses.bosses.nullrouter` |
| Hard deps | NeoForge, Architectury API 13, GeckoLib 4.7 (already required by the mod) |
| Soft deps | none. AE2, Create, Mekanism, Cyberware, Progressive Stages, QuestQueen, Iron's and Cataclysm are touched only by the pack, through events and datapacks. Create and Mekanism water works because the fight reads fluid state (`FluidTags.WATER`) and vanilla damage sources, never mod APIs |
| License | MIT, same as the mod |

```
bosses/nullrouter/
├─ NullRouterBoss.java        module entry: registries, config, events, client, commands (+ /skylorenullrouter alias)
├─ RouterConfig.java          every TUNE ME value (server config) + phase helpers (stall, caps, multipliers)
├─ RouterLocators.java        GENERATED: model locators (lens, core, port_l, port_r, port_b, antenna, crown, base)
├─ RouterSoundIds.java        GENERATED: 48 sound ids
├─ api/RouterEvents.java      stable pack hooks (§11)
├─ registry/                  RouterBlocks, RouterEntities, RouterItems
├─ block/                     Glyph (the alphabet), ChannelConsoleBlock (facing, glyph, status), RequestLampBlock
│                             (glyph, mode), RoutingGateBlock (open), ServiceTerminalBlock; CoolantVentBlock (lit)
├─ encounter/
│  ├─ Vaults.java             SavedData: every vault in a level, keyed by origin; ticks them
│  ├─ Vault.java              one vault + its fight: phase machine, queue, request/decoy, consoles, arming, ACK window,
│  │                          wet/dry math, misroute, hazards (short, drain, airflow), bars, saves, designer hooks
│  ├─ Phase                   state type
│  ├─ VaultLayout.java        geometry (consoles, lamps, board, pad, basins, pillars, alcoves, ports, volumes)
│  ├─ VaultBuilder.java       the mod-placed test vault, console reset, alcove gates, door grate, self-repair
│  ├─ RouterBossBar.java      queue bar + request/ACK bar
│  └─ RouterCommonEvents.java level tick, deaths, retry kills, placement guard
├─ entity/
│  ├─ NullRouterEntity.java   the chassis: ghost/docking/solid/releasing modes, action slot, every attack, damage hook
│  ├─ RouterAction.java       attack roster data (ticks, cooldowns, weights, per-phase deltas)
│  └─ RetryPacketEntity.java  the add: console uplink AI, aggro, reroute, timeout
├─ client/                    RouterClient, NullRouterRenderer (ghost translucency), RetryPacketRenderer
└─ command/RouterCommands.java
```

Shared-core changes made for this boss:
- Four particles added to the shared library: `packet_spark`, `coolant_mist`, `ack_glint` and `null_beam`.
- The Blockbench texture pipeline gained an `automaton` palette.
- The sound synthesizer gained a `modem` voice (dial-up handshake).

**Server vs client.** All logic is server-side. The client gets:
- Synced entity data on the chassis (`MODE`, `WET`, `WARN`, `ACTION`, `STAGE`) and on retries (`UPLINK`, `AGGRO`).
- The consoles' and lamps' own block states (`channel_console[facing,glyph,status]`, `request_lamp[glyph,mode]`). These are the visible truth for every player and for LOD renderers.
- Two vanilla boss bars, titles and action-bar lines.
- Server-sent long-range telegraph particles, client animation keyframes, and the shared `SBNetwork.ScreenFx` shake.

There are **no Null Router custom packets**, so there is nothing to desync: every player reads the same block states.

**What the pack still owns** (datapack or KubeJS, never Java):
- The `ae2_cpu` start gate, the `automaton_network` stage grant, and the `modular_expression` chapter tile (§12).
- The soft `teknari_cybernetics` flavour hook (§12).
- Loot override: `data/skylore_bosses/loot_table/entities/null_router.json` ships vanilla quartz/redstone/copper; the pack replaces it with AE2 or Cyberware parts.
- `victoryFunction` in config: an mcfunction run as each participant on the clear.
- Structure placement of the real vault (§3).

---

## 3. Arena: the Automaton vault

### Footprint

Offsets are from the vault **origin**: the centre block of the floor layer, directly under the chassis pad. Players stand at `origin.y + 1`. North is −Z. All numbers are in `VaultLayout.java`.

```
         x: -16 ........................ 0 ........................ +16
 z=-16  █████████████████████████████ wall █████████████████████████████
 z=-15  █                    [board: row B lamps ○▲□ at y+9]           █   north-wall request board, 3 wide, framed
                             [board: row A lamps ○▲□ at y+8]
 z=-11                                  ▮  (backing column)
 z=-10                                 [B]  console B, faces south; lamps at y+3 (row A) and y+4 (row B)
 z=-8   █        ▓▓ rack column ▓▓                    ▓▓ rack column ▓▓        █   2x2 pillars, floor to ceiling
 z=-3              ~  basin                               basin  ~
 z=-2..2  ▮[A]                     ▒▒▒▒▒ pad 5x5 ▒▒▒▒▒                   [C]▮   A at x=-10 faces east, C at x=+10 faces west
 z=+3              ~  basin                               basin  ~
 z=+6   █        ▓▓ rack column ▓▓                    ▓▓ rack column ▓▓        █
 z=+11  ┌╌╌╌╌gate╌╌╌╌┐                                    ┌╌╌╌╌gate╌╌╌╌┐
 z=+12  ╎ alcove W   ╎             e (entry pad 0,+1,12)   ╎ alcove E   ╎   service alcoves x -15..-12 / 12..15, z 12..15
 z=+16  ███████████████████████████ ▒door▒ ████████████████████████████   3 wide, 3 high; blast grate in lockdown
        landing z 17..22, service terminal at (0, +1, 20)
```

| Element | Spec |
|---|---|
| Floor | 31 × 31 `vault_floor` (clean-room raised floor, pale so water reads) at y 0; `vault_wall` under it at y −1; all unbreakable |
| Chassis pad | 5 × 5 `chassis_pad` at the centre (|x|, |z| ≤ 2). The chassis hovers over it as a ghost (feet at +5) and docks on it in an ACK window (feet at +1). Nothing solid may be placed in the 5 × 5 × 6 column above it during a fight (placement cancelled, anything else broken every 10 ticks) |
| Coolant basins (leak pads) | four 1 × 1 recesses at (±3, ±3), `coolant_vent` underneath. Dry until P3; in P3+ ACK windows the vents open and each basin holds a water source (scoop it with a bucket). Drained when the window closes |
| Consoles | three `channel_console` blocks at A (−10, +1, 0) facing east, B (0, +1, −10) facing south and C (+10, +1, 0) facing west. Each is backed by a 5-high `vault_pillar` column carrying its lamp stack. Unbreakable, explosion-proof, piston-proof. Nothing may be placed on a console's stand spot |
| Request display | row A lamp at +3 and row B lamp at +4 over each console, plus the north-wall board (x −1..1, y +8 row A, y +9 row B) in a pillar frame. One glyph per lamp. The head row is bright (`mode=head`), the decoy row dim (`mode=next`), an unused row dark |
| Cover | four 2 × 2 `vault_pillar` server-rack columns, floor to ceiling, at (−8..−7, −8..−7), (6..7, −8..−7), (−8..−7, 6..7), (6..7, 6..7). Hard line-of-sight cover for `ghost_lance`. They do not set consoles and cannot be pushed |
| Service alcoves | two corner rooms by the door (x −15..−12 / 12..15, z 12..15) behind an L of `routing_gate` blocks 4 high. Open gates have no collision (a floor strip); a closed gate is a red lattice that holds a misrouted player for 60 ticks |
| Wall ports | (±13, +1, −6), (±13, +1, +5): where `retry_storm` pours packets in |
| Walls, ceiling | `vault_wall` ring at ±16, `vault_ceiling` at y +13 (air +1..+12), `vault_light` on a 6-block grid |
| Door | south wall, |x| ≤ 1, y +1..+3; `vault_grate` in lockdown |
| Entry pad | (0, +1, 12): where the terminal and the void rescue put players |
| Service terminal | (0, +1, 20) on the landing outside the door |

### Height bands (Y)

| Band | y above floor surface | Used by |
|---|---|---|
| Floor | 0 | water here outside a window shorts players |
| Basin water | −1..0 | coolant sources in P3+ windows (sit below floor level so they do not spread) |
| Docked chassis | 0 .. +2.8 | solid, damageable; water on the pad touches its hitbox |
| Hover chassis (ghost) | +4 .. +6.8 (±0.25 bob) | out of melee reach, immune; lens at about +5.8 |
| Lamps | +2, +3 above each console | head / decoy glyphs |
| Board | +7 (row A), +8 (row B) | readable from every console and the pad |
| Airflow ceiling | +7 | a non-creative player above this who is off the ground is pushed down (*"Clean-room airflow. Please remain at floor level."*) and loses elytra flight |
| Ceiling | +12 | |

### Entry and lockdown

A fight starts in either of two ways, and both go through `RouterEvents.START_CHECK` first, so the pack can refuse (the `ae2_cpu` gate):
- walking into the interior (the trigger box excludes the rows by the door, z ≥ 10), or
- using the service terminal.

On start:
- The queue is set (§7), consoles reset to ○ ▲ □, and the door fills with `vault_grate`.
- The chassis boots over the pad.
- The chunks covering the vault plus 8 blocks are force-loaded until the fight ends.

During a fight the terminal re-admits a player to the entry pad. The door opens on reset and 160 ticks after victory.

### Void fall

Anyone in the **fall zone** during a fight (under the vault footprint plus 6 blocks, from 3 below the floor down 128) is put back on the entry pad with 4 damage and Slow Falling 3 s: *"Returned to the vault. Your absence has been logged."* The chassis cannot leave the pad column: its position is set from the vault every tick (no physics, no pathing), so no knockback, fluid, piston or teleport effect can carry it out. Misroute teleports only target alcove spots that are checked for two blocks of air over a solid floor.

### Distant Horizons silhouette notes

- The request display and consoles are block states, so LODs show the lamps (bright head row, dim decoy) and console glyphs; a stalled vault reads the same far away.
- The chassis and retries are GeckoLib entities and are invisible in LODs. The island above the vault should carry a vanilla-block landmark, such as a tall end-rod antenna mast over the pad, so the vault reads from a distance.
- The shell is dark `vault_wall` with a lit ceiling grid inside; from outside it is a dark cube. A few exterior `vault_light` blocks on the structure's outer face help it read at night.

### Ownership handoff (Skylore Islands)

| Piece | Owner |
|---|---|
| Worldgen placement of the Automaton vault's sky island, exterior dressing, loot around it | **Skylore Islands** agent (structure NBT + placement) |
| Encounter blocks, chassis, retries, the fight, the vault registry | this mod |
| Test vault for AI bring-up | this mod: `/skylorenullrouter build [pos]` |

**Contract.** The structure must place the same encounter blocks at the same offsets as `VaultBuilder`. At minimum:
- the three `channel_console` blocks facing the pad, with their `request_lamp` pairs and the board lamps;
- the 5 × 5 `chassis_pad`;
- the four basins over `coolant_vent`;
- the two alcoves with `routing_gate` Ls;
- the unbreakable shell, the door opening, and a `service_terminal` within 48 blocks.

Pillars, lights and wall styling may change, as long as some hard cover stays between the pad and the consoles. The mod adopts the structure with `/skylorenullrouter register <origin>` (no blocks placed). An automatic hook on structure placement is open (§17): the Islands agent has to confirm the structure id before final island terrain is authored.

**Fallback test box.** `VaultBuilder` is the full vault in one command. The minimum box that still exercises every rule is a flat room with the three consoles and lamps, the pad, one basin and one alcove.

---

## 4. Phase flowchart

```
DORMANT ──(player in the trigger, or the terminal; START_CHECK allows)──► P0 BOOT (queue = Q0)
P0: t=0 boot; t=60 boot_chime (fires ~t=100); t=110 tutorial request + request_ping
P0 ──(first ACK window closes)──► P1 SINGLE
P1 ──(window closes with queue ≤ ⌈0.42·Q0⌉)──► P3 COOLANT
P1 ──(window closes with queue ≤ ⌈0.75·Q0⌉, or the 3rd ACK in P1)──► P2 DUAL
P2 ──(window closes with queue ≤ ⌈0.42·Q0⌉)──► P3 COOLANT
P1/P2/P3 ──(queue ≥ lowest queue this phase + 3, between windows)──► P4 STORM
P3 ──(1200 ticks without a wet ACK)──► P4 STORM
P4 ──(a window closes that was wet and cleared ≥ 1)──► by queue: P3 if ≤ ⌈0.42·Q0⌉, else P2 if ≤ ⌈0.75·Q0⌉, else P1
any fighting phase ──(queue reaches 0 inside a window)──► DEFEATED ──(160 ticks)──► CLEARED ──(rematch, 6000 ticks)──► DORMANT
any fighting phase ──(no participants for 1200 ticks, or /reset)──► DORMANT (full reset)
```

With Q0 = 24 (solo): P2 at ≤ 18, P3 at ≤ 11 (all **TUNE ME**).

**Phase changes happen only when a window closes** (never mid-window) or between windows (P4 triggers). **Timer rules:**
- `phaseTicks`, `acksInPhase` and the stall clock reset on every phase change.
- The lowest queue of the phase (`queueLow`) resets on phase entry and follows every decrease.
- The P3 no-wet clock (`sinceWetAck`) resets on P3 entry and on every wet ACK.
- Stall, rotation, storm and P4 clocks run only between windows and only while someone is in the vault.

| Phase | Requests | Retries (cap) | Throughput dry / wet | Window quota | Stall | Intent |
|---|---|---|---|---|---|---|
| P0 Boot | one tutorial pattern (same glyph ×3, two consoles wrong) | none | 1.0 / 1.5 | 2 | none | teach "match, then hit" |
| P1 Single request | one random pattern, ≥ 2 channels off the consoles | 2 | 1.0 / 1.5 | 3 | +1 / 600 t | ACK under light interference: retries undo one matched console |
| P2 Dual queue | head + decoy (≥ 2 channels apart), head rotates every 240 t | 3 | 1.0 / 1.5 | 3 | +1 / 500 t | reading the display matters; misroutes |
| P3 Coolant | head + decoy, no rotation; coolant vents open in each window | 3 | **0.25 / 2.5** | dry 1, wet 4 | +1 / 400 t | a wet chassis is the real DPS; a dry ACK is a scratch |
| P4 Retry storm | head + decoy, rotation every 160 t, a random console flicked every 200 t | 6 | 0.25 / 2.5 | dry 1, wet 4 | +1 / 300 t | packet flood; a wet ACK pulls you back |

---

## 5. Entity and component design

| Component | Kind | Why |
|---|---|---|
| `Vault` in `Vaults` | SavedData (per level, map of vaults) | source of truth: phase, queue and its start, both requests and which row is head, console glyphs, arming, ACK stage/ticks/credit/cleared/wet, timers, gates, stats, chassis UUID, misroute cooldowns. Survives relog, restart and chunk unload |
| `NullRouterEntity` | `Monster` + `GeoEntity`, `skylore_bosses:null_router`, 2.4 × 2.8, no gravity, no physics | the chassis. Position owned by the vault (pad column, hover/dock height); one action slot; persistent; health never changes (the queue is the bar). A view: missing for 40 ticks → respawned in the vault's current mode |
| `RetryPacketEntity` | `Monster` + `GeoEntity`, `skylore_bosses:retry_packet`, 0.7 × 0.7 | the add: ground-pathing packet with a console uplink (§10). Water-sensitive. Bound to its vault by origin |
| Channel consoles | `ChannelConsoleBlock`, block state `facing × glyph × status` (48), no BE | the verb. Right-click cycles the glyph (the vault is authoritative). `status` = idle / arming / locked / alert is the bezel colour |
| Request display | `RequestLampBlock`, block state `glyph × mode` | six lamps over the consoles plus six on the board, all set from the vault |
| Coolant vents | `CoolantVentBlock` (`lit`) + the basin air/water above | the P3+ water source |
| Service alcove gates | `RoutingGateBlock` (`open`) | hold a misrouted player; walk-through when open |
| Service terminal | directional block | start / re-admit / cleared message |
| Boss bars | two `ServerBossEvent`s owned by the vault | the queue, and the request or ACK window (§8) |
| Telegraphs | server → per-player long-range particles (`core.fx.Telegraph`) | lance lines, TTL rings, flick beams, pings render across the vault |
| Ghost vs solid | `NullRouterRenderer` | translucent render type always; colour alpha 0.32 (±0.08 shimmer, occasional dropout) cyan-tinted as a ghost, 1.0 white when solid, lerped over the edge i-frames |
| VFX on the model | GeckoLib particle and sound keyframes | null beams, ack glints, packet sparks, coolant mist, sparks, smoke |

Sub-parts (the status lens and LED ring, port pods, cable bundles, crown and antennas, orbiting drive sleds, fins, clamps) are **bones of one model**, not separate entities or hitboxes. The ACK window is the only damage gate, so nothing needs a separate hurtbox.

**The ghost's hurtbox.** The ghost keeps a hitbox for **feedback only**. Melee on it is refused with a NAK line (at most once per 3 s per player), sparks and a buzz. Projectiles pass straight through (`canBeHitByProjectile` is false while a ghost), so arrows are not eaten. This reads better on a first clear than a truly empty hitbox would: the player swings, is told why nothing happened, and is told what to do.

---

## 6. Ack / misroute system card

| Rule | Spec |
|---|---|
| Channels | **3**: A (west), B (north), C (east), read left to right from the door and on the board |
| States per channel | **3 glyphs**: ○ circle (cyan), ▲ triangle (amber), □ square (magenta). Shape and colour both differ, so it reads without colour vision |
| Display | per console: row A lamp (+2 over the console) and row B lamp (+3). The north-wall board repeats both rows for all three channels. The second boss bar spells the head out: *"Request 0x10E: ○ ▲ □"*. In P2+ it adds *"(head on row A; the dim row is a stale request)"* |
| Input | right-click the console (empty hand or any item; buckets are not emptied) cycles ○ → ▲ → □ → ○. 4-tick per-console cooldown (**TUNE ME**). Only participants inside the vault volume may flip. No item is ever required |
| Match check | server-authoritative, after every **player** flip: consoles = head → **arm (head)**; consoles = decoy → **arm (decoy)**; else disarm |
| Arming (misread grace) | a matched pattern must hold for **20 ticks** (**TUNE ME**) before it commits. Consoles show amber `arming` bezels and the bar turns yellow (*"Pattern settling"*). Any flip in that time re-evaluates. Head rotation is held while a pattern settles, so a misroute is always a misread, never bad timing |
| Commit: head | opens the **ACK window** |
| Commit: decoy | **misroute** (below) |
| Non-player flips | retries, `console_flick` and the storm never complete a request: they choose a glyph that forms neither the head nor the decoy. A flip under a settling pattern cancels the arm |
| Request generation | P0: one glyph ×3, one console kept right, two set wrong. P1+: the head is ≥ 2 channels away from the current consoles. The decoy (P2+) is ≥ 2 channels away from the head and never equal to the consoles, so one mis-set console can never complete it |

### ACK window (`ack_lock`, a system card)

| Stage | Ticks (**TUNE ME**) | Chassis | Damage |
|---|---|---|---|
| SOLIDIFY | 10 | fades from ghost to solid while dropping from hover to the pad (clamps swing down, pods slide out); `dock` animation; `ack` bell; consoles `locked` (white bezel); green beams from the consoles to the chassis; P3+: coolant vents open | **i-frames** (hits say *"Docking. The window opens in a moment."*); water already counts |
| OPEN | 110 | docked, fully opaque, lens wide; the last 30 ticks flash a closing warning (`solid_warn`, `ack.warn` beeps) and stay damageable | counts: converted to cleared requests (§7) |
| RELEASE | 10 | lifts off and fades back to a ghost; `release` animation | **i-frames** (*"Window closed. Undocking."*) |
| close | | ghost; consoles unlock; all water in the vault is drained; vents close; phase flow (§4); the next request is issued | |

The chassis attacks nothing during a window: opening one cancels whatever it was doing, except a misroute pulse already in flight, which still lands.

### Misroute

| Rule | Spec |
|---|---|
| Trigger | the consoles settle on the **decoy** (P2+, or `/skylorenullrouter misroute`) |
| Who is misrouted (co-op) | only the **player whose flip completed the pattern** (the arming actor). Nobody else is moved; consoles stay usable by everyone, so one mis-click cannot soft-lock the squad |
| Cost | queue +1 (**TUNE ME**, capped at the starting queue); no damage to the chassis; the decoy is re-rolled (the head stays) |
| Primary | `misroute_pulse`: a 10-tick red beam from the lens to the actor, then a teleport into the **service alcove farthest from them**. That alcove's gate closes for **60 ticks** (**TUNE ME**), then opens. Title: *"Misrouted / Your request has been routed to the service alcove. Please wait."* |
| Fallback 1 | no usable alcove (both holding someone, or blocked by blocks): **swap places with a random retry packet** |
| Fallback 2 | no retry either: 4 null-route damage (**TUNE ME**) and a shove away from the pad |
| Rate limit | a player misrouted again within **300 ticks** (**TUNE ME**) is not teleported. They get Slowness III for 3 s, and the queue still takes the +1. No infinite teleport loop |
| Advancement | `null_router/misrouted` (hidden) to every participant |

### Console interact rules, griefing and chunks

- Consoles are unbreakable (hardness −1), explosion-proof (3.6M) and piston-proof. Mining is not the puzzle and cannot happen outside creative.
- A console that goes missing (`/setblock`, another mod) is replaced from the vault within 20 ticks. Its glyph and status are re-applied every tick.
- Nothing may be placed on a console's stand spot during a fight. Placing blocks elsewhere to wall a console off does not strand retries: one that makes no progress for 80 ticks reroutes (hops) to the console's stand spot.
- Chunks: the vault force-loads its chunks for the whole fight. If a console chunk were somehow unloaded, its state lives in the vault and is re-applied the moment it loads again. Players cannot flip a console in an unloaded chunk, so the two can never disagree.

---

## 7. Queue, damage and wet math

The boss bar is the **unacked request queue**, not an HP pool. Health never changes; damage only matters inside an OPEN window.

```
Q0          = min(queueCap, queueBase + queuePerPlayer × (participants − 1))   = 24 solo, +4 per extra player, cap 40
                                                                                  (TUNE ME; fixed at the start)
wet         = the chassis touched water within the last wetTicks (40) during SOLIDIFY or OPEN
windowWet   = any water contact this window
mult        = P0–P2: dry 1.00, wet 1.50        P3–P4: dry 0.25, wet 2.50                      (TUNE ME)
cap         = P0: 2      P1–P2: 3      P3–P4: wet window 4, dry window 1                      (TUNE ME)

on each damage event A inside OPEN (after vanilla i-frames; the chassis has 0 armour):
    credit += A × mult(wet) / damagePerRequest(20)
    due     = floor(credit)
    if wet and due < wetMinimum(1): due = 1, credit = max(credit, 1)       -- a wet tap always matters
    pop     = min(min(due, cap) − clearedThisWindow, queue)
    queue  −= pop; clearedThisWindow += pop
    queue == 0 → victory
credit and clearedThisWindow reset every window (a dry P3 partial is lost: that is the "scratch")
```

Code: `Vault.onAckDamage`, `RouterConfig.mult`, `RouterConfig.ackCap`, `NullRouterEntity.hurt` / `actuallyHurt`. `BYPASSES_INVULNERABILITY` sources (`/kill`, void) do nothing to the chassis: the win is the queue (`/skylorenullrouter setqueue 0` for designers).

**What counts as water on the chassis** (only inside SOLIDIFY/OPEN):
- any water fluid block overlapping its docked hitbox: a bucket poured on the pad, a basin scooped and poured, Create or Mekanism output;
- the vanilla water-sensitivity tick (it is water-sensitive like an enderman), which also catches rain;
- a splash water bottle.

Water on a ghost does nothing: it hovers four blocks up, and contact outside a window is ignored.

**Floor water (the self-short).** Between windows, a player whose body is in water takes **3 short-circuit damage every 20 ticks** (**TUNE ME**, one per player i-frame cycle) plus Slowness II 2 s: *"Short circuit. Standing water outside an ACK window."* The same water that multiplies the chassis punishes you if it is still on the floor when the window shuts.

**Draining (anti-flood).** Every 20 ticks the vault scans the floor band (y 0..+3, the whole 31 × 31). It removes water, including waterlogging, that has sat for **100 ticks** (**TUNE ME**) outside a window. At every window close it drains everything at once. A permanent flood therefore shorts the players standing in it and is gone within 5 s of appearing between windows.

**Measured in game** (Marionette, normal difficulty):

| Case | Expected | Measured |
|---|---|---|
| P0 ghost / boot, 20 damage | 0 | 0 |
| P0 open, 20 dry | 1 | 1 |
| P0 open, third hit (quota 2) | 0 + quota line | 0 |
| P3 open, 20 dry | 0 (credit 0.25) | 0 |
| P3 dry window, 4 × 20 | 1 (cap 1) | 1 |
| P3 wet, 4 damage (a tap) | 1 (wet minimum) | 1 |
| P3 wet, then 20 | +2 (credit 3) | +2 |
| P3 wet window, then 40 | stops at 4 | 4 |
| real fist punch into a wet P3 window at queue 1 | victory | victory |
| floor short, 42 ticks in water | 6 | 6 |

**Throughput sketch (solo, TUNE ME watch).** A window is 110 open ticks. At 15 DPS that is about 80 damage:
- P0–P2 dry: 80 / 20 = 4 credits, capped at 3.
- P3 dry: 80 × 0.25 / 20 = 1 credit, capped at 1.
- P3 wet: 80 × 2.5 / 20 = 10 credits, capped at 4.

Kill path for 24 requests:
1. P0 clears 2.
2. P1 needs about 2 windows (22 → 18). P2 needs about 3 windows (18 → 11).
3. P3 needs about 3 wet windows, or 11 dry ones, which the 400-tick stall and the P4 trigger make impossible.

That is roughly 9–10 windows. Each cycle is about 10–15 s of reading and setting, then about 6.5 s of window, so a clean solo clear takes about 4 minutes and a messy one 6–8.

---

## 8. Bossbar and scoring

Two bars, shown to every participant, refreshed every 5 ticks, membership synced every second. One shared queue; co-op players never see per-player bosses.

| Bar | Progress | Colour | Title |
|---|---|---|---|
| Queue | queue / Q0, `NOTCHED_12` (two requests a notch at Q0 = 24) | white boot; blue ghost; **yellow** arming; **green** ACK; **purple** wet ACK; **red** retry storm | `Null Router, the Unacked \| Queue N \| <status>` |
| Request (between windows) | time left until the next retransmit (drains to the stall) | white | `Request 0x10E: ○ ▲ □` (+ the head row in P2+) |
| ACK (during a window) | window time left, SOLIDIFY + OPEN + RELEASE | green dry, purple wet | `ACK window: 1 of 3 requests cleared` / `ACK window, coolant contact: 2 of 4 requests cleared` |

Status strings: *Booting*, *Awaiting configuration*, *Unacknowledged*, *Dual queue*, *Coolant rated*, *Retry storm*, *Pattern settling*, *ACK: chassis solid, throughput 100%*, *ACK, wet: throughput 250%*, *Process terminated*. Titles only change when the text changes. Boss music is on; the screen is not darkened, because the lamps must stay readable.

**Scoring and feedback lines** (action bar): every cleared request (*"Wet ACK: 3 of 4 this window. Queue: 9"*), the quota, dry/wet throughput on the first hit of a window, every console flip with the full pattern and the head, every non-player flip (*"Channel B was flipped to ▲."*), every stall, rotation warning and rotation, and every misroute with the actor's name. `RouterEvents.VICTORY` carries `EncounterStats(ticks, acks, wetAcks, misroutes, stalls, storms, retriesKilled, deaths)`.

---

## 9. Per-phase action catalogs

### How the AI picks

One action slot: IDLE → TELEGRAPH → ACTIVE → RECOVERY → IDLE, and **only while the chassis is a ghost**. An ACK window cancels whatever is running (half its cooldown is kept), except a misroute pulse already in flight, which lands.

After RECOVERY it waits a **global gap** of P1 30, P2/P3 24 or P4 16 ticks ÷ `attackSpeed` (**TUNE ME**). Then it builds a weighted bag from every non-scripted action with phase weight > 0, own cooldown expired and condition met, and draws one. An empty bag retries in 10 ticks. Scripted actions (`boot_chime`, `misroute_pulse`, `retry_storm`, and `request_ping` on each new P0/P1 request) are forced by the vault and skip the bag.

| Action | P0 | P1 | P2 | P3 | P4 | Own cooldown | Condition |
|---|---|---|---|---|---|---|---|
| `boot_chime` | script | | | | | once | P0 tick 60 |
| `request_ping` | script | 3 + script | 3 | 2 | 2 | 300 | a console differs from the head |
| `ghost_lance` | | 6 | 5 | 4 | 4 | 60 | a target exists (prefers line of sight) |
| `packet_burst` | | 3 | 4 | 3 | 4 | 400 / 320 / 360 / 200 | retries alive < phase cap |
| `console_flick` | | | 4 | 3 | 5 | 300 / 260 / 140 | an unattended console; × 2 if one matches the head |
| `ttl_expiry` | | | 3 | 3 | 3 | 240 | a target within 9 of the pad; × 3 if someone has camped within 6.5 of the pad for 40+ ghost ticks |
| `misroute_pulse` | | | script | script | script | none | a misroute |
| `retry_storm` | | | | | script | 600 recast | P4 entry and every 600 ticks in P4 |

**Targets** are participants who are alive, not spectators, not creative.

**Idle and ghost behaviour, every phase.**
- The chassis holds the pad column (feet at +4 with a 0.25 bob as a ghost, +0 docked) and never leaves it. Nothing can push it, and the vault re-sets its position every tick, so there is no leash distance, no void exposure and no pathing.
- It turns toward its target at 8°/tick as a ghost and 2°/tick docked.
- The drive sleds orbit and the cables sway (`idle`); docked it plays `solid` / `solid_warn`.
- It only goes to the pad in a window (docking straight down). The pad is always where the water goes, so there is no special P3 pathing: in P3+ the vents open around the pad instead.

**Interrupt rules.** Every action is cancelled by an ACK window opening (the only interrupt: a correct ack). Wetting a ghost does nothing. Retries' uplinks are interrupted by any hit on the retry (§10).

---

### P0 Boot

Legal actions: `boot_chime` (script, tick 60), `request_ping` (script, with the tutorial request at tick 110 and again every 400 ticks without a flip). The chassis is an immune ghost throughout; hits before the request read *"NAK. Chassis booting. Nothing lands yet."* No retries, no stall, no damage. Exit: the first window closes → P1.

#### Attack: boot_chime

| Field | Content |
|---|---|
| Id | `null_router:boot_chime` (`RouterAction.BOOT_CHIME`) |
| Display name | Boot chime |
| Phases | P0 |
| Unlock condition | scripted by the vault at P0 tick 60 |
| Weight / priority | forced; not in the bag |
| Cooldown | none; once per fight |
| Range / positioning | from the lens over the pad; no facing needed |
| Telegraph | 40 ticks: `chime` animation (status ring spins up, lens swells), `chime.windup` swell; null-beam motes at the lens every 5 ticks |
| Wind-up | 40 ticks; the chassis cannot move (it never does); not cancellable |
| Active | 1 tick: `chime` bell; 60 ack glints at the lens; **green beams from the lens to each of the three consoles and to the three board lamps**; everyone reads *"Request 404. Still serving. Channels A, B, C online."* |
| Recovery | 20 ticks; immune anyway |
| Hit resolution | none: no damage, no push, no flips |
| Projectile / AoE specs | cosmetic beams only |
| Interruptibility | never |
| Counterplay | nothing to survive. It points at the three things that matter (consoles, board) before the first request exists |
| Multiplayer notes | all participants |
| Failure / edge cases | chassis missing at tick 60: skipped (the request at 110 and its ping still teach) |

#### Attack: request_ping

| Field | Content |
|---|---|
| Id | `null_router:request_ping` |
| Display name | Request ping |
| Phases | P0 (script), P1 (script on each new request + bag), P2, P3, P4 |
| Unlock condition | P0: on the tutorial request and every 400 ticks without a flip. P1: on every new request. P1–P4 bag: a console differs from the head |
| Weight / priority | P1 3, P2 3, P3 2, P4 2 |
| Cooldown | 300 own (bag); gap per phase |
| Range / positioning | every console; from each head-row lamp down to its console |
| Telegraph | 20 ticks: `ping` animation (crown tilts, lens blinks twice), `ping` modem chirp; glints at the lens |
| Wind-up | 20 ticks; not cancellable except by a window |
| Active | 30 ticks: every 5 ticks a **null-beam line from the head lamp down to each wrong console**, amber marks on it, `ping.console` beep per wrong console at tick 0; a right console gets green glints. Players within 5 of a wrong console read *"Channel B expects ▲."* |
| Recovery | 10 ticks |
| Hit resolution | none; information only |
| Projectile / AoE specs | per-console beams |
| Interruptibility | an ACK window |
| Counterplay | follow the beams: they mark exactly the consoles still wrong against the **head** (in P2+ this also tells you which row is the head) |
| Multiplayer notes | lines go to everyone near a wrong console; beams are global |
| Failure / edge cases | all consoles right: not chosen (the pattern is already arming) |

Measured: runs on each new P1 request; beams only the wrong consoles.

---

### P1 Single request

Legal actions: `request_ping`, `ghost_lance`, `packet_burst`. Gap 30. One random request; retries (cap 2) walk to a console that matches and flip it. Stall +1 every 600 ticks between windows. Exit: window close at queue ≤ 18 or after the 3rd P1 ack → P2 (≤ 11 → P3); queue ≥ lowest + 3 → P4.

#### Attack: request_ping

Identical to the P0 `request_ping` row, with these overrides: scripted on every new request **and** drawn from the bag at weight 3.

#### Attack: ghost_lance

| Field | Content |
|---|---|
| Id | `null_router:ghost_lance` |
| Display name | Ghost lance |
| Phases | P1, P2, P3, P4 |
| Unlock condition | P1 entry; a target (prefers line of sight from the lens) |
| Weight / priority | P1 6 |
| Cooldown | 60 own; gap 30 |
| Range / positioning | from the lens (+5.8 over the pad) to the target's chest, extended to 32 blocks or the **first solid block**; ghost only (you can be shot while it cannot be hit). Target weights: airborne ×3, standing within 3 of a console ×2, else 1 |
| Telegraph | **30 ticks**: `lance_windup` (leans in, lens contracts to a point, pods swing forward), `lance.charge`; the full line drawn in red marks every 2 ticks, with a 2.5-block gap around every player's eyes so it never blanks a screen; the target reads *"Ghost lance. Get behind a column."* |
| Wind-up | 30 ticks; **aim locks at telegraph start** in P1 (it does not track you) |
| Active | 6 ticks from the fire tick: a cyan null beam along the locked line every tick, `lance.fire` |
| Recovery | 16 ticks (beam afterimage for 3) |
| Hit resolution | `skylore_bosses:null_route` 8 (**TUNE ME**, scales with difficulty), once per player per lance, to anyone within 0.9 of the segment (chest or eye) |
| Projectile / AoE specs | hitscan line, width 0.9, stops at the first block; not stopped by entities |
| Interruptibility | an ACK window (a correct match mid-telegraph cancels it) |
| Counterplay | break line of sight with a rack column, or sidestep: the aim is locked |
| Multiplayer notes | random weighted target; the line hits everyone standing on it; it prefers whoever holds a console, which frees the bucket carrier |
| Failure / edge cases | no one in line of sight: aims at a random target anyway and the line ends on the cover (0 damage). Target dies: fires where locked |

Measured: 8 to a player in the open; 0 behind a rack column.

#### Attack: packet_burst

| Field | Content |
|---|---|
| Id | `null_router:packet_burst` |
| Display name | Packet burst |
| Phases | P1, P2, P3, P4 |
| Unlock condition | P1 entry; retries alive < phase cap (P1 2) |
| Weight / priority | P1 3 |
| Cooldown | 400 own; gap 30 |
| Range / positioning | ejected from the pod ports (`port_l`, `port_r`, `port_b`) |
| Telegraph | 30 ticks: `burst_windup` (pods swing out, ports glow), `burst.windup` servo; packet sparks at the ports every 4 ticks; *"Packet burst. Retries incoming."* |
| Wind-up | 30 ticks |
| Active | **1 retry** at tick 0 (P1), `burst.eject` (`burst.fizzle` if the cap refused it) |
| Recovery | 20 ticks |
| Hit resolution | no damage; spawns `retry_packet` (§10) with 0.35 horizontal velocity in a random direction and 0.3 up |
| Projectile / AoE specs | retries are entities |
| Interruptibility | an ACK window |
| Counterplay | kill the packet (6 HP, one hit) before it reaches a console you already matched |
| Multiplayer notes | not targeted |
| Failure / edge cases | at cap: fizzles |

Measured: exactly 1 retry per burst in P1, never more than 2 alive.

---

### P2 Dual queue

Legal actions: `request_ping`, `ghost_lance`, `packet_burst`, `console_flick`, `ttl_expiry` (+ `misroute_pulse` on misroutes). Gap 24. Head + decoy on two rows; the **head swaps rows every 240 ticks** (40-tick warning, *"Queue head rotating in 2 seconds."*, amber marks at every lamp; held while a pattern settles). Retries cap 3. Stall +1 / 500. Titles *"Dual queue / Two requests on the board. Only the bright row is at the head."* Exit: window close at queue ≤ 11 → P3; queue climb +3 → P4.

#### Attack: request_ping

Identical to the P1 row, with these overrides: weight 3, bag only (no script on new requests), gap after 24. It beams against the **head**, which also reveals the head row.

#### Attack: ghost_lance

Identical to the P1 row, with these overrides:
- weight 5, gap after 24;
- telegraph **24 ticks**;
- **two lances**: lance 1 tracks its target until half the telegraph, then locks and fires at 24; lance 2 (a second target, or the same one if solo) tracks until tick 26 and fires at **tick 36** (stagger 12), so dodging the first into the open is punished;
- active 18 ticks.

#### Attack: packet_burst

Identical to the P1 row, with these overrides: weight 4, cooldown 320, **2 retries** (ticks 0 and 5), cap 3.

#### Attack: console_flick

| Field | Content |
|---|---|
| Id | `null_router:console_flick` |
| Display name | Console flick |
| Phases | P2, P3, P4 |
| Unlock condition | P2 entry; a console with **no player within 2.5 of its stand spot** |
| Weight / priority | P2 4, doubled when an unattended console matches the head |
| Cooldown | 300 own; gap 24 |
| Range / positioning | any console; the chassis turns to face it |
| Telegraph | 30 ticks: `flick` animation (the red antenna points), `flick.windup`; an amber rail line from the lens to the console every 2 ticks; the console's bezel flashes red (`alert`); *"Console flick on channel B. Stand at it to refuse."* |
| Wind-up | 30 ticks |
| Active | 1 tick: if a player now stands within 2.5 of the console, **refused** (`flick.refused`, *"Channel B is attended. Flick refused."*); otherwise packet sparks down the line and the console flips to a glyph that forms neither request |
| Recovery | 10 ticks |
| Hit resolution | no damage; one console flip |
| Projectile / AoE specs | beam to a block |
| Interruptibility | an ACK window; a player arriving at the console |
| Counterplay | stand at your matched consoles (co-op: one holder), or walk to the flashing one during the 1.5 s telegraph |
| Multiplayer notes | targets consoles, not players |
| Failure / edge cases | all consoles attended: not chosen. No console matches: flips a random unattended one (still never into a request) |

Measured: refused when the player walks to the console mid-telegraph; flips it when unattended.

#### Attack: ttl_expiry

| Field | Content |
|---|---|
| Id | `null_router:ttl_expiry` |
| Display name | TTL expiry |
| Phases | P2, P3, P4 |
| Unlock condition | a target within 9 horizontal of the pad centre |
| Weight / priority | 3; × 3 if a player has camped within 6.5 of the pad for 40+ ghost ticks |
| Cooldown | 240 own; gap 24 |
| Range / positioning | a ground ring expanding from the pad centre to r 9.25 |
| Telegraph | 30 ticks: `ttl_windup` (rises 3 px, clamps splay, drives spin), `ttl.windup`; red rings at r 6.5 and r 3.5 every 4 ticks; *"TTL expiry. Jump the ring or leave the pad."* |
| Wind-up | 30 ticks |
| Active | 12 ticks: slam (`ttl`, screen shake within 16); ring r = 1 + 0.75 t in grid arcs, `ttl.pulse` every 3 ticks |
| Recovery | 20 ticks |
| Hit resolution | a **grounded** player within 1.6 vertically, horizontal distance in [r − 0.9, r + 0.3]: null route 6 (**TUNE ME**), push 1.2 out + 0.5 up, once per cast |
| Projectile / AoE specs | expanding ground ring, not blocked by anything |
| Interruptibility | an ACK window |
| Counterplay | jump as it reaches you, or do not camp the pad between windows. It exists to stop players parking under the dock waiting for it to come down |
| Multiplayer notes | everyone the ring meets |
| Failure / edge cases | airborne players skipped |

Measured: 6 to a grounded player 4 blocks out (plus 1 fall damage from the knock-up without slow falling).

#### Attack: misroute_pulse

| Field | Content |
|---|---|
| Id | `null_router:misroute_pulse` |
| Display name | Misroute pulse |
| Phases | P2, P3, P4 (any phase with a decoy; designer command anywhere) |
| Unlock condition | the consoles commit on the decoy (§6); the actor is outside the misroute cooldown |
| Weight / priority | forced by the vault |
| Cooldown | none (the per-player misroute cooldown is 300) |
| Range / positioning | the actor, anywhere in the vault |
| Telegraph | 10 ticks: `misroute` animation (lens snaps, crown jerks); a red beam from the lens to the actor every tick; `misroute.windup` klaxon at the actor |
| Wind-up | 10 ticks; not escapable (it is the consequence, not a dodge check) |
| Active | 1 tick: a null beam, then `routeActor`: alcove → swap with a retry → shock (§6) |
| Recovery | 10 ticks |
| Hit resolution | teleport and a 60-tick gate hold (or a swap, or 4 damage and a shove); title *"Misrouted"* |
| Projectile / AoE specs | beam to one player |
| Interruptibility | never: a window opening under it still lands it |
| Counterplay | read the bright row; the 20-tick settle lets you change your mind; on the way back, the alcove gate is by the door, so reroute past the entry pad |
| Multiplayer notes | only the actor; everyone reads *"Request misrouted by Dev. Queue: 12"* |
| Failure / edge cases | actor left or died: skipped. Chassis missing: the vault routes directly |

Measured: queue +1, player in the far alcove, gate closed then reopened after 60 ticks; a second misroute within 300 ticks throttles instead.

---

### P3 Coolant

Legal actions: `request_ping`, `ghost_lance`, `packet_burst`, `console_flick`, `ttl_expiry`, `misroute_pulse`. Gap 24. Head + decoy, **no rotation**. Every window opens the four coolant vents (§3); dry throughput 0.25 with a quota of 1, wet 2.5 with a quota of 4. Stall +1 / 400. Titles *"Coolant rated / Dry acknowledgements now barely register. The vents open with each ACK."* Exit: 1200 ticks without a wet ACK, or a queue climb of +3 → P4.

#### Attack: request_ping

Identical to the P2 row, with these overrides: weight 2.

#### Attack: ghost_lance

Identical to the P2 row (two staggered lances, 24-tick telegraph), with these overrides: weight 4.

#### Attack: packet_burst

Identical to the P2 row, with these overrides: weight 3, cooldown 360. Retries crossing water short themselves (§10), so a basin runner kills packets for free during a window.

#### Attack: console_flick

Identical to the P2 row, with these overrides: weight 3, cooldown 260.

#### Attack: ttl_expiry

Identical to the P2 row. In P3 the pad is where the water goes, so the anti-camp matters most here.

#### Attack: misroute_pulse

Identical to the P2 row.

#### System: coolant_vent (vault-fired, not an action-slot attack)

| Field | Content |
|---|---|
| Id | `null_router:coolant_vent` |
| Phases | P3, P4 |
| Trigger | every ACK window opening (SOLIDIFY) |
| Effect | the four vents light and each basin at (±3, ±3) gets a water source (below floor level, so it does not spread); `coolant.vent` hiss; coolant mist over the basins every 4 ticks while OPEN |
| End | window close: basins emptied, vents dark, then the whole vault drains |
| Counterplay / intent | a bucket-less team can scoop a basin (bucket from the pack, or crafted) and pour it on the docked chassis; players with their own buckets or Create/Mekanism pipes can bring more. It is the only legitimate water in the room |
| Edge cases | vents re-placed if broken; `/skylorenullrouter coolant on\|off` for testing |

Measured: basins filled and vents lit at window open; both reset at close.

---

### P4 Retry storm

Legal actions: `request_ping`, `ghost_lance`, `packet_burst`, `console_flick`, `ttl_expiry`, `misroute_pulse`, `retry_storm` (script). Gap 16. Head + decoy, **rotation every 160 ticks**. Every 200 ticks the storm flicks a random console (it ignores attendance but never forms a request). Retries cap 6, uplink 20 ticks. The coolant vents still open each window; throughput stays late (0.25 / 2.5). Stall +1 / 300. Titles *"Retry storm / Queue unresolved. Retrying all requests."*, klaxon. Exit: a window that was wet and cleared at least one request → back to P1/P2/P3 by queue (§4).

#### Attack: request_ping

Identical to the P2 row, with these overrides: weight 2.

#### Attack: ghost_lance

Identical to the P1 row, with these overrides:
- weight 4, gap 16;
- telegraph **20 ticks**, tracking until tick 10;
- **three lances fanned at −14°, 0°, +14°** around the target, all firing at tick 20. Sidestepping one line can put you in the next, so use cover.

#### Attack: packet_burst

Identical to the P1 row, with these overrides: weight 4, cooldown 200, **3 retries** (ticks 0, 5, 10), cap 6.

#### Attack: console_flick

Identical to the P2 row, with these overrides: weight 5, cooldown 140.

#### Attack: ttl_expiry

Identical to the P2 row.

#### Attack: misroute_pulse

Identical to the P2 row. Rotation every 160 ticks makes misreads more likely; the settle hold still guarantees fairness.

#### Attack: retry_storm

| Field | Content |
|---|---|
| Id | `null_router:retry_storm` |
| Display name | Retry storm |
| Phases | P4 |
| Unlock condition | P4 entry, then every 600 ticks (**TUNE ME**) in P4 between windows |
| Weight / priority | forced by the vault |
| Cooldown | 600 recast |
| Range / positioning | the four wall ports and all three consoles |
| Telegraph | 40 ticks: `storm_windup` (the rack shudders, drives spin up), `storm.alarm` klaxon; red marks at every port every 5 ticks; **all three consoles flash `alert`**; *"Retry storm. All consoles at risk."* |
| Wind-up | 40 ticks |
| Active | 40 ticks: at 0, 10, 20 and 30 one retry from the next port (`storm.port`, up to cap 6) **and** a storm flip of a random console |
| Recovery | 20 ticks |
| Hit resolution | no damage; up to 4 retries and 4 console flips |
| Projectile / AoE specs | spawns and flips |
| Interruptibility | an ACK window (you matched and committed during the alarm: it stops) |
| Counterplay | commit fast during the 2 s alarm (a window cancels the storm), or clear the ports' packets before they reach the consoles, then wet-ACK out |
| Multiplayer notes | all consoles, all players |
| Failure / edge cases | at cap: fewer packets; flips that would form a request are skipped |

Measured: P3 → P4 on a +3 queue climb; 4 retries from the ports; a wet window returns to P3.

---

### Phase transition matrix

| From → To | Trigger | Timer reset rules |
|---|---|---|
| DORMANT → P0 | player in the trigger box or terminal use; `START_CHECK` not vetoed | queue = Q0; all stats and clocks zeroed; consoles reset; chassis spawned (boot) |
| P0 → P1 | the first ACK window closes | phase clocks reset; the P1 request is issued at close |
| P1 → P2 | window close with queue ≤ ⌈0.75·Q0⌉, or the 3rd P1 ack | phase clocks reset; a decoy is generated; rotation clock 240 |
| P1 → P3 | window close with queue ≤ ⌈0.42·Q0⌉ | as above + `sinceWetAck` = 0 |
| P2 → P3 | window close with queue ≤ ⌈0.42·Q0⌉ | rotation off; `sinceWetAck` = 0, queue reference set |
| P1/P2/P3 → P4 | between windows: queue ≥ lowest queue this phase + 3 (stalls, misroutes) | storm clocks 0; `retry_storm` scripted; storms +1 |
| P3 → P4 | 1200 ticks between windows without a wet ACK | as above |
| P4 → P1/P2/P3 | window close that was wet with ≥ 1 cleared | by queue; the decoy is dropped if the new phase is P1; `storm_weathered` advancement |
| any → DEFEATED | queue reaches 0 inside a window (or `setqueue 0`) | window cleared, vents off, drain, retries time out |
| DEFEATED → CLEARED | 160 ticks | door opens; bars cleared; chunks released |
| CLEARED → DORMANT | `rematch` and 6000 ticks | full reset |
| any fighting → DORMANT | 1200 ticks with no participant, or `/reset` | full reset; `RouterEvents.RESET` |

---

## 10. Retry packets and hazards

### Retry packet (`skylore_bosses:retry_packet`)

| Rule | Spec (**TUNE ME** all) |
|---|---|
| Body | 0.7 × 0.7, 6 HP (config `hp`), step 1.1, walking speed 0.3; an amber data packet in a dark frame with an LED "retry loop" circling it |
| Spawn cadence | `packet_burst` 1 / 2 / 2 / 3 per cast (P1 / P2 / P3 / P4); `retry_storm` up to 4 per cast in P4; caps 2 / 3 / 3 / 6 alive |
| Goal | every 10 ticks: the console that currently **shows the head glyph**, nearest first, 12 blocks of penalty per other retry already on it (they spread out). No matched console, or a window open → **aggro** the nearest non-creative player |
| Uplink | at the console's stand spot (≤ 1.6) it faces the console and uplinks: an amber spark beam every 4 ticks, the console bezel `alert`, `retry.uplink` chirps rising in pitch. After **30 ticks** (20 in P4) the console flips to a glyph that forms neither request. Then it rests 30 ticks and re-targets |
| Aggro | walks to the player (1.15×); within 1.3: 3 damage (mob attack, 20-tick cooldown) and red tint |
| Interruptions | **any hit during an uplink drops it** (the console keeps its glyph, 10-tick stun); the console no longer matching the head, or a window opening, ends it |
| Walled off | no 0.5-block progress for 80 ticks while pathing to a console: it reroutes (hops) to the stand spot with a spark |
| Water | Automaton process: water-sensitive (vanilla drowning damage, 1 per i-frame cycle, in water or rain) |
| Despawn | 1200-tick TTL ("timed out"), vault reset, victory, or its vault no longer fighting. Timeouts are not kills |
| Counterplay | kill the one uplinking to a matched console first (the beam and the red bezel show which), then the rest; stand where a basin runner's water crosses their path |

Measured: a single retry walked from the entry pad to the matched console B and flipped it, and the flip never completed a request; the P1 cap held at 2; retry kills counted.

### Hazard: floor_short (not an attack row: the vault fires it, not the chassis)

| Field | Spec |
|---|---|
| Id | `null_router:floor_short` |
| When | between windows (not in SOLIDIFY/OPEN/RELEASE), every phase including P0 |
| Who | every non-creative participant whose body is in water (`isInWater`) |
| Effect | every 20 ticks: 3 `skylore_bosses:short_circuit` damage (never scales), Slowness II 40 ticks, grid arcs, `short` zap; *"Short circuit. Standing water outside an ACK window."* |
| Paired with | the drain (100 ticks between windows; everything at window close) |
| Counterplay | only bring water to the pad during a window; step out of puddles when the window warns |

### Hazard: clean-room airflow

More than 7 blocks over the floor and not on the ground (non-creative): vertical velocity forced down to −0.9, horizontal halved, elytra stopped. Stops flying above the chassis and out of lance line of sight.

---

## 11. Death, loot, trophy and pack events API

**Win.** The queue reaches 0 inside a window. The vault then:
1. closes the window, closes the vents, drains the vault, and times out every retry;
2. moves to DEFEATED and calls `chassis.shutdown()`. The chassis powers down on the pad over 7 s: the lens goes out, the drives fall, the antennas droop, sparks then smoke. The entity is removed at 140 ticks and drops `loot_table/entities/null_router`;
3. for every participant in the vault: awards `null_router/queue_zero` (and `no_misroute` if nobody was misrouted all fight), gives the trophy **Closed Ticket** (*"Resolved. No requester on file."*), shows *"Queue empty / All requests acknowledged. Nobody was waiting."*, and runs `victoryFunction` if set;
4. fires `RouterEvents.VICTORY(level, origin, participants, stats)`, then `BossEvents.DEFEATED(level, "null_router", origin, participants)`;
5. after 160 ticks: CLEARED, door open. With `rematch`, the vault resets after 6000 ticks.

**Loot** (`data/skylore_bosses/loot_table/entities/null_router.json`, **pack overrides**): 16–32 quartz, 16–32 redstone, 6–12 amethyst shards, 8–16 copper ingots, 2–4 diamonds, 3–6 bottles o' enchanting. Retries drop nothing.

**Advancements** (`skylore_bosses:null_router/...`, all `minecraft:impossible` + code-awarded):

| Id | Title | When |
|---|---|---|
| `enter_vault` | Ticket Opened | the fight starts (root) |
| `first_ack` | ACK | the actor who opens the first window |
| `misrouted` (hidden) | Please Hold | every participant, first misroute |
| `wet_ack` | Not Rated for Immersion | a wet window clears ≥ 1 |
| `storm_weathered` | Retry Budget Exceeded | a wet window ends P4 |
| `queue_zero` | Queue Empty | the clear (challenge) |
| `no_misroute` (hidden) | Zero Packet Loss | the clear without any misroute (challenge) |

**Events** (`net.teamaof.skylorebosses.bosses.nullrouter.api.RouterEvents`, Architectury events, logical server):

| Event | Signature | Use |
|---|---|---|
| `START_CHECK` | `(level, origin, trigger) → EventResult` | veto the start: the `ae2_cpu` gate |
| `ENCOUNTER_STARTED` | `(level, origin, players, queue)` | quest "enter" task |
| `PHASE_CHANGED` | `(level, origin, from, to)` | music, quest hints |
| `REQUEST_ISSUED` | `(level, origin, serial, head[3], decoy[3] or null)` | accessibility mods, logging |
| `ACK_OPENED` | `(level, origin, actor or null, queue)` | |
| `ACK_CLOSED` | `(level, origin, cleared, wet, queue)` | |
| `MISROUTED` | `(level, origin, actor, how, queue)`, how = alcove / swap / shock / cooldown | |
| `QUEUE_CHANGED` | `(level, origin, from, to, cause)`, cause = ack / misroute / stall / command | |
| `VICTORY` | `(level, origin, participants, EncounterStats)` | the stage grant, flavour rewards |
| `RESET` | `(level, origin, reason)`, reason = abandoned / command / rematch / created / removed / rebuild | |

Plus the boss-agnostic `BossEvents.STARTED` / `DEFEATED` with boss id `null_router`. **No new stage id is invented**: the mod never names `automaton_network`.

---

## 12. Quest, stage and chapter wiring

| Hook | Owner | Wiring |
|---|---|---|
| Chapter `modular_expression` | pack (QuestQueen) | the vault quest lives here; its "enter" task listens to `ENCOUNTER_STARTED` or the `enter_vault` advancement, its "clear" task to `queue_zero` |
| Gate `ae2_cpu` | pack (Progressive Stages + KubeJS) | `RouterEvents.START_CHECK`: return `interruptFalse()` unless the trigger player has `ae2_cpu`. The terminal then reads *"Ticket refused. Your access level is pending."* The consoles and fight need **no AE2 blocks**: AE2 is the theme and the gate, never a runtime dependency |
| Stage `automaton_network` (opens Act V) | pack | grant on `BossEvents.DEFEATED` with boss id `null_router` to every participant (or run it from `victoryFunction`). The kill consumes the `ae2_cpu` gate's purpose and grants the **existing** door stage; Act V is not forked |
| Soft flavour `teknari_cybernetics` | pack | optional: on `VICTORY`, participants with the Teknari cybernetics stage get a Cyberware lore item or quest line. The mod exposes the stats (e.g. `wetAcks`, `misroutes`) to key flavour off |
| Loot, trophy uses | pack | override the loot table; the Closed Ticket may be a quest submission item |

KubeJS sketch (pack side):

```js
// server_scripts/null_router.js
const RouterEvents = Java.loadClass('net.teamaof.skylorebosses.bosses.nullrouter.api.RouterEvents')
const BossEvents = Java.loadClass('net.teamaof.skylorebosses.core.api.BossEvents')
const EventResult = Java.loadClass('dev.architectury.event.EventResult')
RouterEvents.START_CHECK.register((level, origin, player) =>
  player.stages.has('ae2_cpu') ? EventResult.pass() : EventResult.interruptFalse())
BossEvents.DEFEATED.register((level, bossId, origin, players) => {
  if (bossId === 'null_router') players.forEach(p => p.stages.add('automaton_network'))
})
```

---

## 13. Java implementation outline

**Tick cadence** (`Vaults.tickLevel` on `SERVER_LEVEL_POST`, every vault with a loaded origin or a fight in progress):

| Every | Work |
|---|---|
| 1 tick | phase tick; fall-zone rescue; participants; chassis presence (respawn after 40 missing); console alert decay; arming countdown → commit; ACK stage machine + chassis water check; between windows: stall, P4 triggers, rotation, storm clocks; camp tracking; console block states; alcove gates |
| 5 ticks | boss bars; airflow |
| 10 ticks | docking bay clear; DORMANT trigger check |
| 20 ticks | floor short; water scan/drain; structure self-repair; bar membership |
| 40 ticks | lamp re-sync (and whenever the display is dirty) |

The chassis ticks itself: position from the vault each tick, facing, the action slot (ghost only), and mode sync with the vault's ACK stage (a reloaded chassis snaps to the right mode). Retries re-think every 10 ticks.

**Networking.** None custom. Synced entity data plus block states plus vanilla boss bars, titles and particles (§2).

**Save / reload.** `Vault.save/load` persists everything in §5's first row, including the ACK stage, its ticks and credit, per-player misroute cooldowns, gate timers and the designer hold. The chassis and retries persist as entities bound by origin. The vault re-applies console and lamp states every tick / 40 ticks, and the chassis mode every tick. Measured: leaving and reopening the world **mid-window** kept phase, queue, head, decoy and consoles, and the window resumed.

**Designer commands** (`/skylorenullrouter` = `/skylorebosses null_router`, op 2):

| Command | Effect |
|---|---|
| `build [pos]`, `register [pos]`, `unregister` | test vault / adopt a structure / forget |
| `start`, `reset` | start (START_CHECK applies with a player source), full reset |
| `skipphase` | P0 → P1 (issue request), P1 → P2 and P2 → P3 (queue to the threshold), P3 → P4, P4 → by queue |
| `setqueue <n>` | set the queue (0 = victory) |
| `forceack` | open a window now (consoles set to the head) |
| `solve`, `decoy` | set the consoles to the head / decoy as a player flip (arms, then commits) |
| `setconsole <0..2> <0..2>` | one console as a player flip |
| `misroute [player]` | misroute a player |
| `setwet <ticks>` | make the chassis wet (counts in a window) |
| `spawnretries <n>` | retries at the wall ports (ignores the cap) |
| `coolant <true\|false>` | vents on / off |
| `attack <id>` | force a chassis action (ghost only; `misroute_pulse` targets you) |
| `hold <true\|false>` | freeze the attack bag and the pressure clocks; forced actions still run |
| `status`, `tp` | one-line state dump / entry pad |

### Verification

`tools/testing/t_null_router.py` (Marionette over HTTP; 100 checks, passing twice in a row) builds the vault in a fresh void world and covers:

- **Build and start:** build, the dormant state, the terminal start and readmit, lockdown.
- **P0 tutorial:** boot immunity, the tutorial shape, lamps and board, the ghost NAK from real melee.
- **Consoles and the window:** consoles cycled by real right-clicks, arming, commit, locked consoles, the docked chassis, P0 quota, P0 → P1, request distance.
- **P1:** lance damage in the open and behind cover, ping, burst count and cap, a retry walking to and flipping a matched console, retry kills, stall growth, three acks → P2.
- **P2:** decoy distance and dim lamps, head rotation and lamp swap; misroute into the alcove with the gate, re-roll, reopening and the cooldown; flick refusal by walking to the console and flick landing; the TTL ring for 6.
- **P3 throughput:** P2 → P3 by queue, vents open and close, dry 0 / 1 / cap, wet minimum, wet ×2.5, wet cap 4, the drain at window close.
- **Hazards:** floor short 6 over 42 ticks and its drain, airflow, the docking bay guard.
- **P4:** P4 by queue climb, storm spawns, the wet exit, P4 by the no-wet clock.
- **Persistence and endings:** reload mid-window, the void rescue, a real punch finishing a wet window, the trophy and advancements, cleared and door open, the chassis removed, no retries, the cleared console line, the abandon reset.

---

## 14. Assets list

| Asset | Path | Notes |
|---|---|---|
| Chassis model | `geo/entity/null_router/null_router.geo.json` (144 cubes, 27 bones, 8 locators) | rack core, white front panel, octagonal cyan lens + 12-LED status ring, three-glyph ticket screen, two port pods with cable chains, rear fins, crown with ticket display and two antennas, four orbiting drive sleds, hover skirt with 8 thrusters, bottom port, four docking clamps (extended at rest) |
| Chassis animations | `animations/entity/null_router/null_router.animation.json` (20) | loops `idle` (ghost), `solid`, `solid_warn`; one-shots `dock`, `release`, `boot`, `chime`, `ping`, `lance_windup`, `lance`, `burst_windup`, `burst`, `flick`, `ttl_windup`, `ttl`, `misroute`, `storm_windup`, `storm`, `hurt`, `death` (7 s hold) |
| Retry model | `retry_packet.geo.json` (22 cubes) + `idle`, `move`, `uplink` | |
| Entity textures | 256² atlas + glowmask per model from the `automaton` palette with Codex swatches (chassis, chassis_dark, panel, fluix, cable, cable_glow, lens, three LED colours, screen, fins, copper, hazard, rubber, packet) | |
| Block textures | `textures/block/null_router/` | Codex: vault floor/wall/ceiling/light, pillar side/top, pad top/side, coolant vent, routing gate (cutout), vault grate (cutout), terminal front/side, console side/top, three lamp glyphs. Derived in export: 12 console faces (glyph × status bezel), 3 dim lamps, the dark lamp, the lit vent |
| Item | `closed_ticket` (Codex icon), two spawn eggs | |
| Particles | `packet_spark`, `coolant_mist`, `ack_glint`, `null_beam` (shared library) | |
| Sounds | 48 synthesized OGGs under `sounds/null_router/` | e.g. `idle.hum`, `boot`, `request` (modem), `ack` (bell), `ack.warn`, `stall`, `lance.charge`/`fire`, `coolant.vent`/`hiss`, `short`, `misroute`, `storm.alarm` (klaxon), `death` (power-down servo), `retry.*` |
| Lang | 161 keys in `en_us.json` | blocks, items, entities, death messages, bars, statuses, titles, log lines, console/terminal lines, warnings, advancements, subtitles |
| Data | advancements (7), damage types `short_circuit`, `null_route`, loot tables | |
| Renders | `bosses/null_router/models/null_router/renders/` | front34, side, six posed renders, a Blockbench sheet, an in-game sheet |

**Placeholder-first path, ghost vs solid (the critical read).** The renderer alone carries the read, with no second model. A ghost is a translucent cyan hologram with a shimmer and scanline dropouts, the glowmask (lens, LEDs, cables) stays bright, clamps are folded and it hovers. A solid chassis is opaque and docked, clamps are planted and pods are out. On top of that the bar colour changes, the console bezels go white, green beams run from the consoles, and a bell rings. The in-game sheet shows both.

Rebuild: see [README.md](README.md).

---

## 15. Balance sheet

Every value below is **TUNE ME** and lives in `[null_router]` of `skylore_bosses-server.toml`.

| Section | Key | Default | Meaning |
|---|---|---|---|
| queue | `queueBase` / `queuePerPlayer` / `queueCap` | 24 / 4 / 40 | start depth |
| queue | `damagePerRequest` | 20 | raw damage per request at ×1 |
| queue | `dryMultEarly` / `wetMultEarly` | 1.0 / 1.5 | P0–P2 |
| queue | `dryMultLate` / `wetMultLate` | 0.25 / 2.5 | P3–P4 |
| queue | `ackCapP0` / `ackCapEarly` / `ackCapWetLate` / `ackCapDryLate` | 2 / 3 / 4 / 1 | per-window quota |
| queue | `wetMinimum` | 1 | a wet hit always clears this many |
| queue | `attackSpeed` | 1.0 | scales gaps and cooldowns |
| ack | `armTicks` | 20 | settle before commit |
| ack | `solidifyTicks` / `openTicks` / `warnTicks` / `releaseTicks` | 10 / 110 / 30 / 10 | the window |
| ack | `wetTicks` | 40 | wet memory |
| ack | `consoleCooldownTicks` | 4 | per-console flip cooldown |
| misroute | `queuePenalty` / `holdTicks` / `cooldownTicks` | 1 / 60 / 300 | |
| phases | `p2QueueFraction` / `p2AfterAcks` / `p3QueueFraction` | 0.75 / 3 / 0.42 | |
| phases | `stallP1..P4` | 600 / 500 / 400 / 300 | retransmit clock |
| phases | `p4NoWetAckTicks` / `p4QueueRise` | 1200 / 3 | storm triggers |
| phases | `alternateP2` / `alternateP4` / `alternateWarn` | 240 / 160 / 40 | head rotation |
| phases | `stormRecastTicks` / `stormFlickTicks` | 600 / 200 | |
| retries | `hp` / `damage` / `flipTicks` / `flipTicksStorm` / `lifeTicks` | 6 / 3 / 30 / 20 / 1200 | |
| retries | `capP1..P4` | 2 / 3 / 3 / 6 | |
| hazards | `shortDamage` / `shortSlowTicks` / `drainTicks` / `flightCeiling` | 3 / 40 / 100 / 7 | |
| attacks | `lanceDamage` / `ttlDamage` / `misrouteDamage` | 8 / 6 / 4 | |
| vault | `dormantTimeoutTicks` / `rematch` / `rematchDelayTicks` / `victoryFunction` | 1200 / true / 6000 / "" | |

Code-level constants (change in `RouterAction` / `NullRouterEntity`, also TUNE ME):
- action telegraphs, actives, recoveries, cooldowns and weights (§9);
- lance count, stagger 12, fan 14°, hit radius 0.9, range 32;
- TTL ring speed 0.75/tick, band −0.9/+0.3, push 1.2/0.5;
- flick attend radius 2.5;
- gaps 30 / 24 / 24 / 16;
- hover 4, bob 0.25.

### Playtest watchlist

- **Solo console running.** Three consoles 20 blocks apart, plus bucket runs in P3. Watch the P3 stall (400 ticks) against how long a solo player needs to set three consoles, fetch water and pour it. If solos hit P4 constantly, raise `stallP3` or `armTicks` before touching damage.
- **Co-op split.** One holder, one bucket. Check `console_flick` refusal doesn't make a holder too strong in P2 (flick weight 4). Check the lance's ×2 weight on console holders isn't punishing the holder role out of existence.
- **Wet timing.** Can a player scoop a basin and pour it within the 110-tick window? Measure it. If not, shorten the basin-to-pad distance (±3 now) or raise `openTicks`.
- **Retry flip rate.** 30-tick uplinks against a one-hit kill: in P4 (cap 6, 20 ticks) this can outpace a solo player. Watch `retriesKilled` against `stalls` in `EncounterStats`.
- **Misroute frustration.** Watch the misroute count per clear. More than 2 per fight means the rows read badly: brighten the head, dim the decoy further, or shorten the P2 rotation warning's wording.
- **Throughput.** 20 damage per request assumes Act IV weapons of roughly 12–20 DPS. Iron's casters and Create saws may shred windows, but quotas cap them, so watch clear time, not burst.
- **Flood behaviour.** Pumps (Create, Mekanism) against the 100-tick drain, and the player short at 3 per second.

---

## 16. Canon and text rules checklist

- [x] Player-facing name **Null Router**, epithet **the Unacked** (bar: *"Null Router, the Unacked"*; title *"Null Router / the Unacked. Still serving. Please hold."*). Never Citadel Keeper, Harbinger, "network warden" or a generic Warden/Guardian.
- [x] Automaton voice: dry systems language and polite error strings (*"Request retransmitted... Thank you for your patience."*, *"We apologise for nothing."*, *"Window quota reached. Further damage is logged and ignored."*). The sass is aimed at the abandoned bureaucracy, never at the player.
- [x] The Automaton are emancipated AI replicants, weak to water contact. The chassis and retries are water-sensitive; the player's rig shorts in standing water outside a window.
- [x] The verb is **acknowledge / configure**. Nowhere does the text say "break the channels": consoles are set, and they cannot be broken.
- [x] Water is a **timed steroid and a self-short**. No line suggests flooding the room; the only water the vault gives you is timed.
- [x] No Calyx, infection, parasite, "the path infects you" or Act V ending text. No infection clock.
- [x] The stage door stays `automaton_network`; the mod names no stage at all.
- [x] Writer constraints for the pack's quest text: call it a vault, not a lair; a queue, not HP; requests and tickets, not "attacks"; the guild "left", never "died". The router is not evil: it is still serving.

---

## 17. Open risks

| Risk | Mitigation in code | Remaining |
|---|---|---|
| **Water-mod cheese (eternal flood)** | water between windows shorts players (3 per 20 ticks) and is drained after 100 ticks; everything drains at window close; water on a ghost does nothing | pumps can re-place faster than the scan; the short still punishes standing in it. Watch playtests |
| **Console griefing** | unbreakable, explosion- and piston-proof; restored if removed; stand spots and the dock column refuse placement; walled-off retries reroute | creative-mode players can still break them (restored within 20 ticks) |
| **Teleport into void** | alcove spots are validated (air, air, solid floor, no one inside); fallbacks swap with a retry (inside the vault) or shock in place; fall-zone rescue | a structure that deletes an alcove falls back to swap/shock, by design |
| **Chunk unload** | chunks force-loaded for the fight; console, lamp and chassis state come from SavedData and are re-applied | a server crash mid-fight resumes from the last save (measured: mid-window reload resumes) |
| **Multiplayer desync of patterns** | no client prediction: the pattern is block states and a server-built bar title; the vault is authoritative on every flip | none known |
| **Flight above the chassis** | airflow above +7; the ceiling at +12; the lance prefers airborne targets ×3 | creative flight is exempt (designers) |
| **Structure ownership (Skylore Islands)** | test vault + `register` adoption; contract in §3 | needs the Islands agent's structure id before final island terrain; an automatic registration hook on structure placement is **not built** yet |
| Misroute swap into a bad retry spot | retries only exist inside the vault volume | a retry mid-air could drop a swapped player a block |
| Balance of co-op queue scaling | +4 requests per player | untested beyond solo in Marionette; needs a real co-op playtest |

---

## Implementation order

1. Void-box controller + three consoles + request display + queue bossbar (`Vault`, `VaultLayout`, `VaultBuilder`, `ChannelConsoleBlock`, `RequestLampBlock`, `RouterBossBar`). **Done.**
2. Ghost chassis + ack solidify + one attack (`ghost_lance`) with full telegraph (`NullRouterEntity` modes, `NullRouterRenderer`). **Done.**
3. Match / mismatch + misroute alcove + P0–P1 (arming, commit, alcove gates, cooldown). **Done.**
4. Retry packet AI (flip consoles) (`RetryPacketEntity`). **Done.**
5. Wet rules: floor short vs chassis multiplier + P3 coolant (throughput table, basins, drain). **Done.**
6. P2 pattern alternation + P4 retry storm. **Done.**
7. Arena template / structure hook + lockdown (test vault, `register`, grate, force-load). **Done**; automatic structure hook pending the Islands id.
8. Pack API, advancements, `automaton_network` wiring (`RouterEvents`, advancements; pack scripts in §12). **Done** (mod side).
9. Art / sound pass: the ghost vs solid read (Blockbench model and 20 animations, Codex textures, synthesized sounds, renders). **Done** as a first pass; a hand-painted pass on the chassis atlas is optional.
