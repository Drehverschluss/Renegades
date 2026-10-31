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

Bundled pools (per class in `warrior/`, `rogue/`, `paladin/` and `archer/`): a vanilla fallback by distance only, valid in every dimension
(warrior: iron, then iron or diamond, then diamond; rogue: leather with an iron sword, then chainmail with two iron
swords, then iron with two diamond swords; paladin: chainmail, then iron, then diamond, each either with an axe or with
a sword and a shield; archer: leather, then chainmail, then iron, each with a bow or a crossbow), and the class sets in
three tiers from the matching mods (warrior with Rogues: warrior /
berserker / netherite berserker armor with double axes or glaives; rogue with Rogues: rogue / assassin / netherite
assassin armor with a dagger or sickle and a dagger in the off hand; paladin with Paladins: paladin / crusader /
netherite crusader armor with a claymore or great hammer, or a mace with a kite shield; archer with Archers: archer /
ranger / netherite ranger armor with the bows or crossbows of the tier, plus, with Archers Expansion, the tundra hunter,
war archer and deadeye sets in tiers 2 and 3).

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

Each class has its own base attributes (warrior: 60 health, 6 attack damage, 0.3 knockback resistance, speed 0.25;
rogue: 45 health, 5 attack damage, 0.1 knockback resistance, speed 0.30; paladin: 75 health, 6 attack damage, 0.6
knockback resistance, speed 0.23; archer: 40 health, 4 attack damage, no knockback resistance, speed 0.28). In `[strength]`,
`healthMultiplier` and `damageMultiplier` (default `1.0`) multiply the base values of all renegades when they spawn,
before level scaling such as Dynamic Difficulty is applied on top; `damageMultiplier` also scales the archer's arrows.
Renegades that already exist keep their values.

## Combat AI

Renegades fight with telegraphed attacks: every attack has a windup, an active window and a recovery, and damage is
only dealt in the active window, when the weapon actually swings through its target. A hit is a regular mob melee hit
(attack damage incl. weapon and level bonuses, weapon enchantments such as Sharpness and Fire Aspect, blockable with a
shield) and needs line of sight, so attacks never go through walls.

**Warrior:** alternates heavy sweeps right-to-left and left-to-right; closes gaps with a dash that chains into a rising
dash cut; strafes and backs off around its target between attacks. When hit in melee from close range while not
attacking, it sometimes parries: half damage and an immediate counter-strike (`enableParry` in `[combat]`, default
`true`). `/renegades nearby` shows stance, combat state and the running action with its phase.

**Rogue:** stalks its target in a wide circle (about 8 blocks), turning invisible for a while, and throws knives (two
per throw) from range. When the target lowers its guard (stops attacking, just landed, or is low on health), it runs to
a spot at the target's side or back and strikes with crossing slashes, rolling in and out. After a few seconds of
exposure, or when badly hurt, it withdraws with a roll and a burst of speed and throws knives while retreating.
Heavy hits (more than 4 damage) are sometimes dodged completely with a sidestep (`enableDodge` in `[combat]`, default
`true`). Invisibility hides only the body: armor and held items stay visible, like on an invisible player
(`enableStealth`, default `true`). Its paths prefer rough ground and, while withdrawing, never lead back towards the
target. If the target is out of melee reach (pillar, ledge), it circles below and keeps throwing knives.

Thrown knives fly straight for up to 2 seconds and deal the rogue's attack damage × 0.6 as a mob projectile (blockable
with a shield). They need line of sight, hit only what a melee swing of the rogue could hit and never drop an item;
they show the rogue's dagger (main or off hand), otherwise an iron sword. With mods that give armor an evasion chance
(e.g. Spell Engine's `evasion_chance` on Rogues armor), renegades evade hits like players do.

**Paladin:** advances slowly and heavily: a wide overhead swing, a shoulder check up close, a ground slam, a whirlwind
spin that hits everything around it several times, and a leap at targets 6–14 blocks away that lands with a ground
impact (radius 6 and 3 blocks high, attack damage × 1.0). It only leaps to safe spots: at most 1.5 blocks higher or lower, no block in
the way of the arc (walls, ceilings), solid ground under the whole path, no water or lava (`enableLeap` in `[combat]`,
default `true`). After a heavy hit (8+ damage), and once when its health drops below 40 %, it braces for 2.5 seconds
(`enableBrace`, default `true`): with a shield in the off hand it raises it and blocks like a player (only hits from
the front; an axe disables the shield for 5 seconds and ends the brace), without one it takes half damage. While
bracing it is hardly pushed back. Below 20 % health it makes its last stand: no more bracing, faster, and its big
attacks are ready again at once. Its equipment is either a two-handed weapon or a one-handed weapon with a shield.

**Archer:** fights with a bow or a crossbow and keeps 8–13 blocks from its target. Standing still, it shoots aimed
shots and four-arrow volleys; when the target gets too close, too far or out of sight, or an ally stands in the line of
fire, it runs to a new firing position with a clear view (circling around the target instead of past it), snap-shooting
and throwing traps on the way. When the target lowers its guard (stops attacking, lands, drinks or eats, is low on
health, or stands near a trap), it shoots faster. If the target gets right next to it, it shoves it away. Shots work like
those of skeletons (bow) and pillagers (crossbow): the arrow comes from the held weapon, so its enchantments (Power,
Flame, Punch, Multishot, Piercing, Quick Charge shortens the shot cooldowns) apply; arrows cannot be picked up, a
shield blocks them, and mob-level projectile bonuses (e.g. Dynamic Difficulty) apply. Shots need line of sight but no
height limit, so a target on a pillar is still shot at. Arrows of renegades fly through allies and bystanders.
Traps (three per throw, `enableTraps` in `[combat]`, default `true`) arm after 2 seconds and go off when an enemy comes
within 2 blocks: radius 2.5 and 3 blocks high, attack damage × 1.5. They vanish after 8 seconds or when the archer dies,
are not saved and drop nothing.

**Hazards:** the combat movement steers directly instead of using pathfinding. Before every step (walking, strafing,
backing off, circling, attack advances, dashes and rolls) the ground ahead is checked: renegades never step into lava,
fire, magma, cactus, powder snow, sweet berries or water, nor off a drop deeper than 3 blocks. At such an edge they
move only along it and then walk to their goal with normal pathfinding, which finds the way around.

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
target, members walk back to the group when they are more than 12 blocks away. Bundled: `warrior_solo` (weight 8),
`warrior_pair` (weight 2), `rogue_solo` (weight 6), `warrior_rogue` (a warrior leading a rogue, weight 2),
`paladin_solo` (weight 5), `paladin_warrior` (a paladin leading a warrior, weight 2), `archer_solo` (weight 5),
`warrior_archer` (a warrior leading an archer, weight 2) and `paladin_archer` (a paladin leading an archer, weight 1).

**Debug commands**: `/renegades groups <leader>` lists the templates, `/renegades spawngroup <id>` spawns one at your
position like a natural spawn, `/renegades nearby` lists the renegades within `capRadius` with group, spawn type,
persistence, health, attack damage, invisibility and the combat state, `/renegades stats [reset]` shows the spawn timer's attempts, failures by reason
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
vanilla mob equipment. The regular loot tables (`renegades:entities/<class>`) are empty by default and can be filled
by datapacks.