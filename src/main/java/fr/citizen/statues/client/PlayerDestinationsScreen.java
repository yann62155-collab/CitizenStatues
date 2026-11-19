package fr.citizen.statues.client;

import fr.citizen.statues.network.PlayerTeleportPayload;
import net.minecraft.client.Minecraft;\nimport net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Searchable, categorized and paginated player-facing destination selector. */
public final class PlayerDestinationsScreen extends Screen {
    private static final int PAGE_SIZE = 4;
    private final String statueId;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Button> destinationButtons = new ArrayList<>();
    private EditBox search;
    private Button previous;
    private Button next;
    private int page;

    private record Entry(String id, String name, String description, String icon, String dimension) {
        String category() {
            if (dimension.contains("nether")) return "Nether";
            if (dimension.contains("end")) return "End";
            return "Monde principal";
        }
    }

    public PlayerDestinationsScreen(String statueId, String title, List<String> destinations) {
        super(Component.literal(title));
        this.statueId = statueId;
        for (String line : destinations) {
            String[] p = line.split("\\|", 5);
            if (p.length >= 2) entries.add(new Entry(p[0], p[1],
                    p.length > 2 ? p[2] : "", p.length > 3 ? p[3] : "compass",
                    p.length > 4 ? p[4] : "minecraft:overworld"));
        }
    }

    private List<Entry> filtered() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        return entries.stream().filter(e -> query.isEmpty()
                || e.name().toLowerCase(Locale.ROOT).contains(query)
                || e.description().toLowerCase(Locale.ROOT).contains(query)
                || e.category().toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    @Override
    protected void init() {
        clearWidgets();
        destinationButtons.clear();
        int panel = Math.min(340, width - 24);
        int left = (width - panel) / 2;
        search = new EditBox(font, left, 43, panel, 20, Component.literal("Rechercher"));
        search.setHint(Component.literal("Nom, description ou dimension..."));
        search.setResponder(value -> { page = 0; rebuildEntries(); });
        addRenderableWidget(search);

        previous = addRenderableWidget(Button.builder(Component.literal("← Précédent"),
                b -> { page = Math.max(0, page - 1); rebuildEntries(); })
                .bounds(left, height - 34, 105, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal("Suivant →"),
                b -> { page++; rebuildEntries(); }).bounds(left + panel - 105, height - 34, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("🎙 Parler"), b -> {
                    b.active = false;
                    VoiceRecognitionClient.start(statueId);
                    Minecraft.getInstance().setScreen(null);
                }).bounds(width / 2 - 55, height - 61, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose())
                .bounds(width / 2 - 42, height - 34, 80, 20).build());
        rebuildEntries();
    }

    private void rebuildEntries() {
        for (Button button : destinationButtons) removeWidget(button);
        destinationButtons.clear();
        List<Entry> filtered = filtered();
        int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pages - 1);
        int panel = Math.min(340, width - 24);
        int left = (width - panel) / 2;
        int start = page * PAGE_SIZE;
        for (int i = start; i < Math.min(start + PAGE_SIZE, filtered.size()); i++) {
            Entry entry = filtered.get(i);
            String label = "    " + trim(entry.name(), 34);
            Button button = Button.builder(Component.literal(label), b ->
                    PacketDistributor.sendToServer(new PlayerTeleportPayload(statueId, entry.id())))
                    .bounds(left, 76 + (i - start) * 37, panel, 24).build();
            destinationButtons.add(button);
            addRenderableWidget(button);
        }
        if (previous != null) previous.active = page > 0;
        if (next != null) next.active = page + 1 < pages;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFFFF);
        List<Entry> filtered = filtered();
        int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        graphics.drawCenteredString(font, Component.literal(filtered.size() + " destination(s) • Page "
                + (page + 1) + "/" + pages), width / 2, 29, 0xFFB8C8D8);
        int start = page * PAGE_SIZE;
        for (int i = start; i < Math.min(start + PAGE_SIZE, filtered.size()); i++) {
            Entry entry = filtered.get(i);
            int y = 101 + (i - start) * 37;
            String category = entry.category() + (entry.description().isBlank() ? "" : " • " + entry.description());
            int left = (width - Math.min(340, width - 24)) / 2;
            String clipped = font.plainSubstrByWidth(category, Math.max(40, Math.min(324, width - 40)));
            graphics.drawString(font, Component.literal(clipped), left + 8, y, 0xFFB0B0B0, false);
            int rowTop = 76 + (i - start) * 37;
            if (!entry.description().isBlank() && mouseX >= left && mouseX <= left + Math.min(340, width - 24)
                    && mouseY >= rowTop && mouseY <= rowTop + 24) {
                graphics.renderTooltip(font, Component.literal(entry.description()), mouseX, mouseY);
            }

        }
        if (filtered.isEmpty()) graphics.drawCenteredString(font,
                Component.literal(entries.isEmpty() ? "Aucune destination disponible." : "Aucun résultat."),
                width / 2, height / 2, 0xFFAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
        for (int i = start; i < Math.min(start + PAGE_SIZE, filtered.size()); i++) {
            Entry entry = filtered.get(i);
            ItemStack stack = iconStack(entry.icon());
            if (!stack.isEmpty()) graphics.renderItem(stack,
                    (width - Math.min(340, width - 24)) / 2 + 5, 79 + (i - start) * 37);
        }
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private static ItemStack iconStack(String icon) {
        if (icon == null || icon.isBlank()) return ItemStack.EMPTY;
        String key = icon.contains(":") ? icon : "minecraft:" + icon;
        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id == null) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override public boolean isPauseScreen() { return false; }
}
