package fr.citizen.statues.data;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Searchable, paginated view of enabled destinations for a statue. */
public final class DestinationCatalog {
    public record Page(List<Destination> entries, int page, int pageCount, int total) {}

    private DestinationCatalog() {}

    public static Page search(StatueProfile profile, String query, int requestedPage, int pageSize) {
        if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("pageSize must be 1..100");
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Destination> matches = profile.destinations().values().stream()
                .filter(Destination::enabled)
                .filter(d -> needle.isEmpty()
                        || d.name().toLowerCase(Locale.ROOT).contains(needle)
                        || d.description().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(Destination::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int pages = Math.max(1, (matches.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        int from = Math.min(page * pageSize, matches.size());
        int to = Math.min(from + pageSize, matches.size());
        return new Page(matches.subList(from, to), page, pages, matches.size());
    }
}
