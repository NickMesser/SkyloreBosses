# Matris Calyx, the Parasite Mother — Design Package

Skylore Act V finale (Path of the Earth Spirit). Ships as its own NeoForge 1.21.1 mod.
Every number marked **TUNE** is a starting value to adjust in playtests. Asset ids use the mod namespace `matris_calyx`.

> *"The islands were never a place. They were a stomach."*

---

## 1. One-page fight bible

**Fantasy.** In Proto-World the player finds out that the ground, the walls and the sky-ribs belong to one animal. The Parasite, Immortuos Calyx, has farmed Atmos all along. Matris is its mother-body. The player never fights "a big mob". They fly across her.

**Player fantasy.** A pilot-surgeon. You read tells from the air, land on a limb's island during its three-second opening, cut, and take off again before her body reacts. Act I medicine (syringes, cures) is part of your loadout, and you can run out.

**Win condition.** Kill all six rooted appendages. That drains her bossbar from 600 to 0. The heart splits and the **Calyx Bloom** rises. Burn the Bloom down while using her own terrain as cover from its laser. Victory hooks fire, followed by the ending text "Savior of Atmos".

**Fail states.**
- Player death. This is a normal respawn at the Proto-World anchor. The fight keeps going and the other players keep their progress.
- The **infection clock** hits 100 because the player ran out of cures. This does not kill outright: they are "Taken", knocked back to the anchor with a debuff. See §8.
- Whole-party wipe with nobody in the dimension for 5 min (**TUNE**). The encounter goes dormant. Arms keep their HP but regrow their armour plates. The bossbar notches stay: kills are permanent.

**Tone.** Awe first, then dread. Everything is wet, slow and enormous. She is mostly indifferent to you until she notices you (Retina Flash). No jump scares. The horror is scale and the farm reveal.

**Canon anchors.** Infection is an accident or ambient spread and is always curable. The path is a stance. The Parasite is one creature. See §16.

---

## 2. Mod architecture

| Item | Value |
|---|---|
| Mod id | `matris_calyx` (display name "Skylore: Matris Calyx") |
| Package | `net.teamaof.matriscalyx` |
| Loader | NeoForge 21.1.x, MC 1.21.1, Java 21 |
| Hard deps | NeoForge, GeckoLib 4.x (renderer for the Blockbench models in this folder) |
| Soft deps | `immortuos_calyx` (infection), `pehkui` (not used — see §17), `progressivestages`, `questqueen` (both only through events/datapack), `immersive_aircraft` (flight tuning tag) |
| License | MIT (code) + ARR-free CC-BY 4.0 (assets) — redistributable |

```
net.teamaof.matriscalyx
├─ MatrisCalyx.java                 // @Mod entry, registries, config
├─ registry/  (MCEntities, MCBlocks, MCBlockEntities, MCItems, MCParticles, MCSounds, MCAttachments, MCDataComponents)
├─ encounter/
│   ├─ MatrisEncounter.java          // the fight controller (SavedData-backed state machine)
│   ├─ EncounterState.java           // DORMANT, AWAKENING, LIMBS, HEART_SPLIT, BLOOM, VICTORY, RESETTING
│   ├─ EncounterSavedData.java       // per-level persistence
│   ├─ ArmSlot.java                  // one of six: type, anchor pos, island bounds, alive, hp snapshot
│   ├─ bodyattack/  (BodyAttack, Peristalsis, BileRain, RetinaFlash, RootGrip, Swallow, BodyAttackScheduler)
│   ├─ infection/  (InfectionClock, InfectionBridge, NoopInfectionBridge, ImmortuosInfectionBridge)
│   └─ bossbar/   (MatrisBossBar, notch math)
├─ entity/
│   ├─ arm/  (AbstractRootedArm, GraspingArm, SlamArm, ChargingArm, MouthArm, SpittingArm, NerveArm, ArmWindowController, RootLeashGoal)
│   ├─ bloom/ (CalyxBloom, BloomLaser, BloomEyeTracker)
│   ├─ projectile/ (BileGlob)
│   └─ add/  (SporeThrall, SporeMite, InfectedDrifter)
├─ block/ (SporeVentBlock + SporeVentBlockEntity, HeartCoreBlock, FleshBlock variants, EncounterControllerBlock)
├─ world/ (ProtoWorldDimension keys, ArenaPlacer, ArenaTemplate)
├─ net/  (S2CEncounterSync, S2CBodyAttackTelegraph, S2CInfectionSync, S2CCameraShake)
├─ api/   // stable public surface for the pack (§12)
│   ├─ MatrisEvents.java             // NeoForge events on the GAME bus
│   └─ MatrisApi.java                // read-only queries
├─ client/ (renderers per entity via GeckoLib, BodyAttackOverlay, InfectionHud, BossBarRenderer, ScreenShake)
├─ command/ (SkyloreCalyxCommand)
└─ data/  (datagen: lang, loot, advancements, tags, sounds.json, particle descriptions)
```

**Server owns** encounter state, arm AI, window timing, damage gating, body-attack scheduling, infection, vents, bossbar values.
**Client owns** GeckoLib animation playback (the server syncs triggerable animation keys), Snowstorm-style particles, screen overlays (Retina whiteout, bile vignette), camera shake, and HUD for the infection clock.

**Config** (`matris_calyx-server.toml`): every **TUNE** value, `scaleWithPlayers`, `windowSeconds`, `windowPeriodSeconds`, `infectionEnabled`, `dormantTimeoutSeconds`, `allowElytra`, `arenaSource = MOD | DATAPACK`.

**Datapack hooks the pack still owns:**
- `data/matris_calyx/matris_calyx/arena.json` for island offsets, arm type per slot and vent positions (§3).
- Tags: `matris_calyx:cures` (items that reduce the clock), `matris_calyx:flight_vehicles`, `matris_calyx:doctor_tools`.
- Loot tables: `matris_calyx:entities/<arm>`, `matris_calyx:gameplay/bloom_reward`.
- Advancements (the pack re-parents them into its chapter).

---

## 3. Arena / Proto-World layout

**Dimension ownership decision.** The mod ships the dimension as `matris_calyx:proto_world` with a void generator plus an authored arena template. The Proto-World is *her body* and only exists for this fight, so the mod owning it keeps the encounter self-contained and testable. Skylore's Tempad route points at the mod's dimension key.
**If Skylore Islands already owns `skylore:proto_world`:** set `arenaSource = DATAPACK` and `targetDimension = "skylore:proto_world"`. The mod then only places the arena template into that level (via `ArenaPlacer` at a fixed origin) and never registers its own dimension. **This needs sign-off from the Skylore Islands / worldgen owner before final terrain is authored.**

**Layout** (origin = heart centre at Y 120):

| Island | Offset (x, y, z) | Radius | Contents |
|---|---|---|---|
| Heart | 0, 0, 0 | 40 | Heart core (`HeartCoreBlock`), later the Bloom; ring of 6 vents; anchor platform (respawn) on its south rim |
| Satellite N — Nerve | 0, +18, −120 | 22 | Nerve Arm; closest to the entry, landmarked by a blue glow |
| Satellite NE — Grasping | 104, +30, −60 | 20 | Grasping Arm; tallest, sits above the flight lanes |
| Satellite SE — Spitting | 104, −10, 60 | 22 | Spitting Arm; bile pools on the island |
| Satellite S — Slam | 0, −24, 120 | 26 | Slam Arm; widest flat island |
| Satellite SW — Charging | −104, 0, 60 | 30 × 14 lane | Charging Arm; long ridge island (charge lane) |
| Satellite NW — Mouth | −104, +12, −60 | 20 | Mouth Arm; ribcage arch overhead |

- **Traversal.** No bridges. Every island is 80+ blocks from its neighbours through open void. Aircraft, balloons and elytra are all valid. The 120-block radius takes a biplane about 6–8 s one way (**TUNE** with Immersive Aircraft speeds).
- **Cover.** Each satellite has 2–3 rib-bone arches and a flesh overhang. The heart island has six "lobes" (the Bloom's split heart) plus rib pillars, and these are the laser cover in Phase 3. Cover is made of unbreakable `matris_calyx:hardened_flesh`.
- **Landmarking.** Each arm island has a coloured glow vein (nerve = cyan, grasping = pink, spitting = bile green, slam = bone white, charging = rust, mouth = red) that runs back to the heart along the underside. You can navigate by following the veins.
- **Her body.** A shell of flesh blocks (a thin lattice, not solid) forms a 300-block "stomach" dome at radius about 200. It has ribs, a floor of digestive membrane 90 blocks below the heart (the void floor: falling in = 6 hearts damage and a teleport to the anchor, not instant death — **TUNE**), and a ceiling with dangling villi.
- **Distant Horizons.** The dome ribs are 5–7 blocks thick and the silhouette is authored as a ribcage. The heart has a tall spire of vein columns so it reads from LOD distance. Avoid thin one-block details at the outer shell because DH culls them.
- **Fallback (dimension rejected entirely).** `ArenaPlacer` can stamp the same template into a sealed End-style arena: a void level with fixed time and the same seven islands, no dome. The fight works unchanged. Only the "body is terrain" read gets weaker.

---

## 4. Phase flowchart

```
[DORMANT] --player enters proto_world AND steps on anchor (or EncounterControllerBlock powered)-->
[AWAKENING] 12 s: dome shudders, arms play `emerge` one by one, bossbar fades in 0→600
     |
     v
[LIMBS]  ── body attack every 25–40 s (TUNE), infection ticks, vents spawn adds
   each arm: closed ──(window timer ~15 s)──► window_warn 0.8 s ──► OPEN 3 s ──► closed
   arm killed → bossbar −100, `death` anim, island vein goes dark
   Nerve alive: other arms +buffs (§6); Nerve dead: buffs stripped, "she flinches" event
   at 300/600 (3 arms dead): ESCALATION: body attack cadence ×1.3, Swallow unlocked
     |
     | all six dead (bossbar = 0)
     v
[HEART_SPLIT] 8 s cinematic-lite: all players get Slow Falling 10 s; CalyxBloom spawns and plays `emerge`
     |    (lobes peel, stalk rises, eye opens); bossbar retitled "Calyx Bloom", refilled to Bloom HP
     v
[BLOOM]  loop: aim 1.5 s → laser_charge 2 s → laser_fire 3 s sweep → rest 4 s (vulnerable ×1.5 dmg)
   below 50% HP: Retina Flash every 3rd cycle; vents re-open (2 of 6)
   below 20% HP: double sweep, rest shortened to 3 s
     | Bloom HP 0
     v
[VICTORY] `death` 8 s → MatrisEvents.Victory fired → loot → dome "exhales" → players returned via exit portal
```

Timers (all **TUNE**): window period 15 s ±2 s jitter (desynchronised per arm); window 3 s; window warn 0.8 s; body attack 25–40 s; Bloom cycle ≈ 10.5 s.

---

## 5. Entity / component design

| Component | Kind | Why |
|---|---|---|
| `MatrisEncounter` | Pure system: `SavedData` + level tick hook | Must survive chunk unload and relogs. Owns the truth. |
| `EncounterControllerBlock` | Block + BlockEntity at heart centre | Physical anchor. Force-loads the arena chunks (ticket) while the fight is active. Lets designers start the fight with redstone. |
| Six arms | `Mob` subclasses of `AbstractRootedArm`, `persistenceRequired`, no natural spawns | Need AI goals, hitboxes, GeckoLib animation, damage events. |
| `CalyxBloom` | `Mob`, NoAI movement, custom look control | Stationary turret. The eye bone tracks its target. |
| `BloomLaser` | Pure system in `CalyxBloom` (raycast each tick) + client beam renderer | A projectile entity would desync at 60+ block range. |
| Spore vents | `SporeVentBlock` + BlockEntity (GeckoLib block model `spore_vent`) | Terrain objects you can break. Placed by the template. |
| `BileGlob` | `ThrowableProjectile` | Spitting volleys and Bile Rain. |
| Adds | `SporeThrall`, `SporeMite`, `InfectedDrifter` (Mob) | §9 |
| Body attacks | Pure systems (`BodyAttack` impls) + S2C telegraph packets | Dimension-wide events, not entities. |
| Bossbar | `ServerBossEvent` subclass owned by the encounter | Tracks Matris (six arms), not one hitbox. |

**Hitboxes.** Each arm has one main hitbox (e.g. 3×9×3 for Grasping, scaled by the renderer factor below). While the window is open, damage only counts when it lands in the **core zone**: a `PartEntity` child at the `core` locator (1.6×1.6×1.6). Hits elsewhere play a "thunk" sound and deal 0.

**Scale.** The models are authored at 1 px = 1/16 block. The renderer applies `EntityRenderer#scale` per type (below). There is no Pehkui dependency. Hitboxes are set in `EntityType.Builder.sized()`.

---

## 6. Per-appendage cards

Shared rules: all arms are rooted (they never leave their island), have 100 "bossbar value" each, take damage only while OPEN and only at the core, and have knockback resistance 1.0. `RootLeashGoal` snaps any arm back if it is pushed more than 3 blocks from its anchor. The Charging Arm is the exception: it is leashed to its lane AABB instead.
Stats below assume one player. Multiplayer HP scaling: ×(1 + 0.5 × (players−1)), capped at ×3 (**TUNE**).

### Nerve Arm — `matris_calyx:nerve_arm` (model `nerve_arm/`)
| | |
|---|---|
| Role | No direct damage. Buffs the other five. Teaches kill order. |
| Render scale | 2.5 (≈ 10 blocks tall) |
| HP / armor | 120 HP (**TUNE**) / 0 (gated anyway) |
| Behaviour | `pulse` every 5 s (**TUNE**): other living arms get **Window −0.5 s** (shorter opening), **+20% damage**, **+1 armour-regrow**. A cyan nerve line flashes from its island to each buffed arm (a particle line along the underside veins). |
| Telegraph | Ganglion glow + `nerve_pulse` ring 0.6 s before the buff applies |
| Open window | Every 12 s (it opens more often: this is the tutorial arm). Window 3.5 s. |
| Death effect | All buffs removed immediately. Every other arm plays `hurt` and its window cadence gets 20% faster for the rest of the fight. Subtitle: "She flinches." |

### Grasping Arm — `grasping_arm/`
| | |
|---|---|
| Role | Punishes hovering and slow flight near its island |
| Scale / HP | 3.0 (≈ 13 blocks) / 150 HP |
| Goals | `GraspTargetGoal`: targets the closest flyer within 28 blocks that has hovered (speed < 0.3 b/t) for 1.5 s. `telegraph` 1.2 s → `grab` (hit sweep: capsule along the tentacle) → on hit, mount the victim on the claw (`grip` locator) for 2 s → `yank` pulls them onto the island and dismounts. Aircraft: ejects the pilot, leaves the vehicle floating. |
| Telegraph | Rears back, fingers splay, hiss, mucus strings |
| Open window | Also opens right after a missed grab (a punish window) |
| Counterplay | Keep moving, or bait the grab from high up and dive in when it misses |
| Death | Tentacle whips and collapses along the island (death anim), chunks + blood |

### Slam Arm — `slam_arm/`
| | |
|---|---|
| Role | Area denial on its island. Slow and huge. |
| Scale / HP | 3.0 / 180 HP |
| Goals | `SlamGoal`: if a player is on its island, `telegraph` (2.2 s lift + shake), then `slam` hitting a 7-block radius circle in front (marked on the ground by a red spore ring during the wind-up): 14 dmg + launch. The fist stays in the ground for 1 s. |
| Open window | Guaranteed window 0.3 s after every slam (fist stuck), on top of the periodic timer |
| Counterplay | Stay outside the ring, then run in during the stuck window |
| Death | Topples forward, the fist cracks off the island, root dust |

### Charging Arm — `charging_arm/`
| | |
|---|---|
| Role | Keeps you off its ridge island. A lane-runner. |
| Scale / HP | 2.5 / 150 HP. Leashed to its lane (umbilical cord visual) |
| Goals | `LaneChargeGoal`: `telegraph` 1.5 s (paws, snorts, dust) → `charge` along the lane axis at 0.9 b/t until it reaches the lane end → `impact` 1.8 s stun. The dorsal plates open at the stun. Otherwise `walk` patrol. It never turns mid-charge. |
| Telegraph | Lowered skull, two paw scrapes |
| Open window | Every impact is a window (dorsal core on its back); periodic timer on top |
| Counterplay | Sidestep off the lane, then land on its back |
| Death | Rolls onto its side, legs curl |

### Mouth Arm — `mouth_arm/`
| | |
|---|---|
| Role | Self-heal / lifesteal pressure |
| Scale / HP | 3.0 / 150 HP |
| Goals | `BiteGoal`: 8-block reach. `telegraph` 1.0 s (lips peel, shriek) → `bite` 10 dmg + 3 infection. On hit: `feed` heals **the most-damaged living arm** by 15 HP (the feed anim runs a bulge down its neck). |
| Open window | Periodic |
| Counterplay | Don't feed it. Kill it early or keep your distance. The feed heal is capped at 45 HP per window cycle (**TUNE**). |
| Death | The throat gapes and the lips go slack |

### Spitting Arm — `spitting_arm/`
| | |
|---|---|
| Role | Ranged denial on the flight lanes between islands |
| Scale / HP | 3.0 / 140 HP |
| Goals | `VolleyGoal`: range 64. `telegraph` 1.2 s (sac inflates, gurgle) → `volley` 3 `BileGlob`s with lead prediction (0.6 accuracy, **TUNE**). The server spawns them at the three particle keyframe times. Globs deal 6 dmg + 4 infection and leave a 3 s bile puddle (Slowness II) when they land. |
| Open window | Periodic. Also opens if the sac is hit by the player's own deflected glob (skill tech: a shield parry reflects it). |
| Counterplay | Change altitude after the gurgle. Use cover arches. |
| Death | The sac bursts (big splash) |

### Arm attributes (baseline)
| Attr | Value |
|---|---|
| `generic.armor` | 0 (gating does the work) |
| `knockback_resistance` | 1.0 |
| `follow_range` | 64 |
| `movement_speed` | 0 (Charging: 0.35) |
| Gravity | off, `noPhysics` false, `pushable` false |

---

## 7. Body-attack table (encounter-wide events)

All are `BodyAttack` implementations picked by `BodyAttackScheduler` (weighted, no repeats back to back). Each sends an `S2CBodyAttackTelegraph` packet (`attackId`, `warnTicks`, `lanes`) so clients can play the overlay and sound first.

| Attack | Trigger / weight | Telegraph (warn) | Effect | Counterplay | CD |
|---|---|---|---|---|---|
| **Peristalsis** | any phase, w3 | 3 s: dome rumble, camera sway, ceiling villi contract, subtitle "The ground *swallows*" | All airborne players get Levitation II 2 s → Slow Falling 4 s; vehicles take a vertical impulse and a 2 s control dampener | Land or grab a cover arch; pilots cut throttle before it hits | 35 s |
| **Bile Rain** | w3 | 2 s: green drip particles mark 3 of 6 flight lanes (heart↔satellite) | 40 `BileGlob`s fall along the marked lanes over 4 s | Pick a lane that isn't marked | 30 s |
| **Retina Flash** | w2, needs line of sight to any eye (villi eyes on the dome + Bloom) | 1.2 s: eyes open on the dome walls, iris glow, "She *notices* you" | Players looking toward an eye (dot > 0.6) get Blindness 4 s + Darkness 6 s; everyone else gets a 1 s white vignette | Look away / at the floor; hide behind cover | 45 s |
| **Root Grip** | only players standing on islands, w2 | 1.5 s: root_dust bursts under the player's feet | Slowness III 3 s + a thorn patch (2 dmg/s, 5 s) where they stood | Keep moving after the dust; take off | 25 s |
| **Swallow** (added) | after 3 arms dead, w1 | 4 s: the membrane floor glows and the stomach "inhales" (wind particles toward the floor) | Pull of 0.08 b/t downward for 5 s over the whole arena | Climb and burn fuel. Punishes low-energy pilots. | 60 s |
| **Spore Exhale** (added) | when ≥ 3 vents alive, w2 | 2 s: every live vent `puff`s | +8 infection to everyone within 16 of a vent; spore haze lowers visibility | Break the vents | 40 s |

Why the two additions: Swallow reuses Peristalsis in reverse and escalates flight pressure late in the fight. Spore Exhale gives the vents a reason to be broken and ties them to the infection clock.

---

## 8. Infection clock

- A per-player attachment `matris_calyx:infection` (0–100, synced). It rises **+1 per 6 s** passively in Proto-World (ambient spread, **TUNE**), plus hits: bite +3, bile +4, Spore Exhale +8, add hits +2.
- **Stages.** 25: HUD pulse + Hunger. 50: Weakness I. 75: vision veining overlay + Slowness I. 100: **Taken**: the player is knocked unconscious for 3 s (screen fade), teleported to the anchor, clock reset to 40, Weakness II for 30 s. This is not death and costs no items.
- **Integration (`InfectionBridge`).**
  - `ImmortuosInfectionBridge` is loaded when `ModList.isLoaded("immortuos_calyx")`. It mirrors the value into Immortuos Calyx's infection system: it reads their level on entry, adds to it through their API or capability, and treats their cure items and effects as cures.
  - `NoopInfectionBridge` (Immortuos Calyx absent) uses the mod's own clock. It still works, with the cure items coming from the `matris_calyx:cures` tag (defaults: honey bottle −10, golden apple −25, milk −15, `matris_calyx:purgative_syringe` −40).
  - Config `infectionEnabled=false` turns the clock off entirely (accessibility / casual).
- **Syringe economy.** Players enter with what they crafted in Act I. The heart island's anchor has **one** refill cache of 4 syringes per player per fight (**TUNE**). Killing the Mouth Arm drops 2 syringes.
- **Doctor advantage (better, never exclusive).** Players with the pack's doctor role (tag `matris_calyx:doctor_tools` in hand, or a Progressive Stages stage the pack names in config) get cures **+50% effective**, can **inject another player** (use on them), and see all players' clock values over their heads. Everyone can cure themselves.
- **Canon.** The clock is the ambient spread in her body. It never refers to the chosen path. Cures always work.

---

## 9. Spore vents and add pressure

- There are 6 vents on the heart ring (`vent_0..5` locators on the Bloom model match these positions) and 1 on each satellite (12 total, **TUNE**).
- **Cadence.** Each live vent plays `spawn_add` every 30 s (**TUNE**, jitter ±5). It is paused while no player is within 48 blocks. There is a global add cap of 6 + 3 per player.
- **Break rules.** A vent is a block with 60 "vent HP", damaged by melee or projectiles (it plays `hurt`). At 0 it plays `broken`, becomes `matris_calyx:ruptured_vent` and stays dead for the rest of the fight. It can only be broken while its sphincter is open (during `puff` / `spawn_add`, about 2 s). Explosions deal half damage.
- **Add types.**

| Add | Behaviour | HP |
|---|---|---|
| `spore_thrall` | Slow infected walker; melee +2 infection | 20 |
| `spore_mite` | Tiny, fast, swarms in 3s; explodes into spore haze (+5 infection) | 6 |
| `infected_drifter` | A flying bladder that tethers to aircraft (−30% speed until you shoot it) | 12 |

---

## 10. Bossbar and scoring

- `MatrisBossBar` (`ServerBossEvent`, colour PINK, overlay `NOTCHED_6`). Title: "Matris Calyx".
- Value = Σ(arm contribution) / 600, where each arm contributes `100 × (armHP / armMaxHP)`. A dead arm contributes 0. So the bar drops smoothly as arms are hurt and lands exactly on a notch as each one dies.
- The client gets a custom overlay (`BossBarRenderer` hook) that draws six small arm icons under the bar. Dead ones are crossed out and the Nerve icon is outlined while its buff is active.
- **Bloom phase.** The same event is retitled "Calyx Bloom" with colour RED and overlay PROGRESS, value = BloomHP / BloomMax.
- **Sync.** Recomputed at most every 5 ticks, or immediately on a death event. Players are added when they enter the dimension and removed when they leave.
- **Scoring (optional, for pack quests).** `MatrisApi.getStats(uuid)` exposes: windows hit, cores struck, time to clear, syringes used, deaths. The advancement `matris_calyx:nerve_first` is granted if the Nerve Arm died first.

---

## 11. Phase 3 — Calyx Bloom

- **Model.** `calyx_bloom/`: 79 bones and 481 cubes. Six-lobed split heart with arteries, a three-segment stalk, a crown socket, eight toothed petals, a lidded eye with iris and pupil bones, and six hanging tendrils. Render scale **4.0**, so the eye is about 11 blocks across and the whole organ about 32 blocks tall.
- **HP.** 400 (**TUNE**). The weak spot is the eye (a core-zone part entity at the `laser` locator). Hits on the body deal 25%.
- **Laser cadence.** Target the player with the most threat, with line-of-sight tracking. **Aim** 1.5 s (the eye bone follows the target at 40°/s) → `laser_charge` 2 s (pupil pinches, petals flare, charge particles gather) → `laser_fire` 3 s. The beam is a raycast from the `laser` locator to the first solid block, 1.2-block radius. It sweeps toward the target at 18°/s (**TUNE**) and deals 8 dmg/tick-of-contact every 5 ticks, ignores armour, and adds infection +1. → **Rest** 4 s: the eye droops and the lids half-close. During rest the eye takes ×1.5 damage (the DPS check).
- **Cover rules.** Hardened flesh lobes and rib pillars block the ray. The ray stops at the first non-air, non-flesh-membrane block. Player-placed blocks block it for exactly one sweep and are then destroyed (no infinite cobble walls).
- **DPS-check feel.** Four burst windows per 40 s. Solo, clearing takes 6–8 cycles (about 60–80 s). Below 20% HP the cycle shortens, which rewards committing on the last rest.
- **Retina Flash** is re-used below 50%: the Bloom plays `retina_flash` and triggers the body attack of the same name.
- **Stagger.** Every 15% of max HP lost plays `stagger` and interrupts a charge (a reward for burst).

---

## 12. Quest / stage / chapter wiring

**The mod exposes** (stable API, no pack ids hard-coded):

| Hook | Form |
|---|---|
| `MatrisEvents.EncounterStarted(level, players)` | NeoForge event |
| `MatrisEvents.ArmKilled(level, armType, killer, order)` | event |
| `MatrisEvents.BloomEmerged`, `MatrisEvents.Victory(level, participants, stats)`, `MatrisEvents.Reset(reason)` | events |
| Advancements | `matris_calyx:enter_proto_world`, `…:first_arm`, `…:nerve_first`, `…:all_arms`, `…:bloom_slain` (the pack keys quests on `bloom_slain`) |
| Entity kill criteria | Each arm and the Bloom is a distinct `EntityType`, so QuestQueen kill tasks work directly |
| Commands | `/skylorecalyx start|reset|skipphase|status` (§13) |
| Config | `victoryFunction = "skylore:story/ending/savior"`: an optional mcfunction id run as the server on Victory for each participant |

**Skylore pack owns:**
- the QuestQueen chapter `proto_world` under Act V (entry quest "The Way Inward": Tempad → Proto-World; tasks: first arm, Nerve first (optional bonus), all arms, Bloom slain);
- Progressive Stages: `calyx_purged` granted by the pack's KubeJS listener on `MatrisEvents.Victory` (or the advancement `matris_calyx:bloom_slain`), mirrored to `skylore:story/ending/savior`;
- the gate: only players holding `path_spirit` can use the Tempad route (pack-side);
- the ending text, the credits, and the Skylore 2 teaser.

---

## 13. Java implementation outline

```java
public final class MatrisEncounter {                // one per Proto-World level
    EncounterState state; long stateTicks;
    final ArmSlot[] arms = new ArmSlot[6];          // type, anchor BlockPos, AABB lane/bounds, UUID, alive, hp
    UUID bloomId; float bloomHp;
    final BodyAttackScheduler attacks; final MatrisBossBar bar; final InfectionClock infection;
    void tick(ServerLevel lvl) {                    // LevelTickEvent.Post, 20 Hz
        switch (state) { case LIMBS -> tickLimbs(lvl); case BLOOM -> tickBloom(lvl); ... }
        if (lvl.getGameTime() % 5 == 0) bar.recompute(this);
        if (lvl.getGameTime() % 20 == 0) infection.tickSecond(lvl);
    }
}
```
- **Arm windows.** `ArmWindowController` on each arm: `closedTicks` counts down (base 300 ± 40) → sets `windowWarn` (16 ticks, triggers the `window_warn` anim) → `open` (60 ticks, `open_window` anim). `hurt(DamageSource, amount)` returns false unless `open && source hit the core part`.
- **Animation sync.** GeckoLib `triggerAnim("main", "grab")` from server goals. Looping state (idle/walk/charge/laser_fire) comes from synced entity data (`DATA_STATE`).
- **Packets.** `S2CEncounterSync(state, armsAliveMask, nerveBuff)` on change and on join. `S2CBodyAttackTelegraph(id, warnTicks, payload)`. `S2CInfectionSync(value)` on change. `S2CCameraShake(intensity, ticks)`.
- **Save / reload.** `EncounterSavedData` stores state, stateTicks, per-arm {alive, hp, windowTimer}, vent states, bloomHp, and participants. On load:
  - arms that are alive and missing (unloaded/killed by `/kill`) are respawned at their anchor with their saved HP;
  - dead ones stay dead;
  - a mid-laser Bloom resumes at REST.
  The arena chunks are force-loaded (a `TicketType` with the controller position) while state ∉ {DORMANT, VICTORY}.
- **Relog.** Joining players get the full sync and are added to the bossbar. Their infection value persists in the attachment.
- **Commands** (`permission 2`): `/skylorecalyx start [pos]`, `reset` (despawn all, restore template, DORMANT), `skipphase` (kill the next arm / skip to Bloom / kill Bloom), `status` (dump state), `window <arm> open` (debug), `infection <player> <value>`.
- **Datagen.** Lang, loot, advancements, sound definitions, entity tags, block states for flesh blocks.

---

## 14. Assets list

**Built in this folder (Blockbench, GeckoLib Animated Model format)**:

| Model | Bones / cubes | Animations |
|---|---|---|
| `grasping_arm` | 28 / 173 | idle, telegraph, grab, yank, open_window, window_warn, hurt, death, emerge |
| `slam_arm` | 18 / 159 | idle, telegraph, slam, open_window, window_warn, hurt, death, emerge |
| `charging_arm` | 24 / 127 | idle, walk, telegraph, charge, impact, open_window, window_warn, hurt, death, emerge |
| `mouth_arm` | 24 / 189 | idle, telegraph, bite, feed, open_window, window_warn, hurt, death, emerge |
| `spitting_arm` | 19 / 153 | idle, telegraph, volley, open_window, window_warn, hurt, death, emerge |
| `nerve_arm` | 27 / 162 | idle, pulse, open_window, window_warn, hurt, death, emerge |
| `calyx_bloom` | 79 / 481 | idle, emerge, laser_charge, laser_fire, retina_flash, stagger, death |
| `spore_vent` | 11 / 68 | idle, puff, spawn_add, hurt, broken |
| `bile_glob` | 2 / 13 | fly, burst |

Each folder contains `.bbmodel`, `.geo.json`, `.animation.json`, a 256² texture, a `_glowmask.png` (for GeckoLib `AutoGlowingGeoLayer`: iris, bile, nerve, core and glow veins), `particles/*.json`, and `renders/`.

**Particles** (Snowstorm / Bedrock format, used with locators in the animations): `bile_drip`, `bile_splash`, `spore_puff`, `spore_haze`, `nerve_spark`, `nerve_pulse`, `core_glow`, `blood_burst`, `flesh_chunks`, `root_dust`, `laser_charge`, `laser_beam`, `eye_glint`, `mucus_string`. In Java these become registered `ParticleType`s with matching names (or play through a Snowstorm-compatible library). The JSON is the tuning reference: colours, lifetimes, counts.

**Sounds** (keyframed in the animations, `matris_calyx:` namespace): `arm.open`, `arm.close`, `arm.window_warn`, `arm.core_burst`, `arm.collapse`, `arm.emerge`, `arm.<type>.hurt|death`, `arm.grasping.telegraph|whip|snap|yank`, `arm.slam.windup|strain|impact`, `arm.charging.snort|gallop|impact`, `arm.mouth.shriek|chomp|gulp`, `arm.spitting.gurgle|spit`, `arm.nerve.pulse`, `bloom.heartbeat|heart_split|tear|rise|eye_open|laser_charge|laser_loop|retina_inhale|retina_flash|hurt|death_scream|eye_rupture|collapse|last_heartbeat`, `vent.breathe|puff|strain|birth|hurt|rupture`, `bile.splat`, plus ambience `ambient.stomach` and the body-attack stingers `attack.<id>.warn`. Placeholders: pitched vanilla sounds (warden heartbeat, sniffer, slime, ravager roar, guardian beam).

**Still to make:** the three add models, flesh block textures, the dome template (`.nbt`), HUD textures, lang (`en_us.json`).

---

## 15. Balance sheet (all **TUNE**)

| Value | Start |
|---|---|
| Arm HP (N/G/Sl/C/M/Sp) | 120 / 150 / 180 / 150 / 150 / 140 |
| MP HP scaling | ×(1+0.5·(n−1)), cap ×3 |
| Window period / length / warn | 15 s ±2 / 3 s / 0.8 s |
| Nerve buff | window −0.5 s, +20% dmg |
| Post-Nerve window cadence | ×0.8 period |
| Grasp hover trigger | speed < 0.3 for 1.5 s, 28 range |
| Slam | 14 dmg, r 7, stuck 1 s |
| Charge | 0.9 b/t, stun 1.8 s |
| Bite / feed | 10 dmg, +3 inf / heal 15, cap 45 per cycle |
| Volley | 3 × 6 dmg, +4 inf, 64 range |
| Body attack interval | 25–40 s (×1.3 after 3 kills) |
| Infection passive | +1 / 6 s |
| Syringe cache | 4 per player |
| Vent HP / cadence | 60 / 30 s |
| Bloom HP / laser | 400 / 8 per 5 ticks, 18°/s sweep |
| Expected clear | solo 18–25 min, 4p 12–15 min |

**Playtest watchlist:** pilots stuck unable to land in 3 s windows (widen the window or add landing pads); Grasp eject feeling unfair on aircraft; infection feeling like a DPS race instead of a loadout check; laser cover being cheesed by pillaring; bossbar reading when two arms are damaged at once; performance with 12 vents and six arms loaded.

---

## 16. Canon / text rules checklist

- [ ] Never write: "the path infects you", "carriers of the Calyx", or any path-conditioned infection. No path marks or claims the player.
- [ ] Infection text = accident / "her air is thick with spores" (ambient). Cures always work. Hover text on cures: "It works. It always works."
- [ ] The Parasite is **one creature**: Matris is Immortuos Calyx's mother-body, not a second parasite. Wording: "the Calyx's root-body", "Matris, the Parasite Mother".
- [ ] The path is a stance: "You chose to stand with the Spirit."
- [ ] Other-ending contrast (`path_calyx`) is framed as edited ambition and consent, never contamination.
- [ ] Doctors: "Doctors cure faster" — never "only doctors can…".
- [ ] Ending handoff: the last line hands off to "Savior of Atmos" ("The stomach is quiet. The farm has no farmer.") and seeds Skylore 2 ("Somewhere above the dome, another field is being tended.").

---

## 17. Open risks

| Risk | Mitigation |
|---|---|
| Pehkui | **Not used.** Renderer scale + custom `sized()` hitboxes. Pehkui on arms breaks GeckoLib locators and the hitbox part entities. |
| Map-scale performance | At most 6 arms + 1 Bloom + 12 vent BEs + capped adds. Body attacks are packets, not entities. Bile Rain is capped at 40 projectiles per 4 s. The arena is force-loaded only while active. Bake cube counts: the Bloom has 481 cubes, which is fine for one instance. |
| Multiplayer | HP scaling; per-player infection; windows are shared (co-op coordination is a bonus, not required). |
| Dimension ownership | Decide early (§3). `arenaSource` config supports both. |
| Soft deps | Immortuos Calyx bridge behind `ModList` + a separate class so the absent classes never load; the Noop bridge is fully functional. |
| Flight mods | Levitation/impulse effects must be tested against Immersive Aircraft physics; fall back to "control dampener" only via the `flight_vehicles` tag. |
| Chunk unload mid-fight | SavedData is the truth; entities are re-spawned from slots; tickets keep the arena loaded. |

---

## Implementation order

1. **Void-box prototype:** `MatrisEncounter` + `EncounterControllerBlock` + Nerve Arm + `MatrisBossBar` + `/skylorecalyx start|reset` in a superflat void test world.
2. **All six arms + windows:** the `AbstractRootedArm` family, `ArmWindowController`, core part entity, leash, per-arm goals.
3. **Body attacks + infection hook:** scheduler, the six attacks, `InfectionClock`, Noop and Immortuos bridges, syringe cache, vents and adds.
4. **Bloom phase:** `CalyxBloom`, laser raycast + cover rules, heart-split transition.
5. **Dimension / arena:** `proto_world` registration or DATAPACK mode, `ArenaPlacer`, the template, DH silhouette pass.
6. **Pack API + quest hooks:** `MatrisEvents`, advancements, `victoryFunction`, KubeJS sample, QuestQueen `proto_world` chapter.
7. **Art / sound pass:** add models, glowmasks, real sounds, particle polish, lang.
