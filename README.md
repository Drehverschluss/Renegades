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
    (random item of the tag), an item object (see below) or a list of these (one is picked at random). Missing slots
    stay empty.
  - `variant` (optional): a class variant the mob takes when this entry is chosen (wizard: `fire`, `frost` or
    `arcane`; without it, or with an unknown value, the variant is random). Other classes ignore it.
- Invalid files or entries (broken JSON, unknown items, empty tags) are skipped with a warning in the log.

An item object adds enchantments to an item or tag:

```json
"mainhand": [
  { "item": "minecraft:diamond_sword", "enchantments": { "minecraft:sharpness": 3 } },
  { "item": "minecraft:bow", "enchant_with_levels": { "min": 15, "max": 25 } }
]
```

- `item`: item id or `#tag`.
- `enchantments` (optional): fixed enchantments with their levels.
- `enchant_with_levels` (optional): random enchantments like an enchanting table with a level between `min` and `max`
  (applied first; fixed enchantments are added on top).
- Unknown enchantment ids are ignored with a warning in the log; the rest of the entry stays valid.

Bundled pools (per class in `warrior/`, `rogue/`, `paladin/`, `archer/`, `wizard/` and `priest/`): a vanilla fallback by distance only, valid in every dimension
(warrior: iron, then iron or diamond, then diamond; rogue: leather with an iron sword, then chainmail with two iron
swords, then iron with two diamond swords; paladin: chainmail, then iron, then diamond, each either with an axe or with
a sword and a shield; archer: leather, then chainmail, then iron, each with a bow or a crossbow; wizard: leather dyed in
the school's color, then chainmail, then iron, with a blaze rod (fire), breeze rod (frost) or end rod (arcane); priest:
leather dyed white with golden hood and shoes, then chainmail, then iron, with a golden hoe as its staff; weapons
of the middle tier get random enchantments of level 5–15, those of the far tier of level 15–25 – for the priest, whose
hoe does nothing in combat, the chest piece instead), and the class sets in
three tiers from the matching mods (warrior with Rogues: warrior /
berserker / netherite berserker armor with double axes or glaives; rogue with Rogues: rogue / assassin / netherite
assassin armor with a dagger or sickle and a dagger in the off hand; paladin with Paladins: paladin / crusader /
netherite crusader armor with a claymore or great hammer, or a mace with a kite shield; archer with Archers: archer /
ranger / netherite ranger armor with the bows or crossbows of the tier, plus, with Archers Expansion, the tundra hunter,
war archer and deadeye sets in tiers 2 and 3; wizard with Wizards: wizard robe with a novice wand or wizard staff
(random variant), then the fire, frost or arcane robes with the wand and staff of their school, then the netherite
versions; priest with Paladins: priest robe with an acolyte wand, holy wand or holy staff, then the prior robe with a
diamond holy wand or staff, then the netherite versions; for the wizard and priest sets, the Nether always uses tier 2
and the End tier 3; forcemaster with Forcemaster RPG: oriene / phasleb / aken armor with iron / diamond / netherite
knuckles in both hands).

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
| `noSpawnInColonies` | `true` | With [MineColonies](https://www.curseforge.com/minecraft/mc-mods/minecolonies) installed: no natural spawns (leaders and group members) inside a colony. Spawn eggs, spawners and commands are not affected. The integration is optional; nothing from MineColonies is bundled. |

## Strength

Each class has its own base attributes (warrior: 60 health, 6 attack damage, 0.3 knockback resistance, speed 0.25;
rogue: 45 health, 5 attack damage, 0.1 knockback resistance, speed 0.30; paladin: 75 health, 6 attack damage, 0.6
knockback resistance, speed 0.23; archer: 40 health, 4 attack damage, no knockback resistance, speed 0.28; wizard: 35
health, 3 attack damage, no knockback resistance, speed 0.26; priest: 40 health, 3 attack damage, no knockback
resistance, speed 0.26). In `[strength]`,
`healthMultiplier` and `damageMultiplier` (default `1.0`) multiply the base values of all renegades when they spawn,
before level scaling such as Dynamic Difficulty is applied on top; `damageMultiplier` also scales the archer's arrows.
Renegades that already exist keep their values.

## Combat AI

Renegades fight with telegraphed attacks: every attack has a windup, an active window and a recovery, and damage is
only dealt in the active window, when the weapon actually swings through its target. A hit is a regular mob melee hit
(attack damage incl. weapon and level bonuses, weapon enchantments such as Sharpness and Fire Aspect, blockable with a
shield) and needs line of sight, so attacks never go through walls.

**Cooldowns:** each attack has a cooldown, shortened at random – about 40 % on average for a renegade
fighting alone. Renegades fighting near other renegades (within 32 blocks) get somewhat shorter cooldowns, but at most
25 % shorter than alone, and no cooldown is ever shorter than 1 second (`cooldownGroupScaling` in `[combat]`,
default `true`; `false` = always like alone). The priest's support spells always use their full cooldown.

**Performance:** measured on a dev server, 20 renegades fighting at once cost about 0.8–1.0 ms per server tick
(about 40 µs per renegade incl. vanilla movement); renegades without a target only run the normal mob AI.
`/renegades profile start|stop` shows where the time goes.

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

**Archer:** fights with a bow or a crossbow and keeps 8–13 blocks from its target (with a bow it stands turned sideways,
bow arm towards the target; with a crossbow it holds it with both hands). Standing still, it shoots aimed
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

**Wizard:** a caster of one school (`fire`, `frost` or `arcane`, set by its equipment entry or random; leather armor
is dyed in the school's color). It circles its target at about 7.5 blocks and casts from up to 24 blocks: two quick
bolts per cast (attack power × 0.4 each), and against a target that stands still a volley of four homing bolts that
burst on impact (radius 4, × 0.2). A target that comes closer than about 5 blocks is hit by a nova around the wizard (radius 6,
× 0.6, `enableNova` in `[combat]`, default `true`), followed by a jump back, or by a short sweep of its staff. Fire
spells set targets on fire, frost spells slow them (Slowness II), arcane bolts fly 25 % faster. Spells need line of
sight, never hurt other renegades, never set blocks on fire and never break blocks. Spell damage is magic damage
(`renegades:<school>` damage types, tagged `c:is_magic`): it ignores armor, and witches resist it. Shields only stop
spell **projectiles** (the wizard's and priest's bolts, the volley bolts): raised and facing the bolt, a shield blocks
it like an arrow – no damage, no fire or slowness, the shield loses durability. Area spells (nova, the burst of a
volley bolt, clouds) go through shields.

**Priest:** a healer that keeps its group alive. It keeps 8–14 blocks from its target, if possible behind an ally (the
ally between it and the target), and backs off or jumps back when the target comes closer than 4 blocks. Its spells,
by priority:

- **Self heal** when alone and below 40 % health (cooldown 20 s).
- **Healing cloud** when at least two allies within 8 blocks of each other are below 60 %: a cloud (radius 4) at their
  middle that heals allies in it for 5 seconds; it never heals players or other mobs (cooldown 25 s).
- **Heal** the ally with the lowest health below 70 % within 16 blocks and in sight: holy light from the priest to the
  ally (attack power × 2.5, cooldown 8 s).
- **Protection:** an ally that was just hit gets Absorption I for 10 seconds (cooldown 20 s).
- **Holy bolt** (attack power × 0.35, cooldown 4 s, blocked by shields) – only when nobody needs healing or when
  alone.

Heal, self heal and cloud have a visible windup (raised hands and healing particles, 1.5 s; the cloud 2.7 s) in which
the priest stands still. A hit of 4 or more damage, or any melee hit, during the windup **interrupts** the spell, and
its cooldown restarts at half length – attacking the priest first is the way to beat a group. Caps: one heal restores
at most 25 % of the target's max health, a cloud at most 30 % per target in total, and nothing heals beyond max health.
Healing reaches group members and other renegades, never players or other mobs. The cooldowns of its support spells are
not shortened by nearby renegades (unlike other attacks). In `[combat]`: `enablePriestHealing` (default `true`; `false`
= the priest only protects and attacks) and `healingMultiplier` (default `1.0`, `0.0`–`5.0`, multiplies all healing by
renegades; the caps still apply). `/renegades nearby` shows what a priest is doing (whom it heals), its cooldowns,
interruptions and the health it has healed so far.

**Magic power:** without other mods, a caster's spell power is its attack damage (incl. level bonuses). With
[Spell Power](https://modrinth.com/mod/spell-power) installed, the spell power of its school from its equipment (e.g.
Wizards robes and staffs, or the healing power of Paladins priest robes and holy wands for the priest) is added, with
critical strikes and the target's resistances; the log line
`Magic backend: vanilla|spell_power` shows which one is active. `/renegades nearby` shows a wizard's variant, backend
and current bolt damage, and a priest's heal power.

**Damage type tags:** the mod puts its own spell damage types into the tags other mods check: `c:is_magic` (e.g.
Dynamic Difficulty's magic damage bonus), `neoforge:is_magic` (e.g. Apotheosis "Magic Damage reduced"),
`minecraft:is_lightning` for `renegades:lightning` (e.g. Apotheosis "Lightning Damage reduced"), plus
`witch_resistant_to` and `panic_causes`. `/renegades damagetags` lists our damage types (and Spell Power's) with these
tags. Spell Power's own schools are only in `c:is_magic`; a modpack that wants Apotheosis' reductions to work against
them (e.g. against players' spells) can add them with a datapack, for example:

```json
// data/neoforge/tags/damage_type/is_magic.json
{ "replace": false, "values": [ { "id": "#spell_power:all", "required": false } ] }
```

```json
// data/minecraft/tags/damage_type/is_lightning.json
{ "replace": false, "values": [ { "id": "spell_power:lightning", "required": false } ] }
```

**Hazards:** the combat movement steers directly instead of using pathfinding. Before every step (walking, strafing,
backing off, circling, attack advances, dashes and rolls) the ground ahead is checked: renegades never step into lava,
fire, magma, cactus, powder snow, sweet berries or water, nor off a drop deeper than 3 blocks. At such an edge they
move only along it and then walk to their goal with normal pathfinding, which finds the way around.

**Elemental Wizard** (requires [Elemental Wizards RPG](https://www.curseforge.com/minecraft/mc-mods/elemental-wizards-rpg),
see "Classes that need other mods" below): a control caster of one element (`aqua`, `terra` or `wind`, set by its
equipment entry or random). It moves like the wizard (circles at about 7.5 blocks, jumps back from close targets, a
staff swing as a last resort) and casts with the power of its element (Spell Power schools `spell_power:water`, `earth`,
`air` from the class gear; damage types `renegades:water|earth|air`). It deals a bit less damage than a wizard and makes
up for it with control and support:

| Element | Spell (caster_visuals key) | Effect |
|---|---|---|
| Aqua | Water ball volley (`water_volley`) | 3 small bolts in a row, power × 0.18 each (blocked by shields) |
| Aqua | Water whip (`water_whip`) | Direct hit up to 12 blocks, × 0.35 and Soaked (4 s) |
| Aqua | Springwater (`springwater`) | Area radius 5 around it: enemies × 0.5 and Soaked; heals itself and its allies there by power × 1.0 (max. 15 % of max health, × `healingMultiplier`) and gives them Cleansing Water. Cast when an enemy is within 5 blocks or it or an ally nearby is below 60 % (cooldown 20 s, 1.5 s windup, interrupted like the priest's heals) |
| Terra | Stone spear (`stone_spear`) | Slow projectile, × 0.8 and Bleed (3 s), pierces up to 3 more targets (blocked by shields) |
| Terra | Impale (`impale`) | Marked on the ground under the target for 1 s, then strikes that spot (radius 1.5): × 0.5 and Impaled (holds in place, 1 s). Only against a target on the ground; step out of the marked circle to dodge it (cooldown 12 s) |
| Terra | Stone skin (`stone_skin`) | When it or an ally nearby is attacked: Stone Flesh for itself and its allies within 5 blocks (10 s, +4 armor, +1 toughness; the next hit at full health is halved; cooldown 25 s) |
| Wind | Air cutter (`air_cutter`) | Fast direct hit up to 16 blocks, × 0.6 and a strong push away |
| Wind | Aeroblast (`aeroblast`) | Against a target within 6 blocks: × 0.4 and throws it about 6 blocks up, with Slow Falling, so no fall damage (cooldown 15 s) |
| Wind | Windfield (`windfield`) | Marked at the target's position, then area radius 4: × 0.3 once and Slowness IV for 5 s (cooldown 18 s) |

The wind wizard jumps back twice as often and also steps aside. Aqua, terra and wind effects (Soaked, Cleansing Water,
Impaled, Stone Flesh) and Bleed are the real ones from Elemental Wizards RPG, mrpg_lib and Spell Engine. Windfield uses
vanilla Slowness instead of the mod's own effect: that effect also pushes targets in the air up every tick, so a player
thrown by Aeroblast would hover until the server kicks him for flying. Support and control spells (springwater, stone
skin, impale, aeroblast, windfield) keep their full cooldown.

**Berserker** (requires [Berserker RPG](https://www.curseforge.com/minecraft/mc-mods/berserker-rpg), see "Classes that
need other mods" below): always presses forward with a two-handed berserker axe and gets more dangerous the more health
it has lost. It never parries, blocks, dodges or retreats (65 HP, attack 7, knockback resistance 0.4). Between its
abilities it swings the axe in quick two-handed strikes. Its attacks are its own (no spells cast), modelled on the spells
of the mod:

| Ability (caster_visuals key) | Effect |
|---|---|
| Rage (`rage`, permanent) | Below 50 % health, rising linearly to 10 %: up to +40 % damage, +25 % movement speed and 20 % shorter attack cooldowns. Red rage particles, more the lower its health. Taken back when healed (`enableBerserkerRage` in `[combat]`, default `true`) |
| Bloody strike (`bloody_strike`) | Heavy overhead blow (attack × 1.3) with Bleed for 5 s. On a hit the berserker hurts itself for 10 % of the damage (never below one heart; then not at all) and gets Blood Sacrifice with 4 absorption (cooldown 5 s) |
| Apprehend (`apprehend`) | Lunges at a target 4–8 blocks away and swings wide (120°, reach 5, × 0.6): targets hit are pulled up to it (never into lava, fire, water or off a drop, never through walls) and get Apprehend (−4 armor) for 6 s (cooldown 12 s) |
| Guillotine (`guillotine`) | Against a target 5–10 blocks away: a roar and a ring on the ground mark the landing spot for 0.75 s, then it leaps there (safe landing, free arc, ±1.5 blocks height, like the paladin's leap) and strikes everything within 2 blocks: × 1.5, +20 % per harmful effect on the target (bleed, apprehend, ...), at most +60 %. Step out of the ring to dodge (cooldown 18 s, `enableGuillotine` in `[combat]`, default `true`) |
| Second wind (`second_wind`, once per life) | Below 30 % health: Blood Reckoning – absorption for missing health (Berserker RPG's config, 60 %, at most 20), and when it ends after 8 s, 40 % of the remaining absorption heals |

The effects (Bleed, Blood Sacrifice, Apprehend, Blood Reckoning) are the real ones from Spell Engine and Berserker RPG.
Berserker RPG also makes berserker axes disable shields they hit, for the berserker too. Its abilities keep their
cooldown (only rage shortens it); the plain swings are shortened like other attacks.

**Bard** (requires [Bards RPG](https://www.curseforge.com/minecraft/mc-mods/bards-rpg), see "Classes that need other
mods" below): a supporter that makes its group stronger and its enemies weaker (the priest heals instead). It keeps
8–14 blocks from its target, preferably behind an ally (alone 11–16 blocks), jumps back from close targets and hits
with its instrument as a last resort (35 HP, attack 3). Variant per bard: **battle** (lute) or **guardian** (lyre).
Its spells use spell power (arcane):

| Ability (caster_visuals key) | Effect |
|---|---|
| Song (`song`, variants `battle`, `guardian`) | When an ally within 10 blocks fights, it stands still and plays for 8 s; every 2 s a pulse around it (radius 10) hurts enemies a little and gives itself and every renegade ally a stack for 9 s (at most 3): battle +10 % attack damage per stack, guardian −10 % damage taken per stack and a small heal (power × 0.3, max. 5 % of max health, × `healingMultiplier`, off with `enablePriestHealing = false`). 4 damage or a melee hit interrupts it (cooldown then halved). Cooldown 20 s, `enableBardSongs` in `[combat]`, default `true` |
| Mockery (`mockery`) | Vicious Mockery on the target (up to 16 blocks) for 6 s: −10 % damage dealt, +10 % damage taken (cooldown 15 s) |
| Crescendo (`crescendo`) | A sound wave (× 0.6) that pierces up to 3 targets, stuns them for 0.5 s and lets them take 10 % more damage for 3 s (cooldown 10 s) |
| Encore (`encore`) | When an enemy is within 4 blocks: a blast (× 0.4) that knocks it away; allies within 8 blocks get their running ability cooldowns shortened by 20 % (cooldown 12 s) |

The effects (Vicious Mockery, Crescendo, the song markers Tale of the Dragonslayer / Troubadour's Minuet) are the real
ones from Bards RPG; the song's bonus is its own, so the markers only show it. Songs only affect renegades, never
players. Its abilities keep their full cooldown.

**Forcemaster** (requires [Forcemaster RPG](https://www.curseforge.com/minecraft/mc-mods/forcemaster-rpg), see
"Classes that need other mods" below): a combo fighter with knuckles in both hands that never stands still – it closes
in fast, circles its target at punching distance and steps in for the next combo (50 HP, attack 5, knockback resistance
0.2). Every technique and every finished combo gives it an **arcane charge** (at most 5, shown as arcane particles at
its fists, more and brighter with every charge); with 5 charges it unloads them in **Asal**. Its attacks are its own
(no spells cast), modelled on the spells of the mod:

| Ability (caster_visuals key) | Effect |
|---|---|
| Fist combo | 2–3 quick jabs left and right (× 0.5 each), then an uppercut (× 0.8) that lifts a target standing on the ground about a block; gives a charge |
| Sonic hand (`sonic_hand`) | Target right in front of it (≤ 2.5 blocks): 5 very fast punches within a second (× 0.3 each) (cooldown 8 s) |
| Belial smashing (`belial_smashing`) | Target 5.5–9 blocks away: a straight rush of up to 9 blocks that hits everything on the way once (× 0.7) and pushes it aside; stops at walls and at the edge of lava, fire or a drop (cooldown 12 s) |
| Nen sphere (`nen_sphere`) | Target 6–14 blocks away (or keeping its distance for 3 s): charges visibly for 1 s – a hit of 4 or more damage stops it (cooldown then halved) – then a fast piercing arcane bolt up to 14 blocks (× 0.7 spell power, magic); a raised shield blocks it from the front (cooldown 10 s) |
| Overflow (`overflow`, charges) | The look shown when it gains a charge |
| Asal (`asal`) | With 5 charges and the target within 4 blocks: a clearly announced windup of 1.1 s (the hit zone in front is marked on the ground), then a huge blow straight ahead (5.5 × 2 blocks, × 3.0) that uses up all charges. Step out of the marked zone to dodge it. Afterwards it is **exhausted** for 2 s: slowed, no attacks, no dodging – the window to strike back (`enableAsal` in `[combat]`, default `true`; `false` = it keeps its charges and never unloads them) |
| Dodge | A side step right after its target strikes at it in melee, about every second strike when free, at most every 7–10 s; never while exhausted |

Fast punches ignore the target's short hurt cooldown, like the casters' bolts. Forcemaster RPG's own effects are not
used: its arcane overflow only raises spell power attributes and shows nothing on a mob.

Combat and magic system based on ANARCHY – RPG Minibosses by cleannrooster (CC0-1.0).

## Classes that need other mods

Some classes mirror classes of other mods and only exist with that mod, because without its gear and spells they would
be a plain copy of another class: the **Elemental Wizard** needs `elemental_wizards_rpg`, the **Berserker** needs
`berserker_rpg`, the **Bard** needs `bards_rpg`, the **Forcemaster** needs `forcemaster_rpg`. Without the mod:

- no natural spawns; spawn groups with the class are left out (`/renegades spawngroup` names the missing mod);
- its spawn egg is not in the creative tab; an egg from `/give` and the commands (`spawnelite`, `reequip`) report
  "… is not available (requires: elemental_wizards_rpg)" instead of spawning it;
- the entity type stays registered (worlds and server/client stay compatible); a renegade of that class saved earlier
  loads with the plain fallback gear of its pools (leather) and logs one line.

Its data (equipment pools, spawn groups, caster_visuals, rare items) uses `neoforge:mod_loaded` conditions. These
classes always use the Spell Engine look from caster_visuals, also with `casterVisuals = "builtin"` (their mods need
Spell Engine; they have no built-in look of their own). A missing or wrong spell id in caster_visuals falls back to the
plain built-in bolt and particles (one log line).

## Dedicated servers: allow-flight

Knockbacks, lifts and pulls of the renegades (and some effects of the RPG mods) can keep a player in the air for a
moment. With `allow-flight=false` in `server.properties`, a dedicated server may then kick the player with "Flying is
not enabled on this server". Renegades logs a warning at server start when `allow-flight` is `false`; setting it to
`true` is recommended.

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
`warrior_archer` (a warrior leading an archer, weight 2), `paladin_archer` (a paladin leading an archer, weight 1),
`wizard_solo` (weight 4), `warrior_wizard` (a warrior leading a wizard, weight 2), `paladin_wizard` (a paladin
leading a wizard, weight 1), `priest_solo` (weight 2), `warrior_priest` (a warrior leading a priest, weight 3),
`paladin_priest` (a paladin leading a priest, weight 2) and `warrior_archer_priest` (a warrior leading a priest and an
archer, weight 1; with `maxNearby` 3 it only spawns complete when no other renegade is near, otherwise the archer is
left out first). With Elemental Wizards RPG also `elemental_wizard_solo` (weight 3), `warrior_elemental_wizard`
(weight 2) and `paladin_elemental_wizard` (weight 1), so the elemental wizard is about as common as the wizard; the
spawn timer still makes one attempt per interval, so the total spawn rate stays the same. With Berserker RPG also
`berserker_solo` (weight 4), `berserker_pair` (two berserkers, weight 1), `warrior_berserker` (weight 2) and
`berserker_priest` (a berserker leading a priest, weight 1): 8 of 62 weight, about as often as the wizard (7). With
Bards RPG also `bard_solo` (weight 1), `warrior_bard` (a warrior leading a bard, weight 3), `paladin_bard` (weight 2)
and, with Berserker RPG too, `berserker_bard` (weight 1): 7 of 69 weight; as a supporter the bard rarely spawns alone.
With Forcemaster RPG also `forcemaster_solo` (weight 4), `forcemaster_pair` (two forcemasters, weight 1) and
`forcemaster_priest` (a forcemaster leading a priest, weight 1): 6 of 75 weight, a bit rarer than the berserker.

**Debug commands**: `/renegades groups <leader>` lists the templates, `/renegades spawngroup <id>` spawns one at your
position like a natural spawn, `/renegades nearby` lists the renegades within `capRadius` with group, spawn type,
persistence, health, attack damage, invisibility and the combat state, `/renegades stats [reset]` shows the spawn timer's attempts, failures by reason
(incl. positions in a MineColonies colony) and spawns since server start (extrapolated per hour), `/renegades profile start|stop` measures the time the renegade
AI takes per server tick (entity tick, brain, line of sight, decisions, attacks, hazard and path checks).

### Drop rules

Configured in the server config `config/renegades-server.toml`. To override it for a single world, put a copy into
`<world>/serverconfig/renegades-server.toml`.

| Option | Default | Effect |
|---|---|---|
| `armorDropChance` | `1.0` | Chance for each armor piece to drop. |
| `weaponDropChance` | `1.0` | Chance for each held item (main and off hand) to drop. |
| `requirePlayerKill` | `true` | Equipment only drops if a player killed the mob or hit it shortly before. |
| `dropDurabilityMin` / `dropDurabilityMax` | `0.25` / `0.75` | Each dropped item with durability keeps a random share of its max durability in this range (rolled per item; wear from the fight does not matter). Items without durability or unbreakable items are unchanged; nothing drops broken. `1.0` / `1.0` drops everything undamaged. |
| `rareItemChanceMultiplier` | `1.0` | Multiplies the chance of every rare item (see below). `0` = no rare items, `100` = for testing. |

Items with Curse of Vanishing (or any `prevent_equipment_drop` effect) never drop; Looting raises the chance like for
vanilla mob equipment. The regular loot tables (`renegades:entities/<class>`) are empty by default and can be filled
by datapacks.

## Rare items (datapack)

Very rarely, a renegade wears a special item (e.g. a unique weapon) instead of one piece of its pool equipment. It is
rolled right after the pool set on spawn (all spawn types) and on `/renegades reequip`, and drops like the rest of the
equipment (drop rules above). One entry per file in `data/<namespace>/renegades/rare_items/<name>.json`; a file with
the same path in a later datapack replaces it (e.g. `"chance": 0.0` switches an entry off). `neoforge:conditions` are
supported; items of mods that are not installed are left out, and an entry without any item left is dropped.

```json
{
  "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "arsenal"}],
  "entity": "renegades:rogue",
  "slot": "mainhand",
  "chance": 0.00125,
  "items": ["arsenal:unique_dagger_1", "arsenal:unique_dagger_2", "arsenal:unique_dagger_sw"],
  "replaces": "#renegades:rare_replace/dagger_t2_t3",
  "conditions": {"dimensions": ["minecraft:overworld"]},
  "allow_affixes": true
}
```

- `entity`: one id or a list. `slot`: `mainhand`, `offhand`, `head`, `chest`, `legs` or `feet`.
- `chance`: per spawn, multiplied by `rareItemChanceMultiplier`. Every matching entry is rolled once; at most one rare
  item per slot. Empty slots are never filled.
- `items`: an item, `#tag`, list or object like in the equipment pools (`item`, `enchantments`, `enchant_with_levels`).
- `replaces` (optional): only replaces a worn piece that matches (item, list or `#tag`); without it, any worn piece in
  the slot. This keeps the class working (a unique claymore only replaces a claymore, never the mace of the shield
  set) and ties the rarity to the pool tier.
- `conditions` (optional): like in the equipment pools.
- `allow_affixes` (optional, default `true`): with Apotheosis, rare items get affixes like any other piece of an elite;
  `false` = never (an augment then upgrades another piece).

Included (only with Arsenal from the RPG Series and the class mod; for the Elemental Wizard instead with the mod that
the class mod needs for the staff, see the table):
the uniques replace the tier 2-3 weapon of the same type (tags `renegades:rare_replace/*`), so about 1 in 400
renegades with tier 2-3 gear carries one:

| Class | Unique | Replaces | Chance per entry |
|---|---|---|---|
| Warrior | double axe, glaive | diamond/netherite double axe, glaive | 0.25 % |
| Rogue | dagger (main and off hand), sickle | diamond/netherite dagger, sickle | 0.125 % (two rolls per rogue) |
| Paladin | claymore, hammer | diamond/netherite claymore, great hammer | 0.25 % |
| Paladin | mace, shield | diamond/netherite mace, kite shield | 0.125 % (two rolls) |
| Archer | longbow, heavy crossbow | royal/netherite longbow, (netherite) heavy crossbow | 0.5 % (short bows and rapid crossbows have no unique) |
| Wizard | damage staffs with the variant's school | fire/frost/arcane staff (not wands) | 0.5 % |
| Priest | healing staffs | diamond/netherite holy staff (not wands) | 0.5 % |
| Elemental Wizard | Crystal Aqua / Ruby Terra / Aeternium Wind staff of its element (Elemental Wizards RPG registers them only with Better End / Better Nether / Better End), or the Valkyrie Elementalist Staff (only with Aether) | aqua/terra/wind staff of tier 2-3 (not wands) | 0.5 % |
| Berserker | unique berserker axes 1 and 2 (Berserker RPG, need Arsenal too) | diamond/netherite berserker axe | 0.25 % |
| Bard | unique lutes (battle) and lyres (guardian) (Bards RPG, need Arsenal too) | diamond/netherite lute or lyre | 0.5 % |
| Forcemaster | unique knuckles 1 and 2 (Forcemaster RPG, need Arsenal too), main hand only | diamond/netherite knuckle | 0.25 % |

**Debug command**: `/renegades rareitems` lists the loaded entries per class (slot, chance, items, replaces).

## Elite renegades (Apotheosis)

With [Apotheosis](https://www.curseforge.com/minecraft/mc-mods/apotheosis) installed, renegades can be elites. The
integration is optional; nothing from Apotheosis is bundled.

- **Augment**: Apotheosis' augment `random_affix_items` (`data/apotheosis/apothic_augments`, 12 % of monster spawns,
  not from (trial) spawners) gives a monster a random affix item. A renegade keeps its class gear instead: for each such
  item one of its own pieces gets affixes and that item's rarity (the piece in the same slot, otherwise another free
  piece). How often this happens is set through Apotheosis' own augment data; packs that add more augments with
  `random_affix_item` get elites with several upgraded pieces.
- **Elite set**: with `fullSetEliteChance` (see below) all worn pieces that can take affixes get affixes, all with the
  same rarity, rolled for the world tier like Apotheosis' own loot. It replaces the augment for that renegade, so no
  piece is upgraded twice. Elite sets are saved as `RenegadesElite.Set` in the entity NBT.
- **Name**: an elite shows its rarity as a title, the whole name in the rarity's color: "Rare Warrior Renegade", with an
  elite set "Rare Elite Warrior Renegade" (best rarity if several pieces were upgraded). The wording comes from the
  language keys `entity.renegades.elite` and `entity.renegades.elite_set`; the rarity name from Apotheosis' `rarity.<id>`
  keys. It is not a custom name, so elites despawn like other renegades and a name tag still wins.
- **Drops**: upgraded pieces drop by the normal drop rules (chance, `requirePlayerKill`, 25-75 % durability) and keep
  their affixes and rarity.
- **Affix effects**: attribute affixes work for renegades like for players. Hit affixes trigger on melee hits, arrows and
  thrown knives; projectile affixes on arrows (and on knives from the off-hand dagger). Spells do not trigger weapon
  affixes.

| Option (section `[elite]`) | Default | Effect |
|---|---|---|
| `apotheosisEliteGear` | `true` | Augment items upgrade the renegade's own pieces. If false, Apotheosis has no effect on renegade gear (and there are no elite sets). |
| `fullSetEliteChance` | `0.0` | Chance per spawn (not from (trial) spawners, only with a player in the world) for an elite set. E.g. `0.02` = 2 %. |

**Debug command**: `/renegades spawnelite <class> <rarity> [set]` spawns a renegade like a spawn egg and makes it an
elite with that rarity (one piece, or with `set` all pieces). `/renegades reequip` rolls new gear and removes the elite
state.

## Spell looks (Spell Engine, datapack)

With [Spell Engine](https://www.curseforge.com/minecraft/mc-mods/spell-engine) installed, the casters' spells can use
the look of a Spell Engine spell: its projectile model, travel/release/impact particles and sounds. Only the look
changes; damage, healing, targets, flight paths, homing, areas, shield blocking and cooldowns stay exactly the same.
Without Spell Engine (or without an assigned spell) the built-in look is used. Nothing from Spell Engine is bundled.

| Option (section `[visuals]`) | Default | Effect |
|---|---|---|
| `casterVisuals` | `"auto"` | `"auto"`: spell looks where a spell is assigned and exists, otherwise the built-in look. `"builtin"`: always the built-in look (only for the six original classes; classes of other mods such as the Elemental Wizard always use the spell look). Applies to new spells right away. |

Which spell's look an ability uses comes from `data/<namespace>/renegades/caster_visuals/<name>.json`. Files for the
same entity are merged per ability; a file with the same path in a later datapack replaces it. `neoforge:conditions`
are supported. A spell id that does not exist falls back to the built-in look (one warning in the log).

```json
{
  "neoforge:conditions": [
    {"type": "neoforge:mod_loaded", "modid": "spell_engine"},
    {"type": "neoforge:mod_loaded", "modid": "wizards"}
  ],
  "entity": "renegades:wizard",
  "abilities": {
    "double_bolt": {"variants": {"fire": "wizards:fireball", "frost": "wizards:frostbolt", "arcane": "wizards:arcane_bolt"}},
    "nova": {"variants": {"fire": {"spell": "wizards:fire_breath", "model": false}, "frost": "wizards:frost_nova"}}
  }
}
```

- `entity`: one id or a list.
- `abilities`: ability key → spell id, or an object with `spell` and/or `variants` (variant id → spell id or object).
  A variant without its own entry uses `spell`.
- `model`, `particles`, `sounds` (optional, default `true`, at ability or variant level): `false` keeps the built-in
  model, particles or sounds for that part. The telegraph ring before an area spell is always shown.

Ability keys: wizard `double_bolt`, `homing_bolts`, `nova` (variants `fire`, `frost`, `arcane`); priest `heal`,
`healing_cloud`, `absorption`, `holy_bolt`; elemental wizard `water_volley`, `water_whip`, `springwater`, `stone_spear`,
`impale`, `stone_skin`, `air_cutter`, `aeroblast`, `windfield` (variants `aqua`, `terra`, `wind`); berserker `rage`,
`bloody_strike`, `apprehend`, `guillotine`, `second_wind` (particles and sounds only); bard `song` (variants `battle`,
`guardian`), `mockery`, `crescendo`, `encore`; forcemaster `sonic_hand`, `belial_smashing`, `nen_sphere`, `asal`,
`overflow`. The staff swing keeps
the built-in look.

Included (only with Spell Engine and the class mod): wizard spells from Wizards (fire, frost, arcane), priest spells
from Paladins & Priests (heal, circle of healing, divine protection, penance), elemental wizard spells from Elemental
Wizards RPG (waterball, water whip, springwater, stone spear, impale, stone flesh, air cutter, aeroblast, windfield),
berserker looks from Berserker RPG (wild rage, bloody strike, apprehend, northerner's guillotine, blood reckoning),
bard spells from Bards RPG (wanderer's minuet, nature's minne, vicious mockery, crescendo, encore), forcemaster looks
from Forcemaster RPG (sonic hand, belial smashing, nen sphere, asal, arcane overflow).

**Debug command**: `/renegades castervisuals` shows the mode and the assigned spells per class and ability, and whether
each spell exists.