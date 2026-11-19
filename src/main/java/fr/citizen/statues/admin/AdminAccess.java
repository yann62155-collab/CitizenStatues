package fr.citizen.statues.admin;

import java.util.UUID;

/** Adapter implemented by the NeoForge command/menu layer using server permissions. */
@FunctionalInterface
public interface AdminAccess {
    boolean mayManageStatues(UUID playerId);
}
