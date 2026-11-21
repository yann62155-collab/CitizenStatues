package fr.citizen.statues;

import fr.citizen.statues.data.Destination;
import fr.citizen.statues.data.StatueProfile;
import fr.citizen.statues.voice.IntentMatcher;
import fr.citizen.statues.voice.IntentMatcher.Command;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IntentMatcherTest {
    private static Destination d(String id, String name) {
        return new Destination(id, name, "", "minecraft:overworld", 0, 64, 0, 0, 0, "compass", true);
    }

    private static StatueProfile profile() {
        Map<String, Destination> map = new LinkedHashMap<>();
        for (Destination d : List.of(d("marche", "Place du Marché"), d("eldoria", "Eldoria"),
                d("chateau", "Château Royal"), d("ferme", "La Ferme"), d("port", "Port de Valcourt"),
                d("mine", "Mine abandonnée"), d("taverne", "Taverne du Dragon"))) map.put(d.id(), d);
        return new StatueProfile("p", "Guide", "spawn", true, map, Map.of("le bateau", "port"));
    }

    private static String id(String... hypotheses) {
        var r = IntentMatcher.analyze(List.of(hypotheses), profile());
        return r.destination() == null ? null : r.destination().id();
    }

    @Test void exactNamesInSentences() {
        assertEquals("marche", id("je veux aller à la place du marché"));
        assertEquals("chateau", id("emmène-moi au château royal s'il te plaît"));
        assertEquals("taverne", id("téléporte moi à la taverne du dragon"));
    }

    @Test void partialNamesAreEnough() {
        assertEquals("marche", id("au marché"));
        assertEquals("chateau", id("le château"));
        assertEquals("port", id("je vais au port"));
        assertEquals("taverne", id("la taverne"));
    }

    @Test void typicalRecognitionErrors() {
        assertEquals("marche", id("je veux aller au marcher"));          // homophone
        assertEquals("chateau", id("emmène moi au chateaux"));
        assertEquals("eldoria", id("je veux aller à el doria"));           // split word
        assertEquals("port", id("vers le port de val court"));
        assertEquals("taverne", id("la tavernes du dragons"));
    }

    @Test void alternativesAreUsedWhenTheBestOneFails() {
        assertEquals("mine", id("je veux aller à la mi", "je veux aller à la mine"));
    }

    @Test void synonymsAndOrdinals() {
        assertEquals("port", id("je veux prendre le bateau"));
        // alphabetical order: Château, Eldoria, La Ferme, Mine, Place du Marché, Port, Taverne
        assertEquals("chateau", id("le premier"));
        assertEquals("eldoria", id("numéro deux"));
        assertEquals("taverne", id("le dernier"));
    }

    @Test void destinationWordsAreNotTreatedAsCommands() {
        assertEquals("ferme", id("la ferme"));
    }

    @Test void commands() {
        assertEquals(Command.CANCEL, IntentMatcher.analyze(List.of("annuler"), profile()).command());
        assertEquals(Command.CANCEL, IntentMatcher.analyze(List.of("non laisse tomber"), profile()).command());
        assertEquals(Command.MENU, IntentMatcher.analyze(List.of("montre moi la liste"), profile()).command());
    }

    @Test void unrelatedSpeechNeverTeleports() {
        assertNull(id("bonjour comment ça va"));
        assertNull(id("je mange une pomme"));
        assertNull(id("euh"));
        assertNull(id("la"));
    }

    @Test void suggestionsWhenUnsure() {
        var nearWord = IntentMatcher.analyze(List.of("je veux aller à l'eldorado"), profile());
        assertNull(nearWord.destination());
        assertEquals("eldoria", nearWord.suggestions().get(0).destination().id());

        var r = IntentMatcher.analyze(List.of("je veux aller à la tav"), profile());
        assertTrue(r.suggestions().stream().anyMatch(c -> c.destination().id().equals("taverne")), r.toString());
    }
}
