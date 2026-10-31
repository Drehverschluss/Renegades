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
- `PaladinBrain`: based on `JuggernautBrain` (ground impact as a radial strike instead of the ability system; shield bracing added).

## Optional integrations

- [Armor Model API](https://modrinth.com/mod/armor-model-api) by Daedelus (MIT): optional; when installed, armor that uses it (e.g. RPG Series armor) is rendered on renegades through its API. Not bundled.