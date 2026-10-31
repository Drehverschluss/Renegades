// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat.brain;

import com.drehverschluss.renegades.entity.RenegadeEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;

public final class MobBrainGoal extends Goal {
    private final RenegadeEntity entity;
    private final MobBrain brain;

    public MobBrainGoal(RenegadeEntity entity, MobBrain brain) {
        this.entity = entity;
        this.brain = brain;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public void start() {
        this.entity.setAggressive(true);
        super.start();
    }

    public boolean canUse() {
        return this.hasLiveTarget() && !this.entity.level().isClientSide();
    }

    public boolean canContinueToUse() {
        return this.hasLiveTarget();
    }

    private boolean hasLiveTarget() {
        LivingEntity target = this.entity.getTarget();
        return target != null && target.isAlive();
    }

    public boolean requiresUpdateEveryTick() {
        return true;
    }

    public void tick() {
        this.brain.tick();
    }

    public void stop() {
        this.entity.setAggressive(false);
        this.brain.onTargetLost();
    }
}
