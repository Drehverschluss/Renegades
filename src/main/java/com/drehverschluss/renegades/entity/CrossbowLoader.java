package com.drehverschluss.renegades.entity;

import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.ChargedProjectiles;

/**
 * Loads a crossbow for a mob without the charging time, through vanilla's own ammo logic (ProjectileWeaponItem#draw),
 * so Multishot loads its extra arrows exactly like for players and pillagers. Never instantiated; it only extends
 * ProjectileWeaponItem to reach the protected helper.
 */
final class CrossbowLoader extends ProjectileWeaponItem {
    private CrossbowLoader() {
        super(new Properties());
    }

    static boolean load(LivingEntity shooter, ItemStack crossbow) {
        List<ItemStack> projectiles = draw(crossbow, shooter.getProjectile(crossbow), shooter);
        if (projectiles.isEmpty()) {
            return false;
        }
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(projectiles));
        return true;
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return stack -> false;
    }

    @Override
    public int getDefaultProjectileRange() {
        return 0;
    }

    @Override
    protected void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float velocity, float inaccuracy, float angle,
                                   @Nullable LivingEntity target) {
    }
}
