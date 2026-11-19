package fr.citizen.statues.security;

import net.minecraft.server.level.ServerPlayer;

/** All administrative writes are gated on the authoritative server permission level. */
public final class AdminPermission {
    private AdminPermission() {}
    public static boolean allowed(ServerPlayer player) {
        return player != null && player.hasPermissions(2);
    }
}
