package fr.citizen.statues;

import fr.citizen.statues.registry.ModEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for Citizen Statues.
 * Statues are silent; voice input is client opt-in and teleportation is server-authoritative.
 */
@Mod(CitizenStatues.MOD_ID)
public final class CitizenStatues {
    public static final String MOD_ID = "citizenstatues";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public CitizenStatues() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITY_TYPES.register(modBus);
        LOGGER.info("Citizen Statues initialized.");
    }
}
