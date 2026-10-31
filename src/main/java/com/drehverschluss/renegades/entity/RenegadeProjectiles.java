package com.drehverschluss.renegades.entity;

import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/**
 * Projectiles of renegades (arrows, bolts) follow the same target rules as their melee hits: they fly through allies,
 * other renegades and bystanders instead of hitting them.
 */
public final class RenegadeProjectiles {
    private RenegadeProjectiles() {
    }

    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit
                && event.getProjectile().getOwner() instanceof RenegadeEntity shooter
                && !shooter.canHarm(hit.getEntity())) {
            event.setCanceled(true);
        }
    }
}
