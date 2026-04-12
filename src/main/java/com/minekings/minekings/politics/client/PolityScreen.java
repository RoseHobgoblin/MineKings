package com.minekings.minekings.politics.client;

import com.minekings.minekings.politics.PolityViewPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Read-only info screen for a polity. Opened by right-clicking a bound
 * leader villager. Displays the polity's political state and economic
 * summary as a simple text panel — no buttons, no inputs.
 *
 * <p>Data is a snapshot carried by {@link PolityViewPayload}; close and
 * reopen for fresh data. No ongoing sync.
 */
public class PolityScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int LABEL_COLOR = 0xAAAAAA;
    private static final int GOLD_COLOR = 0xFFAA00;
    private static final int SEPARATOR_COLOR = 0x555555;

    private final PolityViewPayload data;
    private int leftX;
    private int topY;

    public PolityScreen(PolityViewPayload data) {
        super(Component.literal(data.displayName()));
        this.data = data;
    }

    @Override
    protected void init() {
        super.init();
        this.leftX = (this.width - PANEL_WIDTH) / 2;
        this.topY = Math.max(20, (this.height - estimateHeight()) / 2);
    }

    private int estimateHeight() {
        // rough estimate: header(3 lines) + separator + economy(3) + separator
        // + villages(1 + count) + separator + vassals(1 + count) + separator + liege(1)
        int lines = 3 + 3 + 1 + data.villages().size() + 1 + data.vassals().size() + 1;
        return lines * 12 + 30; // 12px per line + padding for separators
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);

        int x = leftX;
        int y = topY;
        int rightX = x + PANEL_WIDTH;

        // Background panel
        gfx.fill(x - 8, y - 8, rightX + 8, y + estimateHeight() + 8, 0xCC000000);

        // === Header ===
        gfx.drawCenteredString(this.font, data.displayName(), this.width / 2, y, GOLD_COLOR);
        y += 12;
        gfx.drawCenteredString(this.font, data.leaderTitleName(), this.width / 2, y, TEXT_COLOR);
        y += 12;
        gfx.drawCenteredString(this.font, data.cultureName() + " culture \u2022 founded day " + data.foundedOnDay(), this.width / 2, y, LABEL_COLOR);
        y += 14;

        // === Separator ===
        gfx.fill(x, y, rightX, y + 1, SEPARATOR_COLOR);
        y += 6;

        // === Economy ===
        gfx.drawString(this.font, "Treasury: " + data.treasury() + " gold", x, y, TEXT_COLOR);
        y += 12;
        long totalDailyIncome = data.villages().stream().mapToLong(PolityViewPayload.VillageRow::dailyIncome).sum();
        gfx.drawString(this.font, "Daily village income: +" + totalDailyIncome, x, y, TEXT_COLOR);
        y += 12;
        gfx.drawString(this.font, String.format("Tax rate: %.0f%%  \u2022  Tribute rate: %.0f%%",
                data.taxRate() * 100f, data.tributeRate() * 100f), x, y, LABEL_COLOR);
        y += 14;

        // === Separator ===
        gfx.fill(x, y, rightX, y + 1, SEPARATOR_COLOR);
        y += 6;

        // === Villages ===
        gfx.drawString(this.font, "Villages (" + data.villages().size() + "):", x, y, GOLD_COLOR);
        y += 12;
        int maxVillages = Math.min(data.villages().size(), 10);
        for (int i = 0; i < maxVillages; i++) {
            PolityViewPayload.VillageRow v = data.villages().get(i);
            String line = String.format("  %s   stockpile %d   %+d/day", v.name(), v.stockpile(), v.dailyIncome());
            gfx.drawString(this.font, line, x, y, TEXT_COLOR);
            y += 11;
        }
        if (data.villages().size() > maxVillages) {
            gfx.drawString(this.font, "  ... and " + (data.villages().size() - maxVillages) + " more", x, y, LABEL_COLOR);
            y += 11;
        }
        y += 3;

        // === Separator ===
        gfx.fill(x, y, rightX, y + 1, SEPARATOR_COLOR);
        y += 6;

        // === Vassals ===
        if (!data.vassals().isEmpty()) {
            gfx.drawString(this.font, "Vassals (" + data.vassals().size() + "):", x, y, GOLD_COLOR);
            y += 12;
            int maxVassals = Math.min(data.vassals().size(), 10);
            for (int i = 0; i < maxVassals; i++) {
                PolityViewPayload.VassalRow v = data.vassals().get(i);
                gfx.drawString(this.font, "  " + v.displayName() + "  (" + v.leaderTitleName() + ")", x, y, TEXT_COLOR);
                y += 11;
            }
            if (data.vassals().size() > maxVassals) {
                gfx.drawString(this.font, "  ... and " + (data.vassals().size() - maxVassals) + " more", x, y, LABEL_COLOR);
                y += 11;
            }
        } else {
            gfx.drawString(this.font, "Vassals: none", x, y, LABEL_COLOR);
            y += 12;
        }
        y += 3;

        // === Liege ===
        gfx.fill(x, y, rightX, y + 1, SEPARATOR_COLOR);
        y += 6;
        if (data.liegeDisplayName().isEmpty()) {
            gfx.drawString(this.font, "Liege: none (independent)", x, y, LABEL_COLOR);
        } else {
            gfx.drawString(this.font, "Liege: " + data.liegeDisplayName(), x, y, TEXT_COLOR);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
