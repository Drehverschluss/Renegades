// Ported from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
package com.drehverschluss.renegades.combat;

import com.drehverschluss.renegades.entity.RenegadeEntity;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class CombatDebug {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static boolean enabled = false;
    private static final double RUNAWAY_SPEED = 1.2;
    private static final int MAX_OWNERSHIP_TICKS = 200;
    private static final Map<Integer, Integer> OWNERSHIP_TICKS = new HashMap<>();
    private static final Map<String, Boolean> REPORTED = new HashMap<>();

    private CombatDebug() {
    }

    public static void verify(RenegadeEntity entity, GroundLocomotion locomotion, ActionRunner runner) {
        if (enabled && !entity.level().isClientSide()) {
            int id = entity.getId();
            if (locomotion.owner() == MovementOwner.ATTACK_MOTION) {
                Integer ticks = OWNERSHIP_TICKS.merge(id, 1, Integer::sum);
                if (ticks > 200 && !runner.isBusy()) {
                    report(entity, "stuck-ownership", "held ATTACK_MOTION for " + ticks + " ticks with no action running");
                }
            } else {
                OWNERSHIP_TICKS.remove(id);
            }

            double speed = locomotion.speed();
            if (speed > 1.2) {
                report(entity, "runaway-speed", "steering reached " + String.format("%.2f", speed) + " b/t");
            }

            if (Double.isNaN(speed)) {
                report(entity, "nan-steering", "steering velocity became NaN");
            }

            Vec3 velocity = entity.getDeltaMovement();
            if (Double.isNaN(velocity.x) || Double.isNaN(velocity.z)) {
                report(entity, "nan-velocity", "entity velocity became NaN");
            }
        }
    }

    public static void verifyAction(CombatAction action) {
        if (enabled) {
            if (action.activeTicks() <= 0 && action.advance() != 0.0) {
                report("action:" + action.id(), action.id() + " has advance but no active window");
            }

            if (action.motion().isCommitted() && action.committedSpeed() <= 0.0) {
                report("action:" + action.id(), action.id() + " is committed but has zero committed speed");
            }

            if (action.windupTicks() <= 0 && action.motion() != AttackMotion.MOBILE) {
                report("action:" + action.id(), action.id() + " commits with no telegraph");
            }
        }
    }

    public static boolean isSaneDestination(RenegadeEntity entity, Vec3 destination) {
        Vec3 delta = destination.subtract(entity.position());
        return delta.horizontalDistance() < 32.0 && Math.abs(delta.y) < 16.0;
    }

    private static void report(RenegadeEntity entity, String kind, String message) {
        report(entity.getType().toShortString() + ":" + kind, message);
    }

    private static void report(String key, String message) {
        if (REPORTED.putIfAbsent(key, Boolean.TRUE) == null) {
            LOGGER.warn("[renegades combat] {}", message);
        }
    }
}
