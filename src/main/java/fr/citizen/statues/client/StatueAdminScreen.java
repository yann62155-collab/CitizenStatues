package fr.citizen.statues.client;

import fr.citizen.statues.network.AdminActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Clean tabbed operator console. All mutations remain server-authoritative. */
public final class StatueAdminScreen extends Screen {
    private static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath("citizenhub", "textures/gui/parchemin.png");
    private static final int INK = 0xFF3B2A1A;
    private static final int INK_LIGHT = 0xFF6B5A44;
    private static final int RED = 0xFF7A1A12;
    private static final int GOLD = 0xFFB08D3C;
    private static final int ROLL = 22;
    private enum Page { STATUES, DESTINATIONS, LINKS }
    private Page page = Page.STATUES;
    private final List<String> lines;
    private final List<Entry> statues = new ArrayList<>();
    private final List<Entry> destinations = new ArrayList<>();
    private final Map<String, DestinationDetails> destinationDetails = new LinkedHashMap<>();
    private final Map<String, StatueDetails> statueDetails = new LinkedHashMap<>();
    private String selectedStatue = "";
    private String selectedDestination = "";
    private String pendingConfirmation = "";
    private EditBox idInput;
    private EditBox nameInput;
    private EditBox descriptionInput;
    private int listPage;
    private static final int PAGE_SIZE = 5;

    private record Entry(String id, String label, boolean enabled) {}
    private record StatueDetails(String specialization, String count, String destinations) {}
    private record DestinationDetails(String dimension, String x, String y, String z,
                                      String yaw, String pitch, String description) {}

    public StatueAdminScreen(List<String> lines) {
        super(Component.literal("CITIZEN • Administration"));
        this.lines = List.copyOf(lines);
        parseEntries();
    }

    private void parseEntries() {
        boolean catalog = false;
        for (String line : lines) {
            if (line.startsWith("STATUEDETAIL|")) {
                String[] p = line.split("\\|", 5);
                if (p.length == 5) statueDetails.put(p[1], new StatueDetails(p[2], p[3], p[4]));
                continue;
            }
            if (line.startsWith("DETAIL|")) {
                String[] p = line.split("\\|", 9);
                if (p.length == 9) destinationDetails.put(p[1],
                        new DestinationDetails(p[2], p[3], p[4], p[5], p[6], p[7], p[8]));
                continue;
            }
            if (line.startsWith("CATALOGUE PARTAGÉ")) { catalog = true; continue; }
            if (line.isBlank() || line.startsWith("STATUES —")) continue;
            int open = line.lastIndexOf('['), close = line.lastIndexOf(']');
            if (open < 0 || close <= open) continue;
            Entry e = new Entry(line.substring(open + 1, close), line.substring(2, open).trim(),
                    line.startsWith("● "));
            (catalog ? destinations : statues).add(e);
        }
        if (selectedStatue.isBlank() && !statues.isEmpty()) selectedStatue = statues.get(0).id();
        if (selectedDestination.isBlank() && !destinations.isEmpty()) selectedDestination = destinations.get(0).id();
    }

    @Override
    protected void init() {
        clearWidgets();
        int panelW = Math.min(540, width - 24);
        int left = (width - panelW) / 2;
        int top = Math.max(12, (height - 360) / 2);
        int inner = left + 14;
        int contentW = panelW - 28;

        addRenderableWidget(button("Statues", inner, top + 43, 118, () -> switchPage(Page.STATUES)));
        addRenderableWidget(button("Destinations", inner + 122, top + 43, 142, () -> switchPage(Page.DESTINATIONS)));
        addRenderableWidget(button("Associations", inner + 268, top + 43, 118, () -> switchPage(Page.LINKS)));
        addRenderableWidget(button("↻", left + panelW - 48, top + 10, 30,
                () -> PacketDistributor.sendToServer(new AdminActionPayload("refresh", "", ""))));

        idInput = new EditBox(font, inner, top + 91, 156, 20, Component.literal("Identifiant"));
        idInput.setMaxLength(64);
        idInput.setHint(Component.literal("identifiant"));
        addRenderableWidget(idInput);
        nameInput = new EditBox(font, inner + 164, top + 91, contentW - 164, 20, Component.literal("Nom"));
        nameInput.setMaxLength(64);
        nameInput.setHint(Component.literal("Nom affiché"));
        addRenderableWidget(nameInput);
        descriptionInput = new EditBox(font, inner, top + 116, contentW, 20, Component.literal("Description"));
        descriptionInput.setMaxLength(256);
        descriptionInput.setHint(Component.literal("Description de la destination"));
        addRenderableWidget(descriptionInput);

        if (page == Page.STATUES) buildStatuePage(inner, top, contentW);
        else if (page == Page.DESTINATIONS) buildDestinationPage(inner, top, contentW);
        else buildLinksPage(inner, top, contentW);

        addRenderableWidget(button("Fermer", width / 2 - 48, height - 30, 96, this::onClose));
    }

    private void buildStatuePage(int x, int top, int w) {
        label("PNJ GUIDES", x, top + 76);
        buildList(statues, x, top + 145, w, true);
        int y = top + 286;
        addRenderableWidget(action("Créer une statue ici", x, y, w,
                () -> { String name = nameInput.getValue().trim(); if (!name.isBlank()) send("statue-create-here", name, ""); }));
        y += 24;
        addRenderableWidget(action("Renommer la statue", x, y, 174,
                () -> { if (!selectedStatue.isBlank() && !nameInput.getValue().isBlank()) send("statue-rename", selectedStatue, nameInput.getValue().trim()); }));
        addRenderableWidget(action("Activer / désactiver", x + 180, y, w - 180, () -> {
            Entry e = find(statues, selectedStatue);
            if (e != null) send(e.enabled() ? "statue-disable" : "statue-enable", selectedStatue, "");
        }));
        y += 24;
        addRenderableWidget(action(confirmLabel("statue"), x, y, w,
                () -> confirmDelete("statue", () -> send("statue-remove", selectedStatue, ""))));
        StatueDetails d = statueDetails.get(selectedStatue);
        if (d != null) {
            label("Spécialisation : " + d.specialization() + "  •  " + d.count() + " destination(s)", x, top + 322);
            if (!d.destinations().isBlank()) label("Liées : " + trim(d.destinations(), 75), x, top + 335);
        }
    }

    private void buildDestinationPage(int x, int top, int w) {
        label("CATALOGUE PARTAGÉ", x, top + 76);
        buildList(destinations, x, top + 145, w, false);
        int y = top + 310;
        addRenderableWidget(action("Créer à ma position", x, y, 174, () -> {
            String id = idInput.getValue().trim().toLowerCase(Locale.ROOT);
            String name = nameInput.getValue().trim();
            if (!id.isBlank() && !name.isBlank()) send("catalog-create-here", name, id);
        }));
        addRenderableWidget(action("Déplacer ici", x + 180, y, w - 180,
                () -> { if (!selectedDestination.isBlank()) send("catalog-move-here", "", selectedDestination); }));
        y += 24;
        addRenderableWidget(action("Renommer", x, y, 174, () -> {
            if (!selectedDestination.isBlank() && !nameInput.getValue().isBlank())
                send("catalog-rename", nameInput.getValue().trim(), selectedDestination);
        }));
        addRenderableWidget(action("Enregistrer description", x + 180, y, w - 180, () -> {
            if (!selectedDestination.isBlank()) send("catalog-description", descriptionInput.getValue(), selectedDestination);
        }));
        y += 24;
        Entry e = find(destinations, selectedDestination);
        addRenderableWidget(action(e != null && e.enabled() ? "Désactiver" : "Activer", x, y, 174, () -> {
            Entry current = find(destinations, selectedDestination);
            if (current != null) send(current.enabled() ? "destination-disable" : "destination-enable", "", selectedDestination);
        }));
        addRenderableWidget(action(confirmLabel("destination"), x + 180, y, w - 180,
                () -> confirmDelete("destination", () -> send("catalog-remove", "", selectedDestination))));
        DestinationDetails d = destinationDetails.get(selectedDestination);
        if (d != null) label(trim(d.dimension() + "  •  " + d.x() + " / " + d.y() + " / " + d.z(), 82), x, top + 406);
    }

    private void buildLinksPage(int x, int top, int w) {
        label("SÉLECTIONNE UNE STATUE ET UNE DESTINATION", x, top + 76);
        int half = (w - 12) / 2;
        label("STATUES", x, top + 101);
        label("DESTINATIONS", x + half + 12, top + 101);
        buildCompactList(statues, x, top + 120, half, true);
        buildCompactList(destinations, x + half + 12, top + 120, half, false);
        int y = top + 286;
        addRenderableWidget(action("Attribuer la destination", x, y, w,
                () -> send("destination-assign", selectedStatue, selectedDestination)));
        y += 24;
        addRenderableWidget(action("Retirer cette destination", x, y, w,
                () -> send("destination-unassign", selectedStatue, selectedDestination)));
        StatueDetails d = statueDetails.get(selectedStatue);
        if (d != null) label("Destinations actuellement liées : " + d.count(), x, top + 345);
    }

    private void buildList(List<Entry> entries, int x, int y, int w, boolean statue) {
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int start = Math.min(listPage, pages - 1) * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = start + i;
            if (index >= entries.size()) break;
            Entry e = entries.get(index);
            int yy = y + i * 22;
            addRenderableWidget(Button.builder(Component.literal((e.enabled() ? "●  " : "○  ") + trim(e.label(), 48)),
                    b -> { if (statue) selectedStatue = e.id(); else { selectedDestination = e.id(); DestinationDetails d = destinationDetails.get(e.id()); if (d != null && descriptionInput != null) descriptionInput.setValue(d.description()); } refreshLayout(); })
                    .bounds(x, yy, w, 20).build());
        }
        addRenderableWidget(button("◀", x, y + PAGE_SIZE * 22 + 2, 32, () -> { listPage = Math.max(0, listPage - 1); refreshLayout(); }));
        addRenderableWidget(button((Math.min(listPage + 1, pages)) + " / " + pages, x + 36, y + PAGE_SIZE * 22 + 2, 72, () -> {}));
        addRenderableWidget(button("▶", x + 112, y + PAGE_SIZE * 22 + 2, 32, () -> { if ((listPage + 1) * PAGE_SIZE < entries.size()) listPage++; refreshLayout(); }));
    }

    private void buildCompactList(List<Entry> entries, int x, int y, int w, boolean statue) {
        int shown = Math.min(6, entries.size());
        for (int i = 0; i < shown; i++) {
            Entry e = entries.get(i);
            addRenderableWidget(Button.builder(Component.literal((e.enabled() ? "● " : "○ ") + trim(e.label(), 23)),
                    b -> { if (statue) selectedStatue = e.id(); else selectedDestination = e.id(); refreshLayout(); })
                    .bounds(x, y + i * 22, w, 20).build());
        }
    }

    private void switchPage(Page next) { page = next; listPage = 0; pendingConfirmation = ""; refreshLayout(); }
    private void refreshLayout() {
        String id = idInput == null ? "" : idInput.getValue();
        String name = nameInput == null ? "" : nameInput.getValue();
        String description = descriptionInput == null ? "" : descriptionInput.getValue();
        clearWidgets();
        init();
        if (idInput != null) idInput.setValue(id);
        if (nameInput != null) nameInput.setValue(name);
        if (descriptionInput != null) descriptionInput.setValue(description);
    }
    private void confirmDelete(String kind, Runnable delete) {
        if (!kind.equals(pendingConfirmation)) { pendingConfirmation = kind; refreshLayout(); }
        else { pendingConfirmation = ""; delete.run(); }
    }
    private String confirmLabel(String kind) {
        return pendingConfirmation.equals(kind) ? "⚠ Confirmer la suppression" : "Supprimer";
    }

    private Button button(String text, int x, int y, int w, Runnable run) {
        return Button.builder(Component.literal(text), b -> run.run()).bounds(x, y, w, 20).build();
    }
    private Button action(String text, int x, int y, int w, Runnable run) {
        Button b = button(text, x, y, w, run);
        if (text.contains("Attribuer") || text.contains("Retirer cette"))
            b.active = !selectedStatue.isBlank() && !selectedDestination.isBlank();
        else if (page == Page.STATUES && !text.contains("Créer"))
            b.active = !selectedStatue.isBlank();
        else if (page == Page.DESTINATIONS && !text.contains("Créer"))
            b.active = !selectedDestination.isBlank();
        return b;
    }
    private void send(String action, String statueId, String destinationId) {
        if ((action.startsWith("statue-") && statueId.isBlank())
                || ((action.equals("destination-assign") || action.equals("destination-unassign"))
                    && (statueId.isBlank() || destinationId.isBlank()))
                || ((action.equals("destination-enable") || action.equals("destination-disable")) && destinationId.isBlank())
                || (action.startsWith("catalog-") && !action.equals("catalog-create-here") && destinationId.isBlank())) return;
        PacketDistributor.sendToServer(new AdminActionPayload(action, statueId, destinationId));
    }
    private Entry find(List<Entry> entries, String id) {
        return entries.stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
    }
    private void label(String text, int x, int y) { /* drawn in render */ }
    private static String trim(String s, int max) { return s.length() <= max ? s : s.substring(0, max - 1) + "…"; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        int panelW = Math.min(540, width - 24);
        int left = (width - panelW) / 2;
        int top = Math.max(12, (height - 360) / 2);
        drawParchment(g, left, top, panelW, 430);
        String heading = "CITIZEN • ADMINISTRATION";
        g.drawString(font, heading, width / 2 - font.width(heading) / 2, top + 15, RED, false);
        g.fill(left + 32, top + 31, left + panelW - 32, top + 32, GOLD);
        g.drawString(font, "Choisis un onglet puis sélectionne un élément.",
                left + 18, top + 69, INK_LIGHT, false);
        g.drawString(font, page == Page.STATUES ? "GESTION DES PNJ" :
                page == Page.DESTINATIONS ? "RÉSEAU DES DESTINATIONS" : "LIAISONS STATUE ↔ DESTINATION",
                left + 18, top + 76, RED, false);
        if (page != Page.LINKS) {
            g.drawString(font, page == Page.STATUES ? "Nom de la statue" : "Identifiant", left + 14, top + 82, INK, false);
            g.drawString(font, "Nom", left + 178, top + 82, INK, false);
            if (page == Page.DESTINATIONS) g.drawString(font, "Description", left + 14, top + 107, INK, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawParchment(GuiGraphics g, int x, int y, int w, int h) {
        int roll = Math.min(22, Math.max(14, Math.round(h * ROLL / 256f)));
        int bodyTop = y + roll;
        int bodyBottom = y + h - roll;
        g.blit(BG, x, y, w, roll, 0f, 0f, 256, ROLL, 256, 256);
        g.blit(BG, x, bodyTop, w, bodyBottom - bodyTop, 0f, (float) ROLL,
                256, 256 - 2 * ROLL, 256, 256);
        g.blit(BG, x, bodyBottom, w, roll, 0f, (float) (256 - ROLL),
                256, ROLL, 256, 256);
    }

    @Override public boolean isPauseScreen() { return false; }
}
