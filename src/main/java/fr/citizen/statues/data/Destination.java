package fr.citizen.statues.data;

import java.util.Locale;
import java.util.Objects;

/** A server-owned, validated teleport destination. */
public record Destination(
        String id,
        String name,
        String description,
        String dimension,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String icon,
        boolean enabled
) {
    public Destination {
        id = requireText(id, "id");
        name = requireText(name, "name");
        description = Objects.requireNonNullElse(description, "");
        dimension = requireText(dimension, "dimension").toLowerCase(Locale.ROOT);
        icon = Objects.requireNonNullElse(icon, "compass");
        if (!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid dimension key");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Coordinates and rotation must be finite");
        }
        if (Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000
                || y < -2048 || y > 2048) {
            throw new IllegalArgumentException("Destination coordinates out of safe bounds");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return value.trim();
    }
}
