package fr.citizen.statues.security;

import fr.citizen.statues.data.Destination;

/** Validation gate to be called on the logical server immediately before teleport. */
public final class TeleportPolicy {
    public enum Result { ALLOWED, DISABLED, INVALID_DIMENSION, OUT_OF_WORLD_BOUNDS, UNSAFE_HEIGHT }

    private TeleportPolicy() {}

    public static Result validate(Destination destination, boolean dimensionExists,
                                  int minBuildHeight, int maxBuildHeight) {
        if (!destination.enabled()) return Result.DISABLED;
        if (!dimensionExists) return Result.INVALID_DIMENSION;
        if (Math.abs(destination.x()) > 29_999_984 || Math.abs(destination.z()) > 29_999_984)
            return Result.OUT_OF_WORLD_BOUNDS;
        if (destination.y() < minBuildHeight || destination.y() >= maxBuildHeight)
            return Result.UNSAFE_HEIGHT;
        return Result.ALLOWED;
    }
}
