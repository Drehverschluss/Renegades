package com.drehverschluss.renegades.entity.ai;

import com.drehverschluss.renegades.entity.RenegadeEntity;
import com.drehverschluss.renegades.group.RenegadeGroups;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Keeps group members together: without a target, a member walks back to its group leader once it is too far away.
 */
public class FollowGroupLeaderGoal extends Goal {
    private static final int SEARCH_INTERVAL = 20;

    private final RenegadeEntity mob;
    private final double speedModifier;
    private final double startDistanceSqr;
    private final double stopDistanceSqr;
    @Nullable
    private RenegadeEntity leader;
    private int searchCooldown;
    private int repathCooldown;

    public FollowGroupLeaderGoal(RenegadeEntity mob, double speedModifier, double startDistance, double stopDistance) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.startDistanceSqr = startDistance * startDistance;
        this.stopDistanceSqr = stopDistance * stopDistance;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.mob.getGroupId() == null || this.mob.getTarget() != null || --this.searchCooldown > 0) {
            return false;
        }
        this.searchCooldown = this.adjustedTickDelay(SEARCH_INTERVAL);

        RenegadeEntity groupLeader = RenegadeGroups.leaderOf(this.mob);
        if (groupLeader == this.mob || this.mob.distanceToSqr(groupLeader) <= this.startDistanceSqr) {
            return false;
        }
        this.leader = groupLeader;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.leader != null && this.leader.isAlive() && this.mob.getTarget() == null
                && this.mob.distanceToSqr(this.leader) > this.stopDistanceSqr
                && this.mob.distanceToSqr(this.leader) <= RenegadeGroups.GROUP_RANGE * RenegadeGroups.GROUP_RANGE;
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
    }

    @Override
    public void tick() {
        if (this.leader != null && --this.repathCooldown <= 0) {
            this.repathCooldown = this.adjustedTickDelay(10);
            this.mob.getNavigation().moveTo(this.leader, this.speedModifier);
        }
    }

    @Override
    public void stop() {
        this.leader = null;
        this.mob.getNavigation().stop();
    }
}
