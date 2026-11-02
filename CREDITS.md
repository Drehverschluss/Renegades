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

## New classes on the ported systems

- `PriestBrain`, `ElementalWizardBrain`, `BerserkerBrain`, `BardBrain` and `ForcemasterBrain` are new; they are built on
  the ported combat and magic system (movement of the casters follows `WizardBrain`). Their abilities are modelled on
  the spells of the class mods below, but use none of their code or assets.

No sounds, textures or other assets of ANARCHY are used apart from the files listed above; all sounds are vanilla
Minecraft sounds.

## Class mods (not bundled)

The classes mirror the classes of these mods. Renegades contains nothing from them – no code, textures, models, sounds
or translations. Its data and code only refer to their registry ids (items, status effects, spells, spell schools, mod
ids in `neoforge:mod_loaded` conditions), so that renegades can wear their gear and show their spell effects when the
mods are installed:

- Wizards, Paladins & Priests, Rogues & Warriors, Archers, Arsenal by Daedelus (All Rights Reserved)
- Archers Expansion, Elemental Wizards RPG, Berserker RPG, Bards RPG, Forcemaster RPG by Fichte (ARR)
- More RPG Library (mrpg_lib) by Fichte (MIT): spell schools and a status effect, by id

Some unique-item entries also check the mod ids of Better End, Better Nether and Aether (the Elemental Wizards RPG
registers its unique staffs only with them); nothing else of these mods is used.

## Optional integrations

- [GeckoLib](https://github.com/bernie-g/geckolib) (MIT): required; model and animation rendering. Not bundled.
- [Armor Model API](https://modrinth.com/mod/armor-model-api) by Daedelus (MIT): optional; when installed, armor that uses it (e.g. RPG Series armor) is rendered on renegades through its API. Not bundled.
- [Spell Power](https://modrinth.com/mod/spell-power) by Daedelus (GPL-3.0): optional; when installed, the casters' spells use its spell power attributes, critical strikes and damage sources. Not bundled.
- [Spell Engine](https://modrinth.com/mod/spell-engine) by Daedelus (GPL-3.0): optional; when installed, abilities can show the particles, sounds and projectile models of Spell Engine spells (caster_visuals). Only compiled against it, not bundled.
- [Apotheosis](https://www.curseforge.com/minecraft/mc-mods/apotheosis) by Shadows_of_Fire (MIT code / All Rights Reserved assets), with [Placebo](https://www.curseforge.com/minecraft/mc-mods/placebo) and [Apothic Attributes](https://www.curseforge.com/minecraft/mc-mods/apothic-attributes) (MIT): optional; when installed, elite renegades get affixed gear. Only compiled against their API, not bundled.
- [Dynamic Difficulty](https://github.com/muon-rw/Dynamic-Difficulty) by MuonR (ARR): optional; when installed, its mob level is available for equipment pool conditions. Only compiled against it, not bundled.
- [MineColonies](https://www.curseforge.com/minecraft/mc-mods/minecolonies) by the LDT Team (GPL-3.0): optional; when installed, renegades do not spawn naturally inside colonies (noSpawnInColonies). Only compiled against its API, not bundled.
