package fr.citizen.statues.voice;

import fr.citizen.statues.data.Destination;
import fr.citizen.statues.data.StatueProfile;

import java.text.Normalizer;
import java.util.*;

/**
 * Tolerant French destination matcher.
 *
 * <p>Speech-to-text output is noisy ("marcher" for "marché", "el doria" for "Eldoria", homophones…),
 * so each destination name, identifier and synonym is compared to every recognised hypothesis with:
 * exact whole-phrase detection, per-word fuzzy comparison (edit distance), a simplified French
 * phonetic key, and re-joined word windows (to repair words split or merged by the recogniser).
 * Ordinals ("le deuxième", "numéro 3") and the commands "annuler" / "menu" are understood too.
 * Unknown speech never becomes coordinates or commands: only destinations of the profile can match.</p>
 */
public final class IntentMatcher {
    /** Minimum score for a destination to be accepted. */
    public static final double ACCEPT = 0.74;
    /** Two different destinations closer than this are considered ambiguous. */
    public static final double AMBIGUITY_MARGIN = 0.06;
    /** Minimum score for a destination to be proposed as a suggestion after a failure. */
    public static final double SUGGEST = 0.50;

    public enum Command { NONE, CANCEL, MENU }

    public record Candidate(Destination destination, double score) {}

    public record Result(Command command, Destination destination, double score,
                         List<Candidate> ranked, String heard) {
        public boolean understood() { return destination != null; }
        public boolean ambiguous() {
            return destination == null && ranked.size() >= 2 && ranked.get(0).score() >= ACCEPT;
        }
        public List<Candidate> suggestions() {
            return ranked.stream().filter(c -> c.score() >= SUGGEST).limit(3).toList();
        }
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "le", "la", "les", "l", "un", "une", "des", "du", "de", "d", "au", "aux", "a", "en", "vers",
            "je", "j", "tu", "veux", "voudrais", "voudrai", "aimerais", "aimerai", "souhaite", "souhaiterais",
            "aller", "allez", "vais", "va", "partir", "rendre", "rends", "me", "m", "moi", "te", "t", "nous",
            "emmene", "emmener", "emmenez", "emmenes", "amene", "amener", "amenez", "conduis", "conduire",
            "envoie", "envoyer", "envoyez", "porte", "transporte", "teleporte", "teleporter", "teleportation",
            "tp", "tele", "direction", "destination", "jusqu", "jusque", "pour", "chez", "dans", "sur", "par",
            "s", "il", "plait", "stp", "svp", "merci", "bonjour", "salut", "hey", "eh", "euh", "heu", "hum",
            "ben", "bah", "alors", "donc", "et", "ou", "y", "c", "est", "ce", "ca", "qui", "que", "qu",
            "mon", "ma", "mes", "ton", "ta", "peux", "pouvez", "pourrais", "pourriez", "faire", "fais",
            "voir", "visiter", "oui", "ok", "d accord", "guide", "statue");

    private static final Set<String> CANCEL_WORDS = Set.of(
            "annule", "annuler", "annulez", "stop", "arrete", "arreter", "rien", "tomber", "laisse",
            "revoir", "quitter", "quitte");
    /** Grammatical words removed from destination names before word-by-word comparison. */
    private static final Set<String> ARTICLES = Set.of(
            "le", "la", "les", "l", "un", "une", "des", "du", "de", "d", "au", "aux", "a", "en", "et", "sur");
    private static final Set<String> MENU_WORDS = Set.of(
            "menu", "liste", "lieux", "destinations", "choix", "options", "montre", "affiche", "aide");

    private static final Map<String, Integer> NUMBERS = Map.ofEntries(
            Map.entry("un", 1), Map.entry("une", 1), Map.entry("deux", 2), Map.entry("trois", 3),
            Map.entry("quatre", 4), Map.entry("cinq", 5), Map.entry("six", 6), Map.entry("sept", 7),
            Map.entry("huit", 8), Map.entry("neuf", 9), Map.entry("dix", 10));
    private static final Map<String, Integer> ORDINALS = Map.ofEntries(
            Map.entry("premier", 1), Map.entry("premiere", 1), Map.entry("1er", 1), Map.entry("1ere", 1),
            Map.entry("deuxieme", 2), Map.entry("second", 2), Map.entry("seconde", 2),
            Map.entry("troisieme", 3), Map.entry("quatrieme", 4), Map.entry("cinquieme", 5),
            Map.entry("sixieme", 6), Map.entry("septieme", 7), Map.entry("huitieme", 8),
            Map.entry("neuvieme", 9), Map.entry("dixieme", 10), Map.entry("dernier", -1), Map.entry("derniere", -1));

    private IntentMatcher() {}

    /** Backwards-compatible single-hypothesis API: returns a destination only when confident. */
    public static Optional<Destination> match(String utterance, StatueProfile profile) {
        return Optional.ofNullable(analyze(List.of(utterance == null ? "" : utterance), profile).destination());
    }

    /**
     * Analyses one or several recogniser hypotheses (best first) and returns the most likely intent.
     */
    public static Result analyze(List<String> hypotheses, StatueProfile profile) {
        List<String> inputs = hypotheses == null ? List.of() : hypotheses.stream()
                .map(IntentMatcher::normalize).filter(s -> !s.isBlank()).distinct().limit(8).toList();
        String heard = inputs.isEmpty() ? "" : inputs.get(0);
        if (inputs.isEmpty() || !profile.enabled()) {
            return new Result(Command.NONE, null, 0, List.of(), heard);
        }

        List<Destination> enabled = profile.destinations().values().stream()
                .filter(Destination::enabled)
                .sorted(Comparator.comparing(Destination::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        Map<String, List<String>> phrases = phrasesByDestination(profile, enabled);
        // Words that belong to a destination name are never treated as filler or commands.
        Set<String> protectedWords = new HashSet<>();
        phrases.values().forEach(list -> list.forEach(p -> tokens(p).stream()
                .filter(t -> !ARTICLES.contains(t)).forEach(protectedWords::add)));

        // Explicit cancel always wins on the primary hypothesis.
        List<String> primaryTokens = tokens(inputs.get(0));
        if (isCancel(primaryTokens, inputs.get(0), protectedWords)) {
            return new Result(Command.CANCEL, null, 1, List.of(), heard);
        }

        Map<String, Double> best = new HashMap<>();
        for (int h = 0; h < inputs.size(); h++) {
            // later alternatives (and the destination-constrained guess, sent last) are trusted a little less
            double weight = Math.max(0.88, 1.0 - 0.03 * h);
            String input = inputs.get(h);
            List<String> queryTokens = tokens(input);

            Destination byOrdinal = ordinal(queryTokens, enabled);
            if (byOrdinal != null) best.merge(byOrdinal.id(), 0.95 * weight, Math::max);

            for (Destination destination : enabled) {
                double score = 0;
                for (String phrase : phrases.getOrDefault(destination.id(), List.of())) {
                    score = Math.max(score, phraseScore(input, queryTokens, phrase, protectedWords));
                    if (score >= 1) break;
                }
                if (score > 0) best.merge(destination.id(), score * weight, Math::max);
            }
        }

        Map<String, Destination> byId = new HashMap<>();
        enabled.forEach(d -> byId.put(d.id(), d));
        List<Candidate> ranked = best.entrySet().stream()
                .map(e -> new Candidate(byId.get(e.getKey()), e.getValue()))
                .sorted(Comparator.comparingDouble(Candidate::score).reversed())
                .toList();

        if (!ranked.isEmpty() && ranked.get(0).score() >= ACCEPT) {
            Candidate top = ranked.get(0);
            boolean ambiguous = ranked.size() > 1 && ranked.get(1).score() >= ACCEPT
                    && top.score() - ranked.get(1).score() < AMBIGUITY_MARGIN && top.score() < 1.0;
            if (!ambiguous) return new Result(Command.NONE, top.destination(), top.score(), ranked, heard);
            return new Result(Command.NONE, null, top.score(), ranked, heard);
        }

        if (primaryTokens.stream().anyMatch(t -> MENU_WORDS.contains(t) && !protectedWords.contains(t))) {
            return new Result(Command.MENU, null, 1, ranked, heard);
        }
        return new Result(Command.NONE, null, ranked.isEmpty() ? 0 : ranked.get(0).score(), ranked, heard);
    }

    // ------------------------------------------------------------------ scoring

    private static Map<String, List<String>> phrasesByDestination(StatueProfile profile, List<Destination> enabled) {
        Map<String, List<String>> phrases = new HashMap<>();
        for (Destination d : enabled) {
            List<String> list = new ArrayList<>();
            list.add(normalize(d.name()));
            String id = normalize(d.id().replace('_', ' ').replace('-', ' '));
            if (!id.isBlank() && !id.matches("[0-9 ]+")) list.add(id);
            phrases.put(d.id(), list);
        }
        profile.synonyms().forEach((phrase, target) -> {
            List<String> list = phrases.get(target);
            if (list != null) list.add(normalize(phrase));
        });
        phrases.values().forEach(list -> list.removeIf(String::isBlank));
        return phrases;
    }

    static double phraseScore(String input, List<String> queryTokens, String phrase, Set<String> protectedWords) {
        if (containsWholePhrase(input, phrase)) return 1.0;

        List<String> target = tokens(phrase).stream().filter(t -> !ARTICLES.contains(t)).toList();
        if (target.isEmpty()) target = tokens(phrase);
        List<String> query = queryTokens.stream()
                .filter(t -> protectedWords.contains(t) || !STOP_WORDS.contains(t)).toList();
        if (query.isEmpty() || target.isEmpty()) return 0;

        // 1) word by word: each meaningful word of the destination must be found in the speech.
        double weighted = 0, totalWeight = 0;
        for (String t : target) {
            double w = Math.max(1, t.length());
            double bestWord = 0;
            for (String q : query) bestWord = Math.max(bestWord, wordSimilarity(q, t));
            weighted += bestWord * w;
            totalWeight += w;
        }
        double wordScore = weighted / totalWeight;

        // 2) joined windows: repairs "el doria" -> "eldoria" or "centreville" -> "centre ville".
        String joinedTarget = String.join("", target);
        double windowScore = 0;
        for (int size = 1; size <= Math.min(4, query.size()); size++) {
            for (int start = 0; start + size <= query.size(); start++) {
                String joined = String.join("", query.subList(start, start + size));
                windowScore = Math.max(windowScore, wordSimilarity(joined, joinedTarget));
            }
        }
        return Math.min(0.99, Math.max(wordScore, windowScore));
    }

    static double wordSimilarity(String a, String b) {
        if (a.equals(b)) return 1.0;
        int shortest = Math.min(a.length(), b.length());
        String pa = phonetic(a), pb = phonetic(b);
        if (shortest <= 2) return pa.equals(pb) && !pa.isEmpty() ? 0.85 : 0;

        double text = similarity(a, b);
        double sound = pa.isEmpty() || pb.isEmpty() ? 0 : (pa.equals(pb) ? 0.95 : similarity(pa, pb) * 0.97);
        double prefix = 0;
        if (shortest >= 4 && (a.startsWith(b) || b.startsWith(a))) prefix = 0.80;
        double score = Math.max(text, Math.max(sound, prefix));
        // very short words need to be near-perfect to avoid false positives ("mine" / "mer")
        if (shortest <= 3 && score < 0.95) score *= 0.75;
        // a clipped start of a word ("tav" -> "taverne") is worth a suggestion, never an automatic teleport
        if (a.length() >= 3 && b.length() > a.length() && b.startsWith(a)) score = Math.max(score, 0.62);
        return score;
    }

    private static double similarity(String a, String b) {
        int max = Math.max(a.length(), b.length());
        if (max == 0) return 1;
        return 1.0 - (double) levenshtein(a, b) / max;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                // transposition ("marhce" / "marche")
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    cur[j] = Math.min(cur[j], prev[j - 1]); // approximated Damerau step
                }
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    /** Simplified French phonetic key: words that sound alike get the same key. */
    static String phonetic(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        if (w.isEmpty()) return w;
        w = w.replaceAll("[^a-z]", "");
        w = w.replace("eaux", "o").replace("eau", "o").replace("aux", "o").replace("au", "o")
                .replace("ph", "f").replace("qu", "k").replace("ck", "k").replace("gu", "g")
                .replace("sch", "x").replace("ch", "x").replace("sh", "x")
                .replace("ge", "je").replace("gi", "ji").replace("gy", "ji")
                .replace("ce", "se").replace("ci", "si").replace("cy", "si")
                .replace("c", "k").replace("w", "v").replace("z", "s").replace("y", "i")
                .replace("ain", "in").replace("ein", "in").replace("aim", "in").replace("im", "in")
                .replace("um", "in").replace("un", "in")
                .replace("ai", "e").replace("ei", "e")
                .replace("an", "A").replace("am", "A").replace("en", "A").replace("em", "A")
                .replace("on", "O").replace("om", "O")
                .replace("ou", "u").replace("oi", "Wa")
                .replace("h", "");
        // silent endings
        w = w.replaceAll("(er|ez|et|es|e|s|t|d|x|p)$", "");
        if (w.endsWith("e") && w.length() > 1) w = w.substring(0, w.length() - 1);
        // collapse doubled letters
        StringBuilder out = new StringBuilder();
        for (char c : w.toCharArray()) {
            if (out.length() == 0 || out.charAt(out.length() - 1) != c) out.append(c);
        }
        return out.toString();
    }

    private static boolean containsWholePhrase(String input, String phrase) {
        if (phrase.isBlank()) return false;
        return (" " + input + " ").contains(" " + phrase + " ")
                || input.replace(" ", "").equals(phrase.replace(" ", ""));
    }

    private static Destination ordinal(List<String> tokens, List<Destination> enabled) {
        if (enabled.isEmpty()) return null;
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            Integer n = ORDINALS.get(t);
            if (n == null && (t.equals("numero") || t.equals("num") || t.equals("choix")) && i + 1 < tokens.size()) {
                String next = tokens.get(i + 1);
                n = NUMBERS.get(next);
                if (n == null && next.matches("[0-9]{1,2}")) n = Integer.parseInt(next);
            }
            if (n == null) continue;
            if (n == -1) return enabled.get(enabled.size() - 1);
            if (n >= 1 && n <= enabled.size()) return enabled.get(n - 1);
        }
        return null;
    }

    private static boolean isCancel(List<String> tokens, String input, Set<String> protectedWords) {
        if (input.contains("laisse tomber") || input.contains("au revoir") || input.contains("non merci")
                || input.equals("non") || input.equals("rien") || input.equals("rien merci")) return true;
        return tokens.stream().anyMatch(t -> CANCEL_WORDS.contains(t) && !protectedWords.contains(t)
                && !t.equals("rien") && !t.equals("laisse") && !t.equals("tomber") && !t.equals("revoir"));
    }

    private static List<String> tokens(String normalized) {
        if (normalized.isBlank()) return List.of();
        return Arrays.asList(normalized.split(" "));
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value.toLowerCase(Locale.FRENCH), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "")
                .replace("œ", "oe").replace("æ", "ae")
                .replace("[unk]", " ")
                .replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }
}
