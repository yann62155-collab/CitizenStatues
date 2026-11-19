package fr.citizen.statues.registry;

import fr.citizen.statues.CitizenStatues;
import fr.citizen.statues.data.StatueDataService;
import fr.citizen.statues.entity.GuideStatueEntity;
import fr.citizen.statues.network.PlayerDestinationsPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Sends the guide menu payload once when a player enters a guide statue's proximity radius. */
@EventBusSubscriber(modid = CitizenStatues.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class StatueProximityEvents {
    private static final double RADIUS = 4.0D;
    private static final Map<UUID, Set<UUID>> NEARBY = new HashMap<>();
    private static long ticks;

    private StatueProximityEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks % 5 != 0) return;
        Set<UUID> seenStatues = new HashSet<>();
        for (var level : event.getServer().getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (!(entity instanceof GuideStatueEntity statue)) continue;
                seenStatues.add(statue.getUUID());
                var profile = StatueDataService.get().registry().get(statue.getProfileId()).orElse(null);
                if (profile == null || !profile.enabled()) {
                    NEARBY.remove(statue.getUUID());
                    continue;
                }
                Set<UUID> nowNearby = new HashSet<>();
                for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                        statue.getBoundingBox().inflate(RADIUS))) {
                    if (player.distanceToSqr(statue) > RADIUS * RADIUS) continue;
                    nowNearby.add(player.getUUID());
                    Set<UUID> previous = NEARBY.computeIfAbsent(statue.getUUID(), ignored -> new HashSet<>());
                    if (previous.add(player.getUUID())) {
                        var destinations = profile.destinations().values().stream()
                                .filter(destination -> destination.enabled())
                                .sorted(java.util.Comparator.comparing(
                                        fr.citizen.statues.data.Destination::name, String.CASE_INSENSITIVE_ORDER))
                                .map(destination -> destination.id() + "|" + destination.name().replace("|", "/")
                                        + "|" + destination.description().replace("|", "/").replace("\n", " ")
                                        + "|" + destination.icon().replace("|", "/") + "|" + destination.dimension())
                                .toList();
                        PacketDistributor.sendToPlayer(player,
                                new PlayerDestinationsPayload(profile.id(), profile.name(), destinations));
                    }
                }
                NEARBY.computeIfAbsent(statue.getUUID(), ignored -> new HashSet<>()).retainAll(nowNearby);
            }
        }
        NEARBY.keySet().retainAll(seenStatues);
    }
}
