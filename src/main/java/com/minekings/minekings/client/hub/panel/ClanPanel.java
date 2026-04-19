package com.minekings.minekings.client.hub.panel;

import com.minekings.minekings.client.hub.HubPanel;
import com.minekings.minekings.politics.PolityViewPayload;
import com.minekings.minekings.politics.RequestHubDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Clan tab: shows the polity the viewing player rules (or holds interest
 * in). Data arrives via {@link PolityViewPayload}; until it lands, a
 * "Loading…" message shows.
 */
public class ClanPanel extends HubPanel {
    private @Nullable PolityViewPayload data;

    @Override
    public String title() { return "Clan"; }

    @Override
    public void onActivated() {
        data = null;
        PacketDistributor.sendToServer(new RequestHubDataPayload(RequestHubDataPayload.TAB_CLAN));
    }

    public void receive(PolityViewPayload payload) {
        this.data = payload;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int tx = x + 12;
        int ty = y + 24;

        if (data == null) {
            gfx.drawString(font, "Loading clan data…", tx, ty, 0xAAAAAA);
            return;
        }

        gfx.drawCenteredString(font, data.displayName(), x + w / 2, ty, 0xFFDD88);
        ty += 12;
        gfx.drawCenteredString(font, data.leaderTitleName(), x + w / 2, ty, 0xFFFFFF);
        ty += 12;
        gfx.drawCenteredString(font,
                data.cultureName() + " culture • founded day " + data.foundedOnDay(),
                x + w / 2, ty, 0xAAAAAA);
        ty += 16;

        gfx.fill(x + 8, ty, x + w - 8, ty + 1, 0x55FFFFFF);
        ty += 6;

        gfx.drawString(font, "Treasury: " + data.treasury() + " gold", tx, ty, 0xFFAA00);
        ty += 12;
        long totalPop = data.villages().stream().mapToLong(PolityViewPayload.VillageRow::population).sum();
        gfx.drawString(font, "Population: " + totalPop + "  •  villages: " + data.villages().size(), tx, ty, 0xFFFFFF);
        ty += 12;
        gfx.drawString(font, String.format("Tax %.0f%%  •  Tribute %.0f%%",
                data.taxRate() * 100f, data.tributeRate() * 100f), tx, ty, 0xAAAAAA);
        ty += 14;

        gfx.fill(x + 8, ty, x + w - 8, ty + 1, 0x55FFFFFF);
        ty += 6;

        gfx.drawString(font, "Villages (" + data.villages().size() + "):", tx, ty, 0xFFDD88);
        ty += 12;
        int maxShow = Math.min(data.villages().size(), 6);
        for (int i = 0; i < maxShow; i++) {
            PolityViewPayload.VillageRow v = data.villages().get(i);
            gfx.drawString(font, "  " + v.name() + "  (pop " + v.population() + ")", tx, ty, 0xFFFFFF);
            ty += 11;
            gfx.drawString(font, String.format("    food %d  •  mats %d  •  gold %d",
                    v.food(), v.materials(), v.gold()), tx, ty, 0xAAAAAA);
            ty += 12;
        }
        if (data.villages().size() > maxShow) {
            gfx.drawString(font, "  … and " + (data.villages().size() - maxShow) + " more",
                    tx, ty, 0xAAAAAA);
            ty += 11;
        }
        ty += 4;

        gfx.fill(x + 8, ty, x + w - 8, ty + 1, 0x55FFFFFF);
        ty += 6;

        if (data.liegeDisplayName().isEmpty()) {
            gfx.drawString(font, "Liege: none (independent)", tx, ty, 0xAAAAAA);
        } else {
            gfx.drawString(font, "Liege: " + data.liegeDisplayName(), tx, ty, 0xFFFFFF);
        }
        ty += 14;

        gfx.drawString(font, "Vassals (" + data.vassals().size() + "):", tx, ty, 0xFFDD88);
        ty += 12;
        int maxVassals = Math.min(data.vassals().size(), 4);
        for (int i = 0; i < maxVassals; i++) {
            PolityViewPayload.VassalRow v = data.vassals().get(i);
            gfx.drawString(font, "  " + v.displayName() + "  (" + v.leaderTitleName() + ")", tx, ty, 0xFFFFFF);
            ty += 11;
        }
        if (data.vassals().isEmpty()) {
            gfx.drawString(font, "  none", tx, ty, 0xAAAAAA);
        } else if (data.vassals().size() > maxVassals) {
            gfx.drawString(font, "  … and " + (data.vassals().size() - maxVassals) + " more", tx, ty, 0xAAAAAA);
        }
    }
}
