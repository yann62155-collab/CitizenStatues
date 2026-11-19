package fr.citizen.statues.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** First read-only administration screen; editing controls are added on top of the secure snapshot flow. */
public final class StatueAdminScreen extends Screen {
    private final List<String> lines;

    public StatueAdminScreen(List<String> lines) {
        super(Component.literal("Citizen — Administration"));
        this.lines = List.copyOf(lines);
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Fermer"), button -> onClose())
                .bounds(width / 2 - 45, height - 34, 90, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int panelWidth = Math.min(360, width - 24);
        int left = (width - panelWidth) / 2;
        graphics.fill(left, 18, left + panelWidth, height - 48, 0xE8101724);
        graphics.drawCenteredString(font, title, width / 2, 29, 0xFFFFFFFF);
        int y = 54;
        for (String line : lines) {
            if (y > height - 58) break;
            graphics.drawString(font, line, left + 14, y, 0xFFE5EAF2, false);
            y += 13;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
