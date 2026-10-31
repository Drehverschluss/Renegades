// Based on RogueNodeMaker and TricksterEntity$TricksterNavigation from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: works on RogueRenegade; uses the mob's random source instead of Math.random.
package com.drehverschluss.renegades.entity.ai;

import com.drehverschluss.renegades.combat.brain.RogueBrain;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Ground navigation of the rogue. Its paths can pass doors; while withdrawing they never lead closer to the target,
 * and where rough ground (steps, slopes, penalised blocks) is next to flat ground, the rogue prefers the rough route
 * so it moves less predictably.
 */
public class RogueNavigation extends GroundPathNavigation {
    public RogueNavigation(Mob mob) {
        super(mob, mob.level());
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new RogueNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    private static class RogueNodeEvaluator extends WalkNodeEvaluator {
        // Steps costing at least this much (distance plus block penalty) count as rough ground.
        private static final float ROUGH_COST_THRESHOLD = 1.05F;
        // Chance to drop a flat neighbour when rough ones are available.
        private static final float FLAT_DROP_CHANCE = 0.55F;
        // A withdrawing rogue skips neighbours that are this much closer (squared blocks) to the target.
        private static final double WITHDRAW_TOLERANCE = 0.5;

        @Override
        public int getNeighbors(Node[] neighbors, Node current) {
            int count = super.getNeighbors(neighbors, current);
            if (count == 0) {
                return 0;
            }

            LivingEntity target = this.mob.getTarget();
            boolean withdrawing = target != null && this.isWithdrawing();
            double currentToTarget = withdrawing ? sqDistToTarget(current, target) : 0.0;
            int roughCount = 0;
            int valid = 0;

            for (int i = 0; i < count; i++) {
                Node node = neighbors[i];
                if (withdrawing && sqDistToTarget(node, target) < currentToTarget - WITHDRAW_TOLERANCE) {
                    continue;
                }
                neighbors[valid++] = node;
                if (stepCost(current, node) >= ROUGH_COST_THRESHOLD) {
                    roughCount++;
                }
            }

            if (roughCount > 0) {
                int biasedValid = 0;
                for (int i = 0; i < valid; i++) {
                    Node node = neighbors[i];
                    boolean flat = stepCost(current, node) < ROUGH_COST_THRESHOLD;
                    if (!flat || this.mob.getRandom().nextFloat() >= FLAT_DROP_CHANCE) {
                        neighbors[biasedValid++] = node;
                    }
                }
                valid = biasedValid > 0 ? biasedValid : valid;
            }

            return valid > 0 ? valid : count;
        }

        private boolean isWithdrawing() {
            return this.mob instanceof RenegadeEntity renegade
                    && renegade.getCombatBrain() instanceof RogueBrain brain
                    && brain.getCurrentCombatState() == RogueBrain.CombatState.WITHDRAWING;
        }

        private static float stepCost(Node from, Node to) {
            return from.distanceTo(to) + to.costMalus;
        }

        private static double sqDistToTarget(Node node, LivingEntity target) {
            double dx = node.x - target.getX();
            double dz = node.z - target.getZ();
            return dx * dx + dz * dz;
        }
    }
}
