package fr.citizen.statues.voice;

/** French, text-only prompts. The statue itself never speaks. */
public final class ConversationPrompts {
    private ConversationPrompts() {}

    public static String forOutcome(RetrySession.Outcome outcome) {
        return switch (outcome) {
            case ASK_REPEAT -> "Je n'ai pas compris. Peux-tu répéter ta destination ?";
            case SHOW_EXAMPLES -> "Tu peux dire, par exemple : « emmène-moi au village » ou « je veux aller au marché ».";
            case OPEN_DESTINATION_MENU -> "Je n'ai pas réussi à identifier ta destination. Voici la liste des lieux disponibles.";
            case UNDERSTOOD -> "Destination comprise.";
        };
    }
}
