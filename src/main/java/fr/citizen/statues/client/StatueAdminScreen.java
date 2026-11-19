package fr.citizen.statues.client;

import fr.citizen.statues.network.AdminActionPayload;
import net.minecraft.client.gui.GuiGraphics;
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

/**
 * CitizenStatues administration screen, drawn in the same visual language as CitizenHub.
 * All mutations remain server-authoritative.
 */
public final class StatueAdminScreen extends Screen {
    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath("citizenhub", "textures/gui/parchemin.png");

    private static final int INK = 0xFF3B2A1A;
    private static final int INK_LIGHT = 0xFF6B5A44;
    private static final int RED = 0xFF7A1A12;
    private static final int GOLD = 0xFFB08D3C;
    private static final int HOVER = 0x307A1A12;
    private static final int SELECTED = 0x457A1A12;
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
    private int listPage;
    private static final int PAGE_SIZE = 5;

    private EditBox idInput;
    private EditBox nameInput;
    private EditBox descriptionInput;

    private int px, py, pw, ph, innerX, innerR;
    private final List<Hit> hits = new ArrayList<>();
    private final List<UiButton> buttons = new ArrayList<>();

    private record Entry(String id, String label, boolean enabled) {}
    private record StatueDetails(String specialization, String count, String destinations) {}
    private record DestinationDetails(String dimension, String x, String y, String z,
                                      String yaw, String pitch, String description) {}
    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
    private record UiButton(String label, int x, int y, int w, boolean enabled) {}

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
            if (line.startsWith("CATALOGUE PARTAGÉ")) {
                catalog = true;
                continue;
            }
            if (line.isBlank() || line.startsWith("STATUES —")) continue;
            int open = line.lastIndexOf('[');
            int close = line.lastIndexOf(']');
            if (open < 0 || close <= open) continue;
            Entry entry = new Entry(line.substring(open + 1, close),
                    line.substring(2, open).trim(), line.startsWith("● "));
            (catalog ? destinations : statues).add(entry);
        }
        if (selectedStatue.isBlank() && !statues.isEmpty()) selectedStatue = statues.get(0).id();
        if (selectedDestination.isBlank() && !destinations.isEmpty()) selectedDestination = destinations.get(0).id();
    }

    @Override
    protected void init() {
        clearWidgets();
        idInput = null;
        nameInput = null;
        descriptionInput = null;
        hits.clear();
        buttons.clear();

        pw = Math.min(560, width - 24);
        ph = Math.min(430, height - 20);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        innerX = px + 28;
        innerR = px + pw - 28;

        addHit(innerX, py + 46, 150, 22, () -> switchPage(Page.STATUES));
        addHit(innerX + 155, py + 46, 170, 22, () -> switchPage(Page.DESTINATIONS));
        addHit(innerX + 330, py + 46, 150, 22, () -> switchPage(Page.LINKS));
        addUiButton("↻", px + pw - 48, py + 15, 24, true,
                () -> PacketDistributor.sendToServer(new AdminActionPayload("refresh", "", "")));
        addUiButton("Fermer", width / 2 - 42, height - 26, 84, true, this::onClose);

        if (page == Page.STATUES) {
            nameInput = createField(innerX + 4, py + 101, pw - 64, 20, "Nom affiché", 64);
            addStatueControls();
        } else if (page == Page.DESTINATIONS) {
            idInput = createField(innerX + 4, py + 100, 166, 20, "identifiant", 64);
            nameInput = createField(innerX + 180, py + 100, pw - 224, 20, "Nom affiché", 64);
            descriptionInput = createField(innerX + 4, py + 143, pw - 64, 20,
                    "Description de la destination", 256);
            addDestinationControls();
        } else {
            addLinkControls();
        }
    }

    private EditBox createField(int x, int y, int w, int h, String hint, int maxLength) {
        EditBox field = new EditBox(font, x, y, w, h, Component.literal(hint));
        field.setMaxLength(maxLength);
        field.setHint(Component.literal(hint));
        field.setBordered(false);
        field.setTextColor(INK);
        field.setTextColorUneditable(INK_LIGHT);
        addRenderableWidget(field);
        return field;
    }

    private void addStatueControls() {
        drawSelectableList(statues, innerX + 4, py + 162, pw - 64, true, PAGE_SIZE);
        int y = py + 300;
        addUiButton("Créer une statue ici", innerX + 4, y, pw - 64, nameInput != null,
                () -> {
                    String name = nameInput.getValue().trim();
                    if (!name.isBlank()) send("statue-create-here", name, "");
                });
        y += 27;
        addUiButton("Renommer la statue", innerX + 4, y, 220,
                !selectedStatue.isBlank() && nameInput != null,
                () -> {
                    if (!selectedStatue.isBlank() && !nameInput.getValue().isBlank())
                        send("statue-rename", selectedStatue, nameInput.getValue().trim());
                });
        Entry selected = find(statues, selectedStatue);
        addUiButton(selected != null && selected.enabled() ? "Désactiver" : "Activer",
                innerX + 231, y, pw - 291, selected != null,
                () -> {
                    Entry current = find(statues, selectedStatue);
                    if (current != null)
                        send(current.enabled() ? "statue-disable" : "statue-enable", selectedStatue, "");
                });
        y += 27;
        addUiButton(pendingConfirmation.equals("statue") ? "Confirmer la suppression" : "Supprimer la statue",
                innerX + 4, y, pw - 64, !selectedStatue.isBlank(),
                () -> confirmDelete("statue", () -> send("statue-remove", selectedStatue, "")));
    }

    private void addDestinationControls() {
        drawSelectableList(destinations, innerX + 4, py + 204, pw - 64, false, 3);
        int y = py + 300;
        addUiButton("Créer à ma position", innerX + 4, y, 220,
                idInput != null && nameInput != null,
                () -> {
                    String id = idInput.getValue().trim().toLowerCase(Locale.ROOT);
                    String name = nameInput.getValue().trim();
                    if (!id.isBlank() && !name.isBlank()) send("catalog-create-here", name, id);
                });
        addUiButton("Déplacer ici", innerX + 231, y, pw - 291, !selectedDestination.isBlank(),
                () -> {
                    if (!selectedDestination.isBlank())
                        send("catalog-move-here", "", selectedDestination);
                });
        y += 27;
        addUiButton("Renommer", innerX + 4, y, 220,
                !selectedDestination.isBlank() && nameInput != null,
                () -> {
                    if (!selectedDestination.isBlank() && !nameInput.getValue().isBlank())
                        send("catalog-rename", nameInput.getValue().trim(), selectedDestination);
                });
        addUiButton("Enregistrer la description", innerX + 231, y, pw - 291,
                !selectedDestination.isBlank() && descriptionInput != null,
                () -> {
                    if (!selectedDestination.isBlank())
                        send("catalog-description", descriptionInput.getValue(), selectedDestination);
                });
        y += 27;
        Entry selected = find(destinations, selectedDestination);
        addUiButton(selected != null && selected.enabled() ? "Désactiver" : "Activer",
                innerX + 4, y, 220, selected != null,
                () -> {
                    Entry current = find(destinations, selectedDestination);
                    if (current != null)
                        send(current.enabled() ? "destination-disable" : "destination-enable", "",
                                selectedDestination);
                });
        addUiButton(pendingConfirmation.equals("destination") ? "Confirmer la suppression" : "Supprimer",
                innerX + 231, y, pw - 291, !selectedDestination.isBlank(),
                () -> confirmDelete("destination",
                        () -> send("catalog-remove", "", selectedDestination)));
    }

    private void addLinkControls() {
        int half = (pw - 76) / 2;
        int left = innerX + 4;
        int right = left + half + 12;
        drawCompactList(statues, left, py + 151, half, true);
        drawCompactList(destinations, right, py + 151, half, false);
        addUiButton("Attribuer la destination", innerX + 4, py + 300, pw - 64,
                !selectedStatue.isBlank() && !selectedDestination.isBlank(),
                () -> send("destination-assign", selectedStatue, selectedDestination));
        addUiButton("Retirer cette destination", innerX + 4, py + 327, pw - 64,
                !selectedStatue.isBlank() && !selectedDestination.isBlank(),
                () -> send("destination-unassign", selectedStatue, selectedDestination));
    }

    private void drawSelectableList(List<Entry> entries, int x, int y, int w, boolean statue, int rows) {
        int pages = Math.max(1, (entries.size() + rows - 1) / rows);
        int pageIndex = Math.min(listPage, pages - 1);
        int start = pageIndex * rows;
        for (int i = 0; i < rows; i++) {
            int index = start + i;
            if (index >= entries.size()) break;
            Entry entry = entries.get(index);
            int rowY = y + i * 21;
            boolean selected = statue ? entry.id().equals(selectedStatue)
                    : entry.id().equals(selectedDestination);
            addHit(x, rowY, w, 19, () -> {
                if (statue) selectedStatue = entry.id();
                else {
                    selectedDestination = entry.id();
                    DestinationDetails detail = destinationDetails.get(entry.id());
                    if (detail != null && descriptionInput != null)
                        descriptionInput.setValue(detail.description());
                }
                refreshLayout();
            });
            if (selected) buttons.add(new UiButton("@selected", x, rowY, w, true));
            buttons.add(new UiButton((entry.enabled() ? "●  " : "○  ")
                    + trim(entry.label(), 58), x, rowY, w, true));
        }
        addUiButton("‹", x, y + rows * 21 + 2, 28, pageIndex > 0,
                () -> { listPage = Math.max(0, listPage - 1); refreshLayout(); });
        buttons.add(new UiButton((pageIndex + 1) + " / " + pages,
                x + 34, y + rows * 21 + 2, 60, false));
        addUiButton("›", x + 100, y + rows * 21 + 2, 28,
                (pageIndex + 1) < pages,
                () -> { if ((listPage + 1) < pages) listPage++; refreshLayout(); });
    }

    private void drawCompactList(List<Entry> entries, int x, int y, int w, boolean statue) {
        List<Entry> visible = entries.subList(0, Math.min(PAGE_SIZE, entries.size()));
        for (int i = 0; i < visible.size(); i++) {
            Entry entry = visible.get(i);
            int rowY = y + i * 22;
            boolean selected = statue ? entry.id().equals(selectedStatue)
                    : entry.id().equals(selectedDestination);
            addHit(x, rowY, w, 20, () -> {
                if (statue) selectedStatue = entry.id();
                else selectedDestination = entry.id();
                refreshLayout();
            });
            if (selected) buttons.add(new UiButton("@selected", x, rowY, w, true));
            buttons.add(new UiButton((entry.enabled() ? "● " : "○ ")
                    + trim(entry.label(), 25), x, rowY, w, true));
        }
    }

    private void addUiButton(String label, int x, int y, int w, boolean enabled, Runnable action) {
        buttons.add(new UiButton(label, x, y, w, enabled));
        if (enabled) addHit(x, y, w, 20, action);
    }

    private void addHit(int x, int y, int w, int h, Runnable action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    private void switchPage(Page next) {
        page = next;
        listPage = 0;
        pendingConfirmation = "";
        refreshLayout();
    }

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
        if (!kind.equals(pendingConfirmation)) {
            pendingConfirmation = kind;
            refreshLayout();
        } else {
            pendingConfirmation = "";
            delete.run();
        }
    }

    private void send(String action, String statueId, String destinationId) {
        if ((action.startsWith("statue-") && statueId.isBlank())
                || ((action.equals("destination-assign") || action.equals("destination-unassign"))
                    && (statueId.isBlank() || destinationId.isBlank()))
                || ((action.equals("destination-enable") || action.equals("destination-disable"))
                    && destinationId.isBlank())
                || (action.startsWith("catalog-") && !action.equals("catalog-create-here")
                    && destinationId.isBlank())) return;
        PacketDistributor.sendToServer(new AdminActionPayload(action, statueId, destinationId));
    }

    private Entry find(List<Entry> entries, String id) {
        return entries.stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
    }

    private static String trim(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Render the vanilla background and widgets first. The parchment must be drawn
        // afterwards; otherwise Screen.render blurs/washes out the custom HUD.
        super.render(g, mouseX, mouseY, partialTick);
        drawParchment(g);

        String title = "CITIZEN • ADMINISTRATION";
        g.drawString(font, title, px + (pw - font.width(title)) / 2, py + 22, RED, false);
        g.fill(innerX + 15, py + 36, innerR - 15, py + 37, GOLD);
        drawText(g, "Administration des statues guides", innerX + 4, py + 80, INK_LIGHT);

        drawTabs(g, mouseX, mouseY);
        drawButtons(g, mouseX, mouseY);
        drawFields(g);
        drawPageContent(g, mouseX, mouseY);

        // The initial super.render() is covered by the parchment above.
        // Re-render only editable text fields so their text/cursor stay visible.
        renderInputOnTop(g, idInput, mouseX, mouseY, partialTick);
        renderInputOnTop(g, nameInput, mouseX, mouseY, partialTick);
        renderInputOnTop(g, descriptionInput, mouseX, mouseY, partialTick);
    }

    private void renderInputOnTop(GuiGraphics g, EditBox field, int mouseX, int mouseY, float partialTick) {
        if (field != null) field.render(g, mouseX, mouseY, partialTick);
    }

    private void drawParchment(GuiGraphics g) {
        int roll = Math.min(22, Math.max(14, Math.round(ph * ROLL / 256f)));
        int bodyTop = py + roll;
        int bodyBottom = py + ph - roll;
        g.blit(BG, px, py, pw, roll, 0f, 0f, 256, ROLL, 256, 256);
        g.blit(BG, px, bodyTop, pw, bodyBottom - bodyTop, 0f, (float) ROLL,
                256, 256 - 2 * ROLL, 256, 256);
        g.blit(BG, px, bodyBottom, pw, roll, 0f, (float) (256 - ROLL),
                256, ROLL, 256, 256);
    }

    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        drawTab(g, "Statues", innerX, py + 46, 150, page == Page.STATUES,
                mouseX, mouseY);
        drawTab(g, "Destinations", innerX + 155, py + 46, 170, page == Page.DESTINATIONS,
                mouseX, mouseY);
        drawTab(g, "Associations", innerX + 330, py + 46, 150, page == Page.LINKS,
                mouseX, mouseY);
    }

    private void drawTab(GuiGraphics g, String label, int x, int y, int w,
                         boolean selected, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 22;
        if (selected) g.fill(x, y + 19, x + w, y + 21, RED);
        else if (hover) g.fill(x + 2, y + 19, x + w - 2, y + 20, GOLD);
        int color = selected ? RED : INK;
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 6, color, false);
    }

    private void drawButtons(GuiGraphics g, int mouseX, int mouseY) {
        for (UiButton b : buttons) {
            if (b.label().equals("@selected")) {
                g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + 19, SELECTED);
                g.fill(b.x(), b.y() + 18, b.x() + b.w(), b.y() + 19, GOLD);
                continue;
            }
            boolean hover = b.enabled() && mouseX >= b.x() && mouseX < b.x() + b.w()
                    && mouseY >= b.y() && mouseY < b.y() + 20;
            int color = b.enabled() ? (hover ? RED : INK) : INK_LIGHT;
            if (hover) g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + 20, HOVER);
            g.fill(b.x(), b.y() + 19, b.x() + b.w(), b.y() + 20, GOLD);
            g.drawString(font, b.label(), b.x() + (b.w() - font.width(b.label())) / 2,
                    b.y() + 6, color, false);
        }
    }

    private void drawFields(GuiGraphics g) {
        if (page == Page.STATUES) {
            drawFieldFrame(g, nameInput, "NOM DE LA STATUE");
        } else if (page == Page.DESTINATIONS) {
            drawFieldFrame(g, idInput, "IDENTIFIANT");
            drawFieldFrame(g, nameInput, "NOM AFFICHÉ");
            drawFieldFrame(g, descriptionInput, "DESCRIPTION");
        }
    }

    private void drawFieldFrame(GuiGraphics g, EditBox field, String label) {
        if (field == null) return;
        int x = field.getX() - 4;
        int y = field.getY() - 2;
        int w = field.getWidth() + 8;
        int h = field.getHeight() + 4;
        g.drawString(font, label, x, y - 12, INK_LIGHT, false);
        g.fill(x, y, x + w, y + h, 0x187A4B20);
        g.fill(x, y, x + w, y + 1, GOLD);
        g.fill(x, y + h - 1, x + w, y + h, GOLD);
        g.fill(x, y, x + 1, y + h, GOLD);
        g.fill(x + w - 1, y, x + w, y + h, GOLD);
    }

    private void drawPageContent(GuiGraphics g, int mouseX, int mouseY) {
        if (page == Page.STATUES) {
            drawText(g, "PNJ GUIDES", innerX + 4, py + 137, RED);
            StatueDetails detail = statueDetails.get(selectedStatue);
            if (detail != null) {
                drawText(g, "Spécialisation : " + detail.specialization()
                        + "  •  " + detail.count() + " destination(s)",
                        innerX + 4, py + 382, INK_LIGHT);
                if (!detail.destinations().isBlank())
                    drawText(g, "Liées : " + trim(detail.destinations(), 75),
                            innerX + 4, py + 396, INK_LIGHT);
            }
        } else if (page == Page.DESTINATIONS) {
            drawText(g, "CATALOGUE PARTAGÉ", innerX + 4, py + 184, RED);
            DestinationDetails detail = destinationDetails.get(selectedDestination);
            if (detail != null) {
                drawText(g, trim(detail.dimension() + "  •  " + detail.x() + " / "
                        + detail.y() + " / " + detail.z(), 92),
                        innerX + 4, py + 390, INK_LIGHT);
            }
        } else {
            drawText(g, "STATUES", innerX + 4, py + 128, RED);
            int half = (pw - 76) / 2;
            drawText(g, "DESTINATIONS", innerX + 4 + half + 12, py + 128, RED);
            StatueDetails detail = statueDetails.get(selectedStatue);
            if (detail != null)
                drawText(g, "Destinations liées : " + detail.count(),
                        innerX + 4, py + 280, INK_LIGHT);
        }
    }

    private void drawText(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Hit hit : hits) {
                if (hit.contains(mouseX, mouseY)) {
                    hit.action().run();
                    return true;
                }
            }
            if (mouseY >= py + 46 && mouseY <= py + 68) {
                if (mouseX >= innerX && mouseX < innerX + 150) {
                    switchPage(Page.STATUES);
                    return true;
                }
                if (mouseX >= innerX + 155 && mouseX < innerX + 325) {
                    switchPage(Page.DESTINATIONS);
                    return true;
                }
                if (mouseX >= innerX + 330 && mouseX < innerX + 480) {
                    switchPage(Page.LINKS);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}