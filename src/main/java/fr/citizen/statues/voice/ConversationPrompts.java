package fr.citizen.statues.voice;

import fr.citizen.statues.data.Destination;

import java.util.List;

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

    /** Helpful retry message: what was heard, and the closest destinations when there are some. */
    public static String forMisunderstanding(IntentMatcher.Result result, List<Destination> available) {
        List<IntentMatcher.Candidate> suggestions = result.suggestions();
        if (result.ambiguous() && suggestions.size() >= 2) {
            return "Tu veux dire « " + suggestions.get(0).destination().name() + " » ou « "
                    + suggestions.get(1).destination().name() + " » ? Répète le nom exact.";
        }
        String heard = result.heard().isBlank() ? "" : "J'ai entendu « " + shorten(result.heard()) + " ». ";
        if (!suggestions.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < suggestions.size(); i++) {
                if (i > 0) names.append(i == suggestions.size() - 1 ? " ou " : ", ");
                names.append("« ").append(suggestions.get(i).destination().name()).append(" »");
            }
            return heard + "Tu voulais peut-être dire " + names + " ?";
        }
        if (!available.isEmpty()) {
            return heard + "Dis par exemple « emmène-moi à " + available.get(0).name()
                    + " », « le premier » ou « menu ».";
        }
        return heard + forOutcome(RetrySession.Outcome.ASK_REPEAT);
    }

    private static String shorten(String text) {
        return text.length() <= 60 ? text : text.substring(0, 57) + "…";
    }
}
