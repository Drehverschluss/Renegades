package com.drehverschluss.renegades.level;

import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Holds the active level source; there is none by default.
 */
public final class RenegadeLevels {
    @Nullable
    private static RenegadeLevelProvider provider;

    private RenegadeLevels() {
    }

    public static Optional<RenegadeLevelProvider> provider() {
        return Optional.ofNullable(provider);
    }

    public static void setProvider(RenegadeLevelProvider levelProvider) {
        provider = levelProvider;
    }
}
