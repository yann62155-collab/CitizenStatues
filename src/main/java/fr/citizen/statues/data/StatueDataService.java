package fr.citizen.statues.data;

import java.io.IOException;
import java.nio.file.Path;

/** Owns the active world's statue registry and its persistent JSON file. */
public final class StatueDataService {
    private static StatueDataService instance;
    private final Path file;
    private StatueRegistry registry;

    private StatueDataService(Path file, StatueRegistry registry) {
        this.file = file;
        this.registry = registry;
    }

    public static synchronized StatueDataService open(Path file) throws IOException {
        instance = new StatueDataService(file, RegistryStorage.load(file));
        return instance;
    }

    public static synchronized StatueDataService get() {
        if (instance == null) throw new IllegalStateException("Statue data is not loaded");
        return instance;
    }

    public StatueRegistry registry() { return registry; }
    public synchronized void save() throws IOException { RegistryStorage.save(file, registry); }

    public static synchronized void close() throws IOException {
        if (instance != null) {
            instance.save();
            instance = null;
        }
    }
}
