package fr.citizen.statues;

import fr.citizen.statues.data.Destination;
import fr.citizen.statues.data.StatueProfile;
import fr.citizen.statues.voice.RetrySession;
import fr.citizen.statues.voice.VoiceIntentService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VoiceIntentServiceTest {
    @Test void successfulUtteranceResolvesOnlyProfileDestination() {
        Destination village = new Destination("village", "Village", "", "minecraft:overworld",
                0, 64, 0, 0, 0, "house", true);
        StatueProfile profile = new StatueProfile("profile", "Guide", "spawn", true,
                Map.of("village", village), Map.of());
        VoiceIntentService service = new VoiceIntentService(3);
        UUID player = UUID.randomUUID();
        service.begin(player, profile.id());

        var result = service.accept(player, "Je veux aller au village", profile);
        assertTrue(result.understood());
        assertEquals("village", result.destination().id());
        assertEquals(RetrySession.Outcome.UNDERSTOOD, result.outcome());
    }

    @Test void unknownIntentEscalatesAfterThreeAttempts() {
        StatueProfile profile = new StatueProfile("profile", "Guide", "spawn", true,
                Map.of(), Map.of());
        VoiceIntentService service = new VoiceIntentService(3);
        UUID player = UUID.randomUUID();
        service.begin(player, profile.id());

        assertEquals(RetrySession.Outcome.ASK_REPEAT,
                service.accept(player, "xyz", profile).outcome());
        assertEquals(RetrySession.Outcome.SHOW_EXAMPLES,
                service.accept(player, "xyz", profile).outcome());
        assertEquals(RetrySession.Outcome.OPEN_DESTINATION_MENU,
                service.accept(player, "xyz", profile).outcome());
    }

    @Test void closedInteractionCannotBeReused() {
        StatueProfile profile = new StatueProfile("profile", "Guide", "spawn", true,
                Map.of(), Map.of());
        VoiceIntentService service = new VoiceIntentService(3);
        UUID player = UUID.randomUUID();
        service.begin(player, profile.id());
        service.close(player);
        assertFalse(service.accept(player, "village", profile).understood());
    }
}
