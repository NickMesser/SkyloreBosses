# Adding a boss

Use Matris Calyx as the template. Everything for a boss lives in two clearly named places:

- `bosses/<boss_id>/`: design doc, Blockbench models and generator tools (repo folder)
- `src/main/java/net/teamaof/skylorebosses/bosses/<bossid>/`: the Java module

## 1. Art and tools: `bosses/<boss_id>/`

1. Copy `bosses/matris_calyx/tools/boss_env.py` and set `BOSS = "<boss_id>"`. For a machine boss call `texture.use("industrial")` there (see `bosses/overhead/tools/boss_env.py`).
2. Write the model specs (see `arms.py`). Each file starts with `import boss_env`, then uses `lib.Model`, the shared particle library `lib.P`, and `m.sfx(anim, t, "sound.name")`.
3. Build them in Blockbench:
   - Copy `build_one.py` and `build_all.py`.
   - Models are written to `bosses/<boss_id>/models/<model>/`.
4. Copy `export_to_mod.py` and `gen_data.py` and adjust the model lists, then run both:
   - Models go to `assets/skylore_bosses/{geo,animations,textures}/.../<boss_id>/`.
   - Sounds go to `sounds/<boss_id>/` and merge into the shared `sounds.json`.
   - Lang entries merge into the shared `en_us.json`.
   - Generated Java: `<Boss>Locators.java` and `<Boss>SoundIds.java`.

## 2. Java module: `bosses/<bossid>/`

1. **Registry classes:**
   - Add entries to the shared registers, e.g. `SBRegistries.ENTITY_TYPES.register("my_boss", ...)`.
   - Give each registry class an empty `public static void init() {}` so the module can force it to load.
2. **Entities:** implement GeckoLib's `GeoEntity`.
   - Use `AnimFx.keyframeParticle` and `AnimFx.keyframeSound` in the controller's keyframe handlers.
   - Use `AnimFx.locator(...)` for server-side positions such as projectile origins.
3. **Renderers:** extend `core.client.GeoBossRenderer` with `(ctx, "<boss_id>", "<model>", scale)`.
4. **Module class:** a `<Boss>Boss implements BossModule` with:
   - `id()`, returning `"<boss_id>"`
   - `registerContent()`: call the `init()` methods and `<Boss>Locators.register()`
   - `soundIds()`
   - optionally `defineConfig`, `commonInit`, `clientInit`, `buildCommands` and `commandAliases`
5. Add `new <Boss>Boss()` to `core/BossModules.java`.
6. **Events:** fire `BossEvents.STARTED` and `BossEvents.DEFEATED` so the pack can hook the boss without special cases.

## 3. Document

- Add a row to the Bosses table in the root `README.md`.
- Add a `bosses/<boss_id>/README.md` like the Matris one.
