# TaCZ Fixes

An add-on mod for [Timeless and Classics Zero](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero).

- Adds mechanics on top of TaCZ 1.2.0, plus a large set of data-driven features aimed at gun-pack authors. Most features also work out of the box.

## Versions

| Branch | Minecraft | Loader | Mod version | TaCZ version |
| --- | --- | --- | --- | --- |
| `taczfixes` | 1.20.1 | Forge 47.4+ | 3.1.2 | tacz-forge 1.20.1 1.2.0 |
| `taczfixes-1.21.1` | 1.21.1 | NeoForge 21.1+ | 3.1.2 | tacz-neoforge 1.21.1 1.2.0 |

Client and server must run the same TaCZ Fixes version (network protocol version `3`). Single-player and client-side installation is enough to use every feature.

## Dependencies

Required:

- Timeless and Classics Zero 1.2.0

Optional (the related features degrade gracefully when missing, without crashing):

- **Cloth Config** 11.x — in-game configuration screen
- **ApricityUI** 1.3.1 — custom HTML HUDs for gun packs

## Features

### Refit and attachments

- Custom attachment slots (data-driven; gun packs can define slots, allow/deny lists and adapters)
- Virtual attachments: install attachments without taking inventory space or consuming items
- Refit points system: installing costs points, remaining points are shown, overspending is rejected
- Refit presets: save / load / import-export refit codes
- Refit recoloring, search and pagination in the refit screen, zoom, slot size and position tweaks
- More attachment stats: magazine capacity, effective range, friction, gravity, sprint/reload/bolt time, etc. shown in tooltips

### Gun mechanics

- Limb damage multipliers (by gun type and hit part), crawl/sneak hit thresholds
- Recoil multipliers, spread multipliers, burst spread penalty, jump spread
- Ricochet, bullets piercing glass, bullet collision with Create contraptions
- Gun durability, anvil/enchantment compatibility, gun levels and kill XP
- Custom fire modes (full-auto / burst / semi-auto, etc.) and burst penalties
- Firing block (nearby muzzle pushes the gun aside), blocked shooting underwater
- Auto-crafting at the gun smith table (consumes FE, configurable duration and power)
- Gun enchantment system (30+ enchantments: power, piercing, chain explosion, lifesteal, double shot, decapitation, etc.)

### Visuals and camera

- PBR reflections, bloom, emissive effects, enchantment glint
- Dynamic muzzle / bullet / explosion lighting
- Stepless scope zoom, aim depth of field, sprint interruption while aiming, hold breath
- Suppression effects: impact blur, vignette, chromatic aberration
- Dynamic crosshair, gun movement roll and rotation inertia

### Dual wielding

- Dual pistols: independent offhand fire / reload / bolt / fire-mode switch / melee / shield
- One-arm aiming (offhand usable), third-person gun-holding animations
- Offhand ammo HUD, heat bar

### Stamina and crawling

- Aiming stamina and general stamina systems (hold-breath cost, weight influence, sway at low stamina)
- ParCool integration: slide/crawl stamina cost and HUD integration
- Crawl pose compatibility: both TaCZ crawling and ParCool crawling can fire/reload normally, without resetting attachment animations

### Animations

- Independent Lua animation state machines per attachment (`example/grip_emx_bipod` is a complete bipod example)
- Attachment animations stay stable while firing, reloading, crawling and switching hands

### HUD

- Custom HTML HUDs shipped by gun packs (ApricityUI)
- HTML HUDs can display custom parameters published by the gun's Lua logic script
- Offhand gun HUD, stamina bar, aiming stamina bar

### Gun pack management

- Gun pack list management, ordering, prefix/version validation and incompatibility warnings
- Search / download / update gun packs from CurseForge; automatic update check on startup with an update dot
- Automatic conversion of legacy gun packs and generation of a missing `gunpack.meta.json`
- Hand-held gun/attachment data editing (written back to the source gun pack)

### Compatibility

- Create, Accelerated Rendering, LesRaisins Tactical Equipements, Iron's Spellbooks, ProjectE
- ParCool, Peek, TouhouLittleMaid, YesSteveModel, Embeddium

## Key bindings

| Default key | Action |
| --- | --- |
| `Alt + S` | Open the config screen (requires Cloth Config) |
| `Alt + G` | Gun pack management |
| `Alt + D` | Edit held gun/attachment data |
| `B` | Switch scope |
| `Left Ctrl` | Hold breath |
| `X` | One-arm dual-wield aiming (only the current hand is raised while aiming) |
| `Y` | Switch offhand fire mode |

## Commands

| Command | Permission level | Description |
| --- | --- | --- |
| `/taczfixes compat` | 0 | List whether the mods integrated with TaCZ Fixes are installed, and their versions |

## Configuration

Configuration lives in `config/taczfixes/*.toml`, split into 20 files by module, for example:

`gun_level`, `limb_damage_multiplier`, `recoil_modifier`, `inaccuracy_modifier`, `bullet_ricochet`, `recoil_knockback`, `gun_light`, `gun_spell`, `stepless_zoom`, `dynamic_crosshair`, `refit`, `auto_craft`, `blocking`, `compat`, `gun_enchantment`, `aiming_stamina`, `stamina`, `dual_wield`, `visual_effect`, `suppression`

The files contain comments. Changes take effect after reloading resources or restarting; server-side configuration is synchronized to clients.

## Data directories

- `taczfixes_cache/` — gun/attachment data overrides, refit presets, CurseForge search cache and install records
- `apricity/taczfixes/<namespace>/hud/*.html` — synchronized automatically from gun packs, **do not edit manually**
- `tacz/` — gun pack directory (zip files or folders)

## For gun pack authors

- HTML HUDs go in `assets/<namespace>/hud/**.html`; the mod synchronizes them to ApricityUI automatically
- Lua logic scripts can publish custom HUD values with `api:setHudParam(key, value)` / `api:setHudParams(table)`; pages read them via `data-taczfixes-bind="key"` and `data-taczfixes-param-*`. See `example/example_logic.lua` and `example/example_custom_hud.html`
- Attachment slots, virtual attachments, refit point costs, enchantment compatibility, etc. are all defined through data JSON
- See `example/` for attachment animation examples: `grip_emx_bipod` is a working bipod state machine with animation configuration
- Besides the attachment's own bones, attachment animations can drive four of TaCZ's gun functional groups: `camera` (camera rotation, applied to both the first-person view and the hands), `root` (whole-gun translate/rotate/scale) and `lefthand_pos` / `righthand_pos` (first-person hand positions). Animating them overlays the same movement on the gun's matching group; the attachment model does not need those bones, placeholders are added automatically.

### Dynamic camoflage

Supports dynamic camoflage based on gunspack. Visit `example/camo_example_pack` to learn more.

## Building

Requires JDK 17. Dependencies must be placed under `repo/` (not provided here); the required artifacts are listed in `build.gradle`.

```bash
./gradlew build --offline
```

The artifact is produced at `build/libs/taczfixes-<version>.jar`.

## License

MIT License · Ssscript
