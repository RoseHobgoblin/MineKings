package com.minekings.minekings.client.hub;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Sub-component rendered inside a centered panel region on the HubScreen.
 * Not a Screen — owns no keyboard focus, no child widgets of its own;
 * the parent HubScreen dispatches render and input.
 */
public abstract class HubPanel {
    protected int x, y, w, h;

    public void init(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
    }

    public abstract String title();
    public abstract void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick);

    /** Called when this panel becomes active. Panels that need server data should request it here. */
    public void onActivated() {}

    /** Called when panel is closed. */
    public void onClosed() {}

    public boolean mouseClicked(double mx, double my, int button) { return false; }
}
