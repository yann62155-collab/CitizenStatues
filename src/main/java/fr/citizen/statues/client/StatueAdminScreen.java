package fr.citizen.statues.client;

import fr.citizen.statues.network.AdminActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Interactive administration dashboard backed by server-authoritative actions. */
public final class StatueAdminScreen extends Screen {
    private final List<String> lines;
    private final List<Entry> statues = new ArrayList<>();
    private final List<Entry> destinations = new ArrayList<>();
    private final Map<String, DestinationDetails> destinationDetails = new LinkedHashMap<>();
    private final Map<String, StatueDetails> statueDetails = new LinkedHashMap<>();
    private String selectedStatue = "";
    private String selectedDestination = "";
    private String pendingConfirmation = "";
    private EditBox newDestinationId;
    private EditBox newDestinationName;
    private EditBox destinationDescription;
    private int statuePage = 0;
    private int destinationPage = 0;

    private record Entry(String id, String label, boolean enabled) {}
    private record StatueDetails(String specialization, String count, String destinations) {}
    private record DestinationDetails(String dimension, String x, String y, String z,
                                     String yaw, String pitch, String description) {}

    public StatueAdminScreen(List<String> lines) {
        super(Component.literal("Citizen — Administration"));
        this.lines = List.copyOf(lines);
        parseEntries();
    }

    private void parseEntries() {
        boolean catalog = false;
        for (String line : lines) {
            if (line.startsWith("STATUEDETAIL|")) {
                String[] parts = line.split("\\|", 5);
                if (parts.length == 5) statueDetails.put(parts[1], new StatueDetails(parts[2], parts[3], parts[4]));
                continue;
            }
            if (line.startsWith("DETAIL|")) {
                String[] parts = line.split("\\|", 9);
                if (parts.length == 9) destinationDetails.put(parts[1], new DestinationDetails(
                        parts[2], parts[3], parts[4], parts[5], parts[6], parts[7], parts[8]));
                continue;
            }
            if (line.startsWith("CATALOGUE PARTAGÉ")) { catalog = true; continue; }
            if (line.isBlank() || line.startsWith("STATUES —")) continue;
            int open = line.lastIndexOf('[');
            int close = line.lastIndexOf(']');
            if (open < 0 || close <= open) continue;
            String id = line.substring(open + 1, close);
            String label = line.substring(2, open).trim();
            Entry entry = new Entry(id, label, line.startsWith("● "));
            if (catalog) destinations.add(entry); else statues.add(entry);
        }
    }

    @Override
    protected void init() {
        int left = Math.max(8, width / 2 - 170);
        int listY = 130;
        addRenderableWidget(Button.builder(Component.literal("Fermer"), button -> onClose())
                .bounds(width / 2 - 40, height - 28, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Actualiser"), button ->
                        PacketDistributor.sendToServer(new AdminActionPayload("refresh", "", "")))
                .bounds(width / 2 - 48, 30, 96, 20).build());

        newDestinationId = new EditBox(font, left, 54, 164, 20, Component.literal("Identifiant"));
        newDestinationId.setMaxLength(64);
        newDestinationId.setHint(Component.literal("ex: spawn_village"));
        addRenderableWidget(newDestinationId);
        newDestinationName = new EditBox(font, left + 174, 54, 164, 20, Component.literal("Nom"));
        newDestinationName.setMaxLength(64);
        newDestinationName.setHint(Component.literal("Nom affiché"));
        addRenderableWidget(newDestinationName);
        destinationDescription = new EditBox(font, left, 79, 338, 20, Component.literal("Description"));
        destinationDescription.setMaxLength(256);
        destinationDescription.setHint(Component.literal("Description de la destination"));
        DestinationDetails selectedDetails = destinationDetails.get(selectedDestination);
        if (selectedDetails != null) destinationDescription.setValue(selectedDetails.description());
        addRenderableWidget(destinationDescription);

        graphicsLabel("STATUES", left + 8, 117);
        graphicsLabel("DESTINATIONS", left + 182, 117);

        int statueStart = statuePage * 5;
        for (int i = 0; i < Math.min(5, Math.max(0, statues.size() - statueStart)); i++) {
            Entry entry = statues.get(statueStart + i);
            Button b = Button.builder(Component.literal((entry.enabled() ? "● " : "○ ") + trim(entry.label(), 19)),
                    button -> { selectedStatue = entry.id(); pendingConfirmation = ""; refreshWidgetLayout(); })
                    .bounds(left, listY + i * 21, 164, 19).build();
            b.active = !entry.id().equals(selectedStatue);
            addRenderableWidget(b);
        }
        int destinationStart = destinationPage * 5;
        for (int i = 0; i < Math.min(5, Math.max(0, destinations.size() - destinationStart)); i++) {
            Entry entry = destinations.get(destinationStart + i);
            Button b = Button.builder(Component.literal((entry.enabled() ? "● " : "○ ") + trim(entry.label(), 19)),
                    button -> { selectedDestination = entry.id(); pendingConfirmation = ""; if (destinationDescription != null) destinationDescription.setValue(""); refreshWidgetLayout(); })
                    .bounds(left + 174, listY + i * 21, 164, 19).build();
            b.active = !entry.id().equals(selectedDestination);
            addRenderableWidget(b);
        }

        int pageY = listY + 5 * 21 + 3;
        addRenderableWidget(Button.builder(Component.literal("◀"), button -> {
                    statuePage = Math.max(0, statuePage - 1); refreshWidgetLayout();
                }).bounds(left, pageY, 35, 18).build());
        addRenderableWidget(Button.builder(Component.literal((statuePage + 1) + " / " + Math.max(1, (statues.size() + 4) / 5)),
                button -> {}).bounds(left + 39, pageY, 90, 18).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), button -> {
                    if ((statuePage + 1) * 5 < statues.size()) statuePage++; refreshWidgetLayout();
                }).bounds(left + 133, pageY, 31, 18).build());
        addRenderableWidget(Button.builder(Component.literal("◀"), button -> {
                    destinationPage = Math.max(0, destinationPage - 1); refreshWidgetLayout();
                }).bounds(left + 174, pageY, 35, 18).build());
        addRenderableWidget(Button.builder(Component.literal((destinationPage + 1) + " / " + Math.max(1, (destinations.size() + 4) / 5)),
                button -> {}).bounds(left + 213, pageY, 90, 18).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), button -> {
                    if ((destinationPage + 1) * 5 < destinations.size()) destinationPage++; refreshWidgetLayout();
                }).bounds(left + 307, pageY, 31, 18).build());

        int actionY = pageY + 23;
        addRenderableWidget(action("Activer statue", left, actionY, 164, () -> send("statue-enable", selectedStatue, "")));
        addRenderableWidget(action("Désactiver statue", left + 174, actionY, 164, () -> send("statue-disable", selectedStatue, "")));
        actionY += 22;
        addRenderableWidget(action("Activer destination", left, actionY, 164, () -> send("destination-enable", "", selectedDestination)));
        addRenderableWidget(action("Désactiver destination", left + 174, actionY, 164, () -> send("destination-disable", "", selectedDestination)));
        actionY += 22;
        addRenderableWidget(action("Attribuer à la statue", left, actionY, 164, () -> send("destination-assign", selectedStatue, selectedDestination)));
        addRenderableWidget(action("Retirer de la statue", left + 174, actionY, 164, () -> send("destination-unassign", selectedStatue, selectedDestination)));
        actionY += 22;
        addRenderableWidget(action("Renommer", left, actionY, 164, () -> {
            String name = newDestinationName.getValue().trim();
            if (!selectedDestination.isBlank() && !name.isBlank()) send("catalog-rename", name, selectedDestination);
        }));
        addRenderableWidget(action("Enregistrer description", left + 174, actionY, 164, () -> {
            if (!selectedDestination.isBlank()) send("catalog-description", destinationDescription.getValue(), selectedDestination);
        }));
        actionY += 22;
        addRenderableWidget(action("Déplacer ici", left, actionY, 164, () -> {
            if (!selectedDestination.isBlank()) send("catalog-move-here", "", selectedDestination);
        }));
        addRenderableWidget(action("Créer à ma position", left + 174, actionY, 164, () -> {
            String id = newDestinationId.getValue().trim().toLowerCase(java.util.Locale.ROOT);
            String name = newDestinationName.getValue().trim();
            if (!id.isBlank() && !name.isBlank()) send("catalog-create-here", name, id);
        }));
        actionY += 22;
        addRenderableWidget(action("Renommer statue", left, actionY, 164, () -> {
            String name = newDestinationName.getValue().trim();
            if (!selectedStatue.isBlank() && !name.isBlank()) send("statue-rename", selectedStatue, name);
        }));
        addRenderableWidget(action(pendingConfirmation.equals("statue") ? "Confirmer suppression" : "Supprimer statue",
                left + 174, actionY, 164, () -> {
                    if (!pendingConfirmation.equals("statue")) { pendingConfirmation = "statue"; refreshWidgetLayout(); }
                    else { pendingConfirmation = ""; send("statue-remove", selectedStatue, ""); }
                }));
        actionY += 22;
        addRenderableWidget(action(pendingConfirmation.equals("destination") ? "Confirmer suppression" : "Supprimer destination",
                left, actionY, 338, () -> {
                    if (!pendingConfirmation.equals("destination")) { pendingConfirmation = "destination"; refreshWidgetLayout(); }
                    else { pendingConfirmation = ""; send("catalog-remove", "", selectedDestination); }
                }));
    }

    private void graphicsLabel(String text, int x, int y) {
        // Labels are painted in render(); this method intentionally keeps widget layout independent.
    }

    private Button action(String label, int x, int y, int w, Runnable callback) {
        Button button = Button.builder(Component.literal(label), b -> callback.run())
                .bounds(x, y, w, 20).build();
        String key = label.toLowerCase(java.util.Locale.ROOT);
        if (key.contains("créer")) button.active = true;
        else if (key.contains("attribuer") || key.contains("retirer"))
            button.active = !selectedStatue.isBlank() && !selectedDestination.isBlank();
        else if (key.contains("statue")) button.active = !selectedStatue.isBlank();
        else button.active = !selectedDestination.isBlank();
        return button;
    }

    private void send(String action, String statueId, String destinationId) {
        if (action.startsWith("statue-") && statueId.isBlank()) return;
        if (action.startsWith("destination-") && destinationId.isBlank()) return;
        PacketDistributor.sendToServer(new AdminActionPayload(action, statueId, destinationId));
    }

    private void refreshWidgetLayout() {
        String id = newDestinationId == null ? "" : newDestinationId.getValue();
        String name = newDestinationName == null ? "" : newDestinationName.getValue();
        String description = destinationDescription == null ? "" : destinationDescription.getValue();
        clearWidgets();
        init();
        newDestinationId.setValue(id);
        newDestinationName.setValue(name);
        if (!description.isBlank()) destinationDescription.setValue(description);
    }

    private static String shortName(String value) {
        return value.isBlank() ? "—" : value.substring(0, Math.min(8, value.length()));
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int panelWidth = Math.min(370, width - 16);
        int left = (width - panelWidth) / 2;
        graphics.fill(left, 18, left + panelWidth, height - 18, 0xE8101724);
        graphics.drawCenteredString(font, title, width / 2, 22, 0xFFFFFFFF);
        graphics.drawString(font, "STATUES", left + 8, 117, 0xFF72C7FF, false);
        graphics.drawString(font, "DESTINATIONS", left + 182, 117, 0xFF72C7FF, false);
        graphics.drawString(font, "ID et nom : crée ou renomme une destination. Description ci-dessous.",
                left + 8, 42, 0xFFB8C4D6, false);
        DestinationDetails details = destinationDetails.get(selectedDestination);
        if (details != null) {
            int detailY = Math.max(18, height - 82);
            graphics.drawString(font, trim("Dimension : " + details.dimension(), 48), left + 8, detailY, 0xFFFFFFFF, false);
            graphics.drawString(font, trim("XYZ : " + details.x() + " / " + details.y() + " / " + details.z(), 48),
                    left + 8, detailY + 12, 0xFFB8C4D6, false);
            graphics.drawString(font, "Orientation : " + details.yaw() + " / " + details.pitch(),
                    left + 8, detailY + 24, 0xFFB8C4D6, false);
        } else {
            graphics.drawString(font, "Sélectionne une destination pour afficher ses détails.",
                    left + 8, height - 43, 0xFFB8C4D6, false);
        }
        StatueDetails statue = statueDetails.get(selectedStatue);
        if (statue != null) {
            int statueY = Math.max(18, height - 126);
            graphics.drawString(font, "Statue : " + trim(statue.specialization(), 24)
                    + " • " + statue.count() + " destination(s)", left + 8, statueY, 0xFF72C7FF, false);
            if (!statue.destinations().isBlank()) graphics.drawString(font,
                    trim("Attribuées : " + statue.destinations(), 55), left + 8, statueY + 12,
                    0xFFB8C4D6, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
