# Renegades

Rare hostile adventurers that mirror RPG classes, wearing and dropping their class gear.

**Work in progress.**

- Minecraft 1.21.1
- NeoForge
- License: MIT (see [LICENSE](LICENSE))

## Equipment pools (datapack)

What a renegade wears is defined by datapacks, not by code. Files live in
`data/<namespace>/renegades/equipment_pools/<any path>.json`; each file belongs to one mob. All files for the same mob
are merged, and a file at the same path in a higher datapack replaces the one below (e.g. to override the bundled data
in `data/renegades/renegades/equipment_pools/warrior/`). `/reload` applies changes.

```json
{
  "neoforge:conditions": [{ "type": "neoforge:mod_loaded", "modid": "rogues" }],
  "entity": "renegades:warrior",
  "fallback": false,
  "entries": [
    {
      "id": "rogues_tier1",
      "weight": 10,
      "conditions": {
        "dimensions": ["minecraft:overworld"],
        "min_distance": 0,
        "max_distance": 1500,
        "min_y": -64,
        "max_y": 320,
        "min_level": 1,
        "max_level": 44
      },
      "equipment": {
        "head": "rogues:warrior_armor_head",
        "chest": "rogues:warrior_armor_chest",
        "legs": "rogues:warrior_armor_legs",
        "feet": "rogues:warrior_armor_feet",
        "mainhand": ["rogues:iron_double_axe", "rogues:iron_glaive"],
        "offhand": "#c:tools/shield"
      }
    }
  ]
}
```

- `neoforge:conditions` (optional): standard NeoForge load conditions; the whole file is skipped if they fail.
- `entity`: the mob this file is for.
- `fallback` (default `false`): fallback entries are only used if no regular entry matches.
- `entries`: on spawn, one entry is picked as a whole (weighted) from all matching regular entries, otherwise from
  the matching fallback entries. If nothing matches, the mob spawns without equipment.
  - `id`: name for logs and debug commands. `weight` (default `1`).
  - `conditions` (all optional, all given ones must match, ranges inclusive): `dimensions`; `min_distance` /
    `max_distance` (horizontal distance in blocks from the world spawn); `min_y` / `max_y`; `min_level` / `max_level`.
  - `equipment`: slots `head`, `chest`, `legs`, `feet`, `mainhand`, `offhand`. A value is an item id, a `#tag`
    (random item of the tag) or a list of these (one is picked at random). Missing slots stay empty.
- Invalid files or entries (broken JSON, unknown items, empty tags) are skipped with a warning in the log.

Equipment is chosen when the mob spawns naturally, from a spawn egg or spawner, or via `/summon` without NBT
(`/summon` with NBT keeps vanilla behavior: no equipment is chosen).

**Mob levels:** `min_level` / `max_level` need a level source. With [Dynamic Difficulty](https://github.com/muon-rw/Dynamic-Difficulty)
installed, the mob's level is used and the chosen set matches the level the mob ends up with. Without a level source,
level conditions are ignored.

**Debug commands** (permission level 2): `/renegades pools <entity>` lists the loaded entries, `/renegades reequip <targets>`
rolls the equipment again at the mob's current position and shows which entry was chosen and why.

## Natural spawns

Renegades do not use the vanilla monster spawning (whose mob cap is usually full). They have their own spawn timer:
every `spawnIntervalSeconds` each player gets one attempt that succeeds with `spawnChance`. A successful attempt looks
for a free spot with solid ground `minDistance`–`maxDistance` blocks around the player, on the surface or (with
`allowUnderground`) in a cave, picks a spawn group (see below) whose leader may spawn in that biome, and spawns it as a
natural spawn. No attempts on Peaceful, with `doMobSpawning` off or for spectators.

Which biomes a class may spawn in is set by the biome tag `#renegades:spawns/<class>`, e.g. `#renegades:spawns/warrior`
(default: `#minecraft:is_overworld`, `#minecraft:is_nether`, `#minecraft:is_end`). Override the tag in a datapack to
change it.

Options in `[spawns]` of `renegades-server.toml`:

| Option | Default | Effect |
|---|---|---|
| `spawnIntervalSeconds` | `60` | Seconds between two attempts per player. |
| `spawnChance` | `0.03` | Chance per attempt (60 attempts per hour × 0.03 ≈ 1.8 spawns per hour and player). |
| `minDistance` / `maxDistance` | `24` / `64` | Horizontal distance from the player in blocks. |
| `allowUnderground` | `true` | Half of the attempts look for a cave spot within 32 blocks of the player's height. |
| `requireDarkness` | `false` | `true` uses the normal monster light rules. |
| `maxNearby` | `3` | No spawn (and no further group members) if this many renegades are within `capRadius` of the spawn position or of the nearest player. |
| `capRadius` | `128` | Radius in blocks for `maxNearby`. |
| `persistentNaturalSpawns` | `true` | Renegades from natural spawns, spawn eggs and commands never despawn. Renegades from (trial) spawners always despawn like monsters. |

## Strength

Each class has its own base attributes (warrior: 60 health, 6 attack damage, 0.3 knockback resistance). In `[strength]`,
`healthMultiplier` and `damageMultiplier` (default `1.0`) multiply the base values of all renegades when they spawn,
before level scaling such as Dynamic Difficulty is applied on top. Renegades that already exist keep their values.

## Combat AI

Renegades fight with telegraphed attacks: every attack has a windup, an active window and a recovery, and damage is
only dealt in the active window, when the weapon actually swings through its target. A hit is a regular mob melee hit
(attack damage incl. weapon and level bonuses, weapon enchantments such as Sharpness and Fire Aspect, blockable with a
shield) and needs line of sight, so attacks never go through walls.

**Warrior:** alternates heavy sweeps right-to-left and left-to-right; closes gaps with a dash that chains into a rising
dash cut; strafes and backs off around its target between attacks. When hit in melee from close range while not
attacking, it sometimes parries: half damage and an immediate counter-strike (`enableParry` in `[combat]`, default
`true`). `/renegades nearby` shows stance, combat state and the running action with its phase.

Combat system based on ANARCHY – RPG Minibosses by cleannrooster (CC0-1.0).

## Spawn groups (datapack)

When a renegade spawns naturally, it can bring company. Templates live in
`data/<namespace>/renegades/spawn_groups/<any path>.json` (one template per file; same override, `neoforge:conditions`
and error handling as the equipment pools; the file id, e.g. `renegades:warrior_pair`, is the template id).

```json
{
  "leader": "renegades:warrior",
  "weight": 2,
  "conditions": { "dimensions": ["minecraft:overworld"], "min_distance": 500 },
  "members": [
    { "entity": "renegades:warrior", "min": 1, "max": 1 }
  ]
}
```

- `leader`: the mob whose natural spawn can create this group. `weight` (default `1`).
- `conditions` (optional): same conditions as in equipment pools, checked at the leader's position.
- `members` (optional): entity, `min` (default `1`) and `max` (default `min`) per member type. No members = the
  leader spawns alone.

The spawn timer picks a template by weight among all templates whose leader may spawn in the biome (see
"Natural spawns") and whose conditions match; that decides which class spawns. The spawned leader then picks its final
template among its own templates, now with its mob level known (none matching = alone). Members spawn on
free ground next to the leader, roll their own equipment and never start a group of their own; members that do not
fit (no valid spot, `maxNearby` reached) are left out. Spawn eggs always spawn a single renegade.

All members share a group id: if a player attacks one, the members within 32 blocks attack that player; without a
target, members walk back to the group when they are more than 12 blocks away. Bundled: `warrior_solo` (weight 8)
and `warrior_pair` (weight 2).

**Debug commands**: `/renegades groups <leader>` lists the templates, `/renegades spawngroup <id>` spawns one at your
position like a natural spawn, `/renegades nearby` lists the renegades within `capRadius` with group, spawn type,
persistence, health and attack damage, `/renegades stats [reset]` shows the spawn timer's attempts, failures by reason
and spawns since server start (extrapolated per hour).

### Drop rules

Configured in the server config `config/renegades-server.toml`. To override it for a single world, put a copy into
`<world>/serverconfig/renegades-server.toml`.

| Option | Default | Effect |
|---|---|---|
| `armorDropChance` | `1.0` | Chance for each armor piece to drop. |
| `weaponDropChance` | `1.0` | Chance for each held item (main and off hand) to drop. |
| `requirePlayerKill` | `true` | Equipment only drops if a player killed the mob or hit it shortly before. |
| `dropUndamaged` | `true` | Dropped equipment keeps its durability (otherwise random damage like vanilla). |

Items with Curse of Vanishing (or any `prevent_equipment_drop` effect) never drop; Looting raises the chance like for
vanilla mob equipment. The regular loot table (`renegades:entities/warrior`) is empty by default and can be filled
by datapacks.