package com.drehverschluss.renegades.group;

import com.drehverschluss.renegades.config.RenegadesConfig;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Runtime queries for renegade groups (a shared group id). Also the entry point for support classes that act on their
 * group, e.g. a healer looking for wounded members.
 */
public final class RenegadeGroups {
    /**
     * Radius in which group members are considered together (leader lookup, following).
     */
    public static final double GROUP_RANGE = 48.0D;
    public static final double ALERT_RANGE = 32.0D;

    private RenegadeGroups() {
    }

    /**
     * Other living members of the mob's group within the radius; empty if the mob has no group.
     */
    public static List<RenegadeEntity> membersNearby(RenegadeEntity self, double radius) {
        UUID groupId = self.getGroupId();
        if (groupId == null) {
            return List.of();
        }
        return self.level().getEntitiesOfClass(RenegadeEntity.class, self.getBoundingBox().inflate(radius),
                other -> other != self && other.isAlive() && groupId.equals(other.getGroupId()) && other.distanceToSqr(self) <= radius * radius);
    }

    /**
     * The member the group gathers around: the one with the lowest UUID among the loaded members in range. If the
     * leader dies or is unloaded, the next member automatically takes over.
     */
    public static RenegadeEntity leaderOf(RenegadeEntity self) {
        return membersNearby(self, GROUP_RANGE).stream()
                .filter(other -> other.getUUID().compareTo(self.getUUID()) < 0)
                .min(Comparator.comparing(RenegadeEntity::getUUID))
                .orElse(self);
    }

    /**
     * Makes the group members around the attacked mob target the attacker.
     */
    public static void alertGroup(RenegadeEntity attacked, LivingEntity attacker) {
        for (RenegadeEntity member : membersNearby(attacked, ALERT_RANGE)) {
            if (member.getTarget() != attacker && member.canAttack(attacker)) {
                member.setTarget(attacker);
            }
        }
    }

    /**
     * Number of renegades (all classes) within the radius.
     */
    public static int countRenegades(Level level, Vec3 center, double radius) {
        return level.getEntitiesOfClass(RenegadeEntity.class, new AABB(BlockPos.containing(center)).inflate(radius),
                entity -> entity.isAlive() && entity.distanceToSqr(center) <= radius * radius).size();
    }

    /**
     * How many more renegades may spawn at this position under the maxNearby cap. Counted within capRadius around the
     * position and around the nearest player, since persistent renegades would otherwise pile up around players over time.
     */
    public static int remainingCapacity(Level level, Vec3 pos) {
        double radius = RenegadesConfig.CAP_RADIUS.get();
        int nearby = countRenegades(level, pos, radius);
        Player player = level.getNearestPlayer(pos.x, pos.y, pos.z, -1.0D, false);
        if (player != null) {
            nearby = Math.max(nearby, countRenegades(level, player.position(), radius));
        }
        return RenegadesConfig.MAX_NEARBY.get() - nearby;
    }
}
