// Based on AbilitySpec from ANARCHY - RPG Minibosses by cleannrooster (CC0-1.0).
// Changes: no global ability registry and no telegraph colours (AtlasVFX); sounds default to MagicSounds.
package com.drehverschluss.renegades.magic;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * A spell: its school, whether it harms enemies or helps allies, how it is delivered, what it does to its targets
 * (and optionally to allies), and how it looks and sounds.
 */
public record AbilitySpec(ResourceLocation id, MagicSchool school, Intent intent, Delivery delivery, List<Impact> impacts, List<Impact> allyImpacts,
                          Presentation presentation) {
    public AbilitySpec {
        impacts = List.copyOf(impacts);
        allyImpacts = List.copyOf(allyImpacts);
    }

    /**
     * School used for particles and sounds.
     */
    public MagicSchool lookSchool() {
        return this.presentation.look() != null ? this.presentation.look() : this.school;
    }

    public static Builder builder(ResourceLocation id, MagicSchool school) {
        return new Builder(id, school);
    }

    public enum Intent {
        HARMFUL,
        HELPFUL
    }

    /**
     * @param windupSound  null = the school's windup sound
     * @param releaseSound null = the school's release sound
     * @param impactSound  null = the school's impact sound
     * @param telegraph    whether area spells mark their area on the ground during the windup
     * @param look         school used for particles and sounds (null = the spell's school)
     */
    public record Presentation(@Nullable SoundEvent windupSound, @Nullable SoundEvent releaseSound, @Nullable SoundEvent impactSound, boolean telegraph,
                               @Nullable MagicSchool look) {
        public static final Presentation DEFAULT = new Presentation(null, null, null, true, null);
    }

    public static final class Builder {
        private final ResourceLocation id;
        private final MagicSchool school;
        private Intent intent = Intent.HARMFUL;
        private Delivery delivery = new Delivery.Direct();
        private final List<Impact> impacts = new ArrayList<>();
        private final List<Impact> allyImpacts = new ArrayList<>();
        @Nullable
        private SoundEvent windupSound;
        @Nullable
        private SoundEvent releaseSound;
        @Nullable
        private SoundEvent impactSound;
        private boolean telegraph = true;
        @Nullable
        private MagicSchool look;

        private Builder(ResourceLocation id, MagicSchool school) {
            this.id = id;
            this.school = school;
        }

        public Builder helpful() {
            this.intent = Intent.HELPFUL;
            return this;
        }

        public Builder delivery(Delivery newDelivery) {
            this.delivery = newDelivery;
            return this;
        }

        public Builder impact(Impact impact) {
            this.impacts.add(impact);
            return this;
        }

        public Builder impactIf(boolean condition, Impact impact) {
            return condition ? this.impact(impact) : this;
        }

        public Builder allyImpact(Impact impact) {
            this.allyImpacts.add(impact);
            return this;
        }

        public Builder healAllies(float coefficient) {
            return this.allyImpact(new Impact.Heal(coefficient));
        }

        public Builder damage(float coefficient) {
            return this.impact(Impact.Damage.of(coefficient));
        }

        public Builder sounds(@Nullable SoundEvent windup, @Nullable SoundEvent release, @Nullable SoundEvent impact) {
            this.windupSound = windup;
            this.releaseSound = release;
            this.impactSound = impact;
            return this;
        }

        public Builder looksLike(MagicSchool lookSchool) {
            this.look = lookSchool;
            return this;
        }

        public Builder noTelegraph() {
            this.telegraph = false;
            return this;
        }

        public AbilitySpec build() {
            Presentation presentation = new Presentation(this.windupSound, this.releaseSound, this.impactSound, this.telegraph, this.look);
            return new AbilitySpec(this.id, this.school, this.intent, this.delivery, this.impacts, this.allyImpacts, presentation);
        }
    }
}
