package fr.citizen.statues.security;

import fr.citizen.statues.data.Destination;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import java.util.Optional;
import java.util.Set;

/** Server-only destination transfer with dimension, world-border and landing checks. */
public final class TeleportService {
    public enum Status {
        SUCCESS, DESTINATION_DISABLED, INVALID_DIMENSION, OUTSIDE_WORLD_BORDER,
        NO_SAFE_LANDING, INVALID_COORDINATES
    }

    private TeleportService() {}

    public static Status teleport(ServerPlayer player, MinecraftServer server, Destination destination) {
        if (!destination.enabled()) return Status.DESTINATION_DISABLED;
        if (!Double.isFinite(destination.x()) || !Double.isFinite(destination.y())
                || !Double.isFinite(destination.z())) return Status.INVALID_COORDINATES;

        ResourceLocation location = ResourceLocation.tryParse(destination.dimension());
        if (location == null) return Status.INVALID_DIMENSION;
        ServerLevel target = server.getLevel(ResourceKey.create(Registries.DIMENSION, location));
        if (target == null) return Status.INVALID_DIMENSION;

        BlockPos requested = BlockPos.containing(destination.x(), destination.y(), destination.z());
        if (!target.getWorldBorder().isWithinBounds(requested)) return Status.OUTSIDE_WORLD_BORDER;

        Optional<BlockPos> landing = findSafeLanding(target, requested);
        if (landing.isEmpty()) return Status.NO_SAFE_LANDING;

        BlockPos feet = landing.get();
        player.teleportTo(target, feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D,
                Set.of(), destination.yaw(), destination.pitch());
        return Status.SUCCESS;
    }

    private static Optional<BlockPos> findSafeLanding(ServerLevel level, BlockPos origin) {
        int minY = level.getMinBuildHeight() + 1;
        int maxY = level.getMaxBuildHeight() - 2;
        for (int radius = 0; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int dy = 0; dy <= 4; dy++) {
                        for (int sign : dy == 0 ? new int[]{1} : new int[]{1, -1}) {
                            int y = origin.getY() + dy * sign;
                            if (y < minY || y > maxY) continue;
                            BlockPos feet = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
                            BlockState floor = level.getBlockState(feet.below());
                            BlockState body = level.getBlockState(feet);
                            BlockState head = level.getBlockState(feet.above());
                            if (!floor.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)) continue;
                            if (!body.getCollisionShape(level, feet).isEmpty()) continue;
                            if (!head.getCollisionShape(level, feet.above()).isEmpty()) continue;
                            if (body.getFluidState().getType() != Fluids.EMPTY
                                    || head.getFluidState().getType() != Fluids.EMPTY) continue;
                            if (!level.getWorldBorder().isWithinBounds(feet)) continue;
                            return Optional.of(feet);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }
}
