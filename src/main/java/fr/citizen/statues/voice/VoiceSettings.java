package fr.citizen.statues.voice;

/** Privacy-first voice options; microphone capture is opt-in and client initiated only. */
public record VoiceSettings(boolean enabled, String languageTag, boolean offlineOnly,
                            int maximumAttempts, int captureLimitSeconds) {
    public VoiceSettings {
        languageTag = languageTag == null || languageTag.isBlank() ? "fr-FR" : languageTag;
        if (maximumAttempts < 1 || maximumAttempts > 5)
            throw new IllegalArgumentException("maximumAttempts must be between 1 and 5");
        if (captureLimitSeconds < 1 || captureLimitSeconds > 30)
            throw new IllegalArgumentException("captureLimitSeconds must be between 1 and 30");
    }

    public static VoiceSettings defaults() {
        return new VoiceSettings(false, "fr-FR", true, 3, 8);
    }
}
