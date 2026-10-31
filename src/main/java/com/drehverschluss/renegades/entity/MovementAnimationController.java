package com.drehverschluss.renegades.entity;

import java.util.Map;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Controller for the movement animations (idle, walk and the combat movement), with its own time per renegade:
 * <ul>
 * <li>the time comes from the renegade's own tick (plus partial tick), not from the model-wide animation time that
 * GeckoLib shares between all renegades of a model;</li>
 * <li>the tempo only scales the time since the last frame, so a changing walk tempo does not make the animation jump
 * (GeckoLib multiplies the tempo with the whole time since the animation started);</li>
 * <li>an idle or walk animation that starts out of combat begins at a fixed phase from the renegade's UUID, so
 * renegades that start together (e.g. loaded at the same time) do not move in step. Loops then wrap seamlessly;
 * combat animations always start at 0.</li>
 * </ul>
 * Transitions (transitionLength) and loop restarts work as in GeckoLib: they still reset through shouldResetTick.
 */
public class MovementAnimationController<T extends RenegadeEntity> extends AnimationController<T> {
    private double elapsed;
    private double lastTime = Double.NaN;
    private boolean wasTransitioning;

    public MovementAnimationController(T animatable, String name, int transitionTickTime, AnimationStateHandler<T> animationHandler) {
        super(animatable, name, transitionTickTime, animationHandler);
    }

    @Override
    public void process(GeoModel<T> model, AnimationState<T> state, Map<String, GeoBone> bones, Map<String, BoneSnapshot> snapshots, double seekTime,
                        boolean crashWhenCantFindBone) {
        this.wasTransitioning = this.animationState == State.TRANSITIONING;
        super.process(model, state, bones, snapshots, this.animatable.tickCount + state.getPartialTick(), crashWhenCantFindBone);
    }

    @Override
    protected double adjustTick(double tick) {
        if (this.shouldResetTick) {
            if (getAnimationState() != State.STOPPED) {
                this.tickOffset = tick;
            }
            this.shouldResetTick = false;
            this.lastTime = tick;
            // Only the start of an animation after its transition gets the phase, not a loop restart.
            boolean startsRunning = this.wasTransitioning && this.animationState == State.RUNNING && !this.animatable.isAggressive();
            this.wasTransitioning = false;
            this.elapsed = startsRunning ? this.animatable.animationPhaseTicks() : 0.0D;
            return this.elapsed;
        }

        double delta = Double.isNaN(this.lastTime) ? 0.0D : Math.max(0.0D, tick - this.lastTime);
        this.lastTime = tick;
        this.elapsed += this.animationSpeedModifier.apply(this.animatable) * delta;
        return this.elapsed;
    }
}
