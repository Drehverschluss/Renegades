// Based on Relations from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: renegades use their own hit rules (RenegadeEntity#canHarm); renegades help each other.
package com.drehverschluss.renegades.magic;

import com.drehverschluss.renegades.entity.RenegadeEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Who a spell may harm or help.
 */
public final class Relations {
    private Relations() {
    }

    /**
     * Renegades follow the same rules as their melee hits: only their target, players and mobs fighting them; never
     * allies, other renegades (unless targeted) or bystanders.
     */
    public static boolean canHarm(LivingEntity caster, Entity target) {
        if (caster instanceof RenegadeEntity renegade) {
            return renegade.canHarm(target);
        }
        if (!(target instanceof LivingEntity living) || target == caster || !living.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player player && player.isCreative() || caster.isAlliedTo(target)) {
            return false;
        }
        return !(caster instanceof TraceableEntity ownable && ownable.getOwner() == target);
    }

    /**
     * The caster itself, its allies and (for renegades) other renegades that are not fighting it.
     */
    public static boolean canHelp(LivingEntity caster, Entity target) {
        if (!(target instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        if (target == caster || caster.isAlliedTo(target)) {
            return true;
        }
        return caster instanceof RenegadeEntity && target instanceof RenegadeEntity other && other.getTarget() != caster;
    }
}
