package com.minekings.minekings.client.hub;

import com.minekings.minekings.client.hub.panel.CharacterPanel;
import com.minekings.minekings.client.hub.panel.ClanPanel;
import com.minekings.minekings.client.hub.panel.EncyclopediaPanel;
import com.minekings.minekings.client.hub.panel.KingdomPanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Bannerlord-style hub. The map is the persistent backdrop; tabs along
 * the bottom toggle overlay panels centered on the screen. Re-clicking
 * an active tab dismisses its panel (back to bare map). Return closes
 * the hub entirely.
 *
 * <p>v0.7 ships with a placeholder map (faint grid) and a populated
 * Clan panel; Character/Kingdom/Encyclopedia are stubs.
 */
public class HubScreen extends Screen {
    private static final int TAB_ROW_HEIGHT = 28;
    private static final int TAB_WIDTH = 108;
    private static final int TAB_GAP = 4;
    private static final int RETURN_WIDTH = 80;

    // Panel bounds (populated in init based on current window size)
    private int panelX, panelY, panelW, panelH;

    // Tab instances
    private final HubPanel characterPanel = new CharacterPanel();
    private final HubPanel clanPanel = new ClanPanel();
    private final HubPanel kingdomPanel = new KingdomPanel();
    private final HubPanel encyclopediaPanel = new EncyclopediaPanel();

    private @Nullable HubPanel activePanel = null;

    // Instance handle so network handlers can dispatch back to the open hub.
    private static @Nullable HubScreen CURRENT = null;

    public HubScreen() {
        super(Component.translatable("screen.minekings.hub"));
    }

    public static @Nullable HubScreen current() {
        return CURRENT;
    }

    @Override
    protected void init() {
        super.init();
        CURRENT = this;

        int tabRowY = this.height - TAB_ROW_HEIGHT - 4;
        int totalWidth = 4 * TAB_WIDTH + 3 * TAB_GAP;
        int startX = (this.width - totalWidth - RETURN_WIDTH - 20) / 2;

        addTabButton(startX,                                    tabRowY, characterPanel,    "Character");
        addTabButton(startX + (TAB_WIDTH + TAB_GAP) * 1,        tabRowY, clanPanel,         "Clan");
        addTabButton(startX + (TAB_WIDTH + TAB_GAP) * 2,        tabRowY, kingdomPanel,      "Kingdom");
        addTabButton(startX + (TAB_WIDTH + TAB_GAP) * 3,        tabRowY, encyclopediaPanel, "Encyclopedia");

        int returnX = startX + totalWidth + 20;
        addRenderableWidget(Button.builder(Component.literal("Return"), b -> onClose())
                .bounds(returnX, tabRowY, RETURN_WIDTH, 20)
                .build());

        // Panel region: centered upper 2/3 of the screen, above the tab row.
        this.panelW = Math.min(this.width - 80, 560);
        this.panelH = this.height - TAB_ROW_HEIGHT - 40;
        this.panelX = (this.width - panelW) / 2;
        this.panelY = 20;

        characterPanel.init(panelX, panelY, panelW, panelH);
        clanPanel.init(panelX, panelY, panelW, panelH);
        kingdomPanel.init(panelX, panelY, panelW, panelH);
        encyclopediaPanel.init(panelX, panelY, panelW, panelH);
    }

    private void addTabButton(int x, int y, HubPanel panel, String label) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> toggle(panel))
                .bounds(x, y, TAB_WIDTH, 20)
                .build());
    }

    private void toggle(HubPanel panel) {
        if (activePanel == panel) {
            activePanel.onClosed();
            activePanel = null;
            return;
        }
        if (activePanel != null) activePanel.onClosed();
        activePanel = panel;
        activePanel.onActivated();
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderMapBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);

        if (activePanel != null) {
            // Dim the map where the panel sits.
            gfx.fill(panelX - 4, panelY - 4, panelX + panelW + 4, panelY + panelH + 4, 0xCC000000);
            // Title bar
            gfx.drawCenteredString(this.font, activePanel.title(),
                    panelX + panelW / 2, panelY + 4, 0xFFAA00);
            gfx.fill(panelX, panelY + 16, panelX + panelW, panelY + 17, 0x55FFFFFF);

            activePanel.render(gfx, mouseX, mouseY, partialTick);
        }
    }

    /** Placeholder map: dark parchment background with a faint grid. */
    private void renderMapBackground(GuiGraphics gfx) {
        gfx.fill(0, 0, this.width, this.height, 0xFF1A1612);
        int grid = 32;
        int gridColor = 0x22FFFFFF;
        for (int x = 0; x < this.width; x += grid) {
            gfx.fill(x, 0, x + 1, this.height, gridColor);
        }
        for (int y = 0; y < this.height; y += grid) {
            gfx.fill(0, y, this.width, y + 1, gridColor);
        }
        gfx.drawCenteredString(this.font, "(map — v0.9)",
                this.width / 2, this.height / 2, 0x55FFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (activePanel != null && activePanel.mouseClicked(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256 && activePanel != null) { // ESC dismisses panel first
            activePanel.onClosed();
            activePanel = null;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        CURRENT = null;
        super.onClose();
    }

    public @Nullable HubPanel getActivePanel() { return activePanel; }
    public ClanPanel getClanPanel() { return (ClanPanel) clanPanel; }
}
