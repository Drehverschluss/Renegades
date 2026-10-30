package com.drehverschluss.renegades.equipment;

import javax.annotation.Nullable;

/**
 * Inclusive integer range; a null bound is unbounded.
 */
public record IntBounds(@Nullable Integer min, @Nullable Integer max) {
    public static final IntBounds ANY = new IntBounds(null, null);

    public boolean isAny() {
        return this.min == null && this.max == null;
    }

    public boolean test(double value) {
        return (this.min == null || value >= this.min) && (this.max == null || value <= this.max);
    }

    @Override
    public String toString() {
        return (this.min == null ? "" : this.min) + ".." + (this.max == null ? "" : this.max);
    }
}
