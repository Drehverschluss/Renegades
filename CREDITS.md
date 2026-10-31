# Credits

## Model and animations

Model and animations based on [ANARCHY - RPG Minibosses](https://github.com/cleannrooster/rpg-minibosses) by cleannrooster (CC0-1.0).

- `assets/renegades/geo/entity/renegade.geo.json`: derived from the Templar model (bone structure, hierarchy and pivots kept; cubes reduced to player proportions).
- `assets/renegades/animations/entity/renegade.animation.json`: taken from `mobs_v2.animations.json`.

## Combat system

Combat system based on [ANARCHY - RPG Minibosses](https://github.com/cleannrooster/rpg-minibosses) by cleannrooster (CC0-1.0).

- `com.drehverschluss.renegades.combat` (actions, action runner, swing execution, ground locomotion, attack geometry) and
  `combat.brain` (mob brain, brain goal, stimulus): ported from `entity.combat` and `entity.brain`, adapted to GeckoLib.
- `WarriorBrain`: based on `TemplarBrain`.
- `RogueBrain`: based on `RogueBrain` (knife throw as an own projectile instead of the ability system).
- `RogueNavigation`: based on `TricksterEntity$TricksterNavigation` and `RogueNodeMaker`.
- `PaladinBrain`: based on `JuggernautBrain` (ground impact as an area strike instead of the ability system; shield bracing added).
- `ArcherBrain`: based on `MercenaryBrain` (shots with the held bow or crossbow, traps as an own entity instead of the ability system).
- `WizardBrain`: based on `FireMageBrain` (spells per school variant; nova can be switched off).

## Magic system

Magic system based on [ANARCHY - RPG Minibosses](https://github.com/cleannrooster/rpg-minibosses) by cleannrooster (CC0-1.0).

- `com.drehverschluss.renegades.magic` (ability specs, deliveries, impacts, area shapes, ability executor, tick scheduler,
  magic projectile and cloud): ported from ANARCHY's magic package, without the ability registry, debug command, AtlasVFX
  cues and boss-only systems; effects use vanilla particles and sounds.
- `SpellPowerMagicBackend`: based on ANARCHY's Spell Power backend.

## Trap model

- `assets/renegades/geo/entity/trap.geo.json`, `animations/entity/trap.animation.json` and `textures/entity/trap.png`:
  the trap model, animation and texture of [ANARCHY - RPG Minibosses](https://github.com/cleannrooster/rpg-minibosses)
  by cleannrooster (CC0-1.0).

## Optional integrations

- [Armor Model API](https://modrinth.com/mod/armor-model-api) by Daedelus (MIT): optional; when installed, armor that uses it (e.g. RPG Series armor) is rendered on renegades through its API. Not bundled.
- [Spell Power](https://modrinth.com/mod/spell-power) by Daedelus (GPL-3.0): optional; when installed, wizard spells use its spell power attributes, critical strikes and damage sources. Not bundled.
- [MineColonies](https://www.curseforge.com/minecraft/mc-mods/minecolonies) by the LDT Team (GPL-3.0): optional; when installed, renegades do not spawn naturally inside colonies (noSpawnInColonies). Only compiled against its API, not bundled.
