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

/** Interactive administration dashboard backed by server-authoritative actions. */
public final class StatueAdminScreen extends Screen {
    private final List<String> lines;
    private final List<Entry> statues = new ArrayList<>();
    private final List<Entry> destinations = new ArrayList<>();
    private String selectedStatue = "";
    private String selectedDestination = "";
    private EditBox newDestinationId;
    private EditBox newDestinationName;
    private EditBox destinationDescription;
    private int statuePage = 0;
    private int destinationPage = 0;

    private record Entry(String id, String label, boolean enabled) {}

    public StatueAdminScreen(List<String> lines) {
        super(Component.literal("Citizen — Administration"));
        this.lines = List.copyOf(lines);
        parseEntries();
    }

    private void parseEntries() {
        boolean catalog = false;
        for (String line : lines) {
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
        int y = 130;
        addRenderableWidget(Button.builder(Component.literal("Fermer"), button -> onClose())
                .bounds(width / 2 - 40, height - 28, 80, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Actualiser"), button ->
                        PacketDistributor.sendToServer(new AdminActionPayload("refresh", "", "")))
                .bounds(width / 2 - 48, 30, 96, 20).build());

        newDestinationId = new EditBox(font, left, 54, 164, 20, Component.literal("Identifiant"));
        newDestinationId.setMaxLength(64);
        newDestinationId.setHint(Component.literal("ex: spawn_village"));
        addRenderableWidget(newDestinationId);
        newDestinationName = new EditBox(font, left + 174, 54, 164, 20, Component.literal("Nom destination"));
        newDestinationName.setMaxLength(64);
        newDestinationName.setHint(Component.literal("Nom affiché"));
        addRenderableWidget(newDestinationName);
        destinationDescription = new EditBox(font, left, 79, 338, 20, Component.literal("Description"));
        destinationDescription.setMaxLength(256);
        destinationDescription.setHint(Component.literal("Description de la destination (facultatif)"));
        addRenderableWidget(destinationDescription);

        addRenderableWidget(Button.builder(Component.literal("Statue sélectionnée : "
                        + shortName(selectedStatue)), button -> {}).bounds(left, y, 164, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Destination : "
                        + shortName(selectedDestination)), button -> {}).bounds(left + 174, y, 164, 20).build());
        y += 25;

        int statueStart = statuePage * 5;
        int count = Math.min(5, Math.max(0, statues.size() - statueStart));
        for (int i = 0; i < count; i++) {
            Entry entry = statues.get(statueStart + i);
            addRenderableWidget(Button.builder(Component.literal("S: " + trim(entry.label(), 20)),
                            button -> { selectedStatue = entry.id(); refreshWidgetLayout(); })
                    .bounds(left, y, 164, 20).build());
            y += 22;
        }
        int destinationY = 130;
        int destinationStart = destinationPage * 5;
        for (int i = 0; i < Math.min(5, Math.max(0, destinations.size() - destinationStart)); i++) {
            Entry entry = destinations.get(destinationStart + i);
            addRenderableWidget(Button.builder(Component.literal("D: " + trim(entry.label(), 20)),
                            button -> { selectedDestination = entry.id(); refreshWidgetLayout(); })
                    .bounds(left + 174, destinationY, 164, 20).build());
            destinationY += 22;
        }

        addRenderableWidget(Button.builder(Component.literal("◀ Statues"), button -> {
                    statuePage = Math.max(0, statuePage - 1); refreshWidgetLayout();
                }).bounds(left, y, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Statues ▶"), button -> {
                    if ((statuePage + 1) * 5 < statues.size()) statuePage++; refreshWidgetLayout();
                }).bounds(left + 84, y, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal("◀ Dest."), button -> {
                    destinationPage = Math.max(0, destinationPage - 1); refreshWidgetLayout();
                }).bounds(left + 174, y, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Dest. ▶"), button -> {
                    if ((destinationPage + 1) * 5 < destinations.size()) destinationPage++; refreshWidgetLayout();
                }).bounds(left + 258, y, 80, 18).build());
        int actionsY = Math.max(y + 21, destinationY + 5 * 22) + 4;
        addRenderableWidget(Button.builder(Component.literal("Activer statue"), button ->
                        send("statue-enable", selectedStatue, ""))
                .bounds(left, actionsY, 164, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Désactiver statue"), button ->
                        send("statue-disable", selectedStatue, ""))
                .bounds(left + 174, actionsY, 164, 20).build());
        actionsY += 23;
        addRenderableWidget(Button.builder(Component.literal("Activer destination"), button ->
                        send("destination-enable", "", selectedDestination))
                .bounds(left, actionsY, 164, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Désactiver destination"), button ->
                        send("destination-disable", "", selectedDestination))
                .bounds(left + 174, actionsY, 164, 20).build());
        actionsY += 23;
        addRenderableWidget(Button.builder(Component.literal("Attribuer à la statue"), button ->
                        send("destination-assign", selectedStatue, selectedDestination))
                .bounds(left, actionsY, 164, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Retirer de la statue"), button ->
                        send("destination-unassign", selectedStatue, selectedDestination))
                .bounds(left + 174, actionsY, 164, 20).build());
        actionsY += 25;
        addRenderableWidget(Button.builder(Component.literal("Renommer la destination sélectionnée"), button -> {
                    String name = newDestinationName.getValue().trim();
                    if (!selectedDestination.isBlank() && !name.isBlank())
                        send("catalog-rename", name, selectedDestination);
                }).bounds(left, actionsY, 338, 20).build());
        actionsY += 23;
        addRenderableWidget(Button.builder(Component.literal("Enregistrer la description"), button -> {
                    if (!selectedDestination.isBlank()) send("catalog-description", destinationDescription.getValue(), selectedDestination);
                }).bounds(left, actionsY, 338, 20).build());
        actionsY += 23;
        addRenderableWidget(Button.builder(Component.literal("Déplacer la destination ici"), button -> {
                    if (!selectedDestination.isBlank()) send("catalog-move-here", "", selectedDestination);
                }).bounds(left, actionsY, 338, 20).build());
        actionsY += 23;
        addRenderableWidget(Button.builder(Component.literal("Créer ici dans le catalogue"), button -> {
                    String id = newDestinationId.getValue().trim().toLowerCase(java.util.Locale.ROOT);
                    String name = newDestinationName.getValue().trim();
                    if (!id.isBlank() && !name.isBlank()) send("catalog-create-here", name, id);
                }).bounds(left, actionsY, 338, 20).build());
    }

    private void send(String action, String statueId, String destinationId) {
        if (action.startsWith("statue-") && statueId.isBlank()) return;
        if (action.startsWith("destination-") && destinationId.isBlank()) return;
        PacketDistributor.sendToServer(new AdminActionPayload(action, statueId, destinationId));
    }

    private void refreshWidgetLayout() {
        clearWidgets();
        init();
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
        graphics.drawString(font, "CATALOGUE", left + 182, 117, 0xFF72C7FF, false);
        graphics.drawString(font, "ID et nom : crée ou renomme une destination. Description ci-dessous.",
                left + 8, 42, 0xFFB8C4D6, false);
        graphics.drawString(font, "Sélectionne une statue et une destination pour les associer.",
                left + 8, height - 43, 0xFFB8C4D6, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
