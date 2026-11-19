package fr.citizen.statues.registry;

import fr.citizen.statues.CitizenStatues;
import fr.citizen.statues.data.StatueDataService;
import fr.citizen.statues.web.WebAdminServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.IOException;

@EventBusSubscriber(modid = CitizenStatues.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ServerLifecycleEvents {
    private ServerLifecycleEvents() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            var file = event.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("data").resolve("citizen_statues.json");
            StatueDataService.open(file);
            WebAdminServer.start(event.getServer(), java.nio.file.Path.of("config", "citizenstatues-web.properties"));
            CitizenStatues.LOGGER.info("Citizen Statues registry loaded from {}", file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load Citizen Statues registry", exception);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        WebAdminServer.stop();
        try {
            StatueDataService.close();
        } catch (IOException exception) {
            CitizenStatues.LOGGER.error("Could not save Citizen Statues registry", exception);
        }
    }
}
