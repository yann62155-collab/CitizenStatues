package fr.citizen.statues;

import fr.citizen.statues.data.Destination;
import fr.citizen.statues.data.DestinationCatalog;
import fr.citizen.statues.data.RegistryStorage;
import fr.citizen.statues.data.StatueProfile;
import fr.citizen.statues.data.StatueRegistry;
import fr.citizen.statues.security.PlayerRateLimiter;
import fr.citizen.statues.security.TeleportPolicy;
import fr.citizen.statues.voice.IntentMatcher;
import fr.citizen.statues.voice.RetrySession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CitizenStatuesCoreTest {
    @TempDir Path temp;

    private Destination spawn() {
        return new Destination("spawn", "Village", "Le village principal",
                "minecraft:overworld", 10, 64, 10, 0, 0, "house", true);
    }

    @Test void destinationRejectsInvalidCoordinates() {
        assertThrows(IllegalArgumentException.class, () ->
                new Destination("bad", "Bad", "", "minecraft:overworld",
                        Double.NaN, 64, 0, 0, 0, "x", true));
    }

    @Test void profileMatchesFrenchDestinationAndSynonyms() {
        Destination destination = spawn();
        StatueProfile profile = new StatueProfile("statue", "Guide", "spawn", true,
                Map.of(destination.id(), destination), Map.of("centre ville", "spawn"));
        assertEquals("spawn", IntentMatcher.match("Je veux aller au village", profile).orElseThrow().id());
        assertEquals("spawn", IntentMatcher.match("Emmène-moi au centre-ville", profile).orElseThrow().id());
    }

    @Test void retriesFallBackOnThirdFailure() {
        RetrySession session = new RetrySession(3);
        UUID player = UUID.randomUUID();
        assertEquals(RetrySession.Outcome.ASK_REPEAT, session.misunderstood(player));
        assertEquals(RetrySession.Outcome.SHOW_EXAMPLES, session.misunderstood(player));
        assertEquals(RetrySession.Outcome.OPEN_DESTINATION_MENU, session.misunderstood(player));
        assertEquals(0, session.attempts(player));
    }

    @Test void catalogSearchesAndPaginates() {
        Destination a = spawn();
        Destination b = new Destination("market", "Marché", "Commerces", "minecraft:overworld",
                20, 64, 20, 0, 0, "emerald", true);
        StatueProfile profile = new StatueProfile("s", "Guide", "general", true,
                Map.of(a.id(), a, b.id(), b), Map.of());
        var page = DestinationCatalog.search(profile, "mar", 0, 1);
        assertEquals(1, page.total());
        assertEquals("Marché", page.entries().getFirst().name());
    }

    @Test void storageRoundTripsStatuesAndDestinations() throws Exception {
        StatueRegistry registry = new StatueRegistry();
        StatueProfile profile = registry.create("Guide du spawn", "spawn");
        profile.putDestination(spawn());
        profile.putSynonym("centre", "spawn");
        Path file = temp.resolve("statues.json");
        RegistryStorage.save(file, registry);
        StatueRegistry loaded = RegistryStorage.load(file);
        assertEquals(1, loaded.size());
        var restored = loaded.get(profile.id()).orElseThrow();
        assertEquals("Village", restored.destinations().get("spawn").name());
        assertEquals("spawn", restored.synonyms().get("centre"));
    }


    @Test void corruptRegistryIsBackedUpAndRecoversEmpty() throws Exception {
        Path file = temp.resolve("broken-statues.json");
        java.nio.file.Files.writeString(file, "{ not valid json");
        StatueRegistry recovered = RegistryStorage.load(file);
        assertEquals(0, recovered.size());
        assertTrue(java.nio.file.Files.exists(temp.resolve("broken-statues.json.corrupt")));
    }

    @Test void teleportPolicyRejectsMissingDimensionAndUnsafeHeight() {
        assertEquals(TeleportPolicy.Result.INVALID_DIMENSION,
                TeleportPolicy.validate(spawn(), false, -64, 320));
        Destination tooHigh = new Destination("high", "High", "", "minecraft:overworld",
                0, 400, 0, 0, 0, "x", true);
        assertEquals(TeleportPolicy.Result.UNSAFE_HEIGHT,
                TeleportPolicy.validate(tooHigh, true, -64, 320));
    }

    @Test void rateLimiterEnforcesCooldown() {
        PlayerRateLimiter limiter = new PlayerRateLimiter(1_000);
        UUID player = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(player, 10_000));
        assertFalse(limiter.tryAcquire(player, 10_500));
        assertTrue(limiter.tryAcquire(player, 11_000));
    }
}
