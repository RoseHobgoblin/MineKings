package com.minekings.minekings.client.hub.panel;

import com.minekings.minekings.client.hub.HubPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class KingdomPanel extends HubPanel {
    @Override public String title() { return "Kingdom"; }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        gfx.drawCenteredString(Minecraft.getInstance().font,
                "(realm tree — coming v0.8)",
                x + w / 2, y + h / 2, 0x888888);
    }
}
