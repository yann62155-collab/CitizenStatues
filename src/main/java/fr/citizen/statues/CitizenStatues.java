package fr.citizen.statues;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for Citizen Statues.
 *
 * The statue itself is intentionally silent. Voice capture and speech
 * recognition will be implemented as client-only interaction services;
 * all destination validation and teleportation remain server-authoritative.
 */
@Mod(CitizenStatues.MOD_ID)
public final class CitizenStatues {
    public static final String MOD_ID = "citizenstatues";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public CitizenStatues() {
        LOGGER.info("Citizen Statues initialized.");
    }
}
