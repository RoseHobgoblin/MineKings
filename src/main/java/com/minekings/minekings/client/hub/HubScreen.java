package com.minekings.minekings.client.hub;

import com.minekings.minekings.MineKings;
import com.minekings.minekings.client.hub.map.MapTileCache;
import com.minekings.minekings.client.hub.map.MarkerType;
import com.minekings.minekings.client.hub.map.SetTileRenderer;
import com.minekings.minekings.client.hub.map.SubTile;
import com.minekings.minekings.client.hub.map.SubTileQuartet;
import com.minekings.minekings.client.hub.map.TileRenderIterator;
import com.minekings.minekings.client.hub.map.TileTextureRegistry;
import com.minekings.minekings.client.hub.map.TileTextureSet;
import com.minekings.minekings.client.hub.panel.CharacterPanel;
import com.minekings.minekings.client.hub.panel.ClanPanel;
import com.minekings.minekings.client.hub.panel.EncyclopediaPanel;
import com.minekings.minekings.client.hub.panel.KingdomPanel;
import com.minekings.minekings.politics.MapRegionPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Bannerlord-style hub. The map fills the full screen as a persistent
 * backdrop; tabs along the bottom toggle overlay panels. The map supports
 * drag-to-pan, mouse-wheel zoom, a rotated player arrow, a culture-colored
 * village marker set with hover tooltips, a scale bar, and a recenter
 * button.
 */
public class HubScreen extends Screen {
    private static final int TAB_ROW_HEIGHT = 28;
    private static final int TAB_WIDTH = 108;
    private static final int TAB_GAP = 4;
    private static final int RETURN_WIDTH = 80;

    // Panel bounds (populated in init based on current window size)
    private int panelX, panelY, panelW, panelH;

    private final HubPanel characterPanel = new CharacterPanel();
    private final HubPanel clanPanel = new ClanPanel();
    private final HubPanel kingdomPanel = new KingdomPanel();
    private final HubPanel encyclopediaPanel = new EncyclopediaPanel();

    private @Nullable HubPanel activePanel = null;

    private static @Nullable HubScreen CURRENT = null;

    // -------- Map viewport --------

    /** Zoom levels as pixels-per-chunk. Must all be even (subtile size = half). */
    private static final int[] ZOOM_LEVELS = {8, 12, 16, 24, 32};
    private static final int DEFAULT_ZOOM_INDEX = 2; // 16 px/chunk

    private double viewBlockX, viewBlockZ;
    private int zoomIndex = DEFAULT_ZOOM_INDEX;
    private boolean followPlayer = true;

    private static final int COLOR_SCREEN_BG   = 0xFF2E251A;     // dark wood surround
    private static final int COLOR_FOG         = 0xFF6B5738;     // weathered parchment — clearly distinct from rendered terrain
    private static final int COLOR_FRAME_HI    = 0xFFE8C98C;
    private static final int COLOR_PLAYER_HALO = 0x80000000;
    private static final int COLOR_SCALE_BAR   = 0xFFE0C484;
    private static final int COLOR_SCALE_BG    = 0xA0000000;

    // Marker frame + player textures (icons are per-MarkerType + zoom LOD).
    private static final ResourceLocation TEX_MARKER_FRAME = ResourceLocation.fromNamespaceAndPath(MineKings.MODID, "textures/gui/map/markers/frame.png");
    private static final ResourceLocation TEX_PLAYER  = ResourceLocation.fromNamespaceAndPath(MineKings.MODID, "textures/gui/map/player.png");

    /** One entry per rendered marker; used for hover hit-testing. */
    private record MarkerHit(int cx, int cy, int radius, MapRegionPayload.VillageMarker marker) {}
    private final List<MarkerHit> markerHits = new ArrayList<>();

    /** Pre-resolved subtile draw command. Coordinates are world-space-ish
     *  (origin-independent): {@code baseX = minChunkX*pxPerChunk + s.x*halfChunkPx}.
     *  The current frame's {@code originX}/{@code originY} are added at draw
     *  time, so sub-chunk player movement (which shifts the origin every
     *  frame under followPlayer) does NOT invalidate the cache — only chunk
     *  boundary crossings, zoom changes, or new tile data do. */
    private record TerrainQuad(ResourceLocation tex, int baseX, int baseY, int u, int v) {}
    private final List<TerrainQuad> terrainQuadCache = new ArrayList<>();
    private int terrainCacheKeyMinX, terrainCacheKeyMinZ, terrainCacheKeyMaxX, terrainCacheKeyMaxZ;
    private int terrainCachePxPerChunk = -1;
    private int terrainCacheTileVersion = -1;
    private int terrainCacheResolveVersion = -1;

    private @Nullable Button recenterButton;

    public HubScreen() {
        super(Component.translatable("screen.minekings.hub"));
    }

    public static @Nullable HubScreen current() { return CURRENT; }

    private int pixelsPerChunk() { return ZOOM_LEVELS[zoomIndex]; }

    @Override
    protected void init() {
        super.init();
        CURRENT = this;

        LocalPlayer lp = Minecraft.getInstance().player;
        if (lp != null && followPlayer) {
            viewBlockX = lp.getX();
            viewBlockZ = lp.getZ();
        }

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

        // Recenter button — top-right of the map area.
        recenterButton = Button.builder(Component.literal("Recenter"), b -> {
            followPlayer = true;
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null) { viewBlockX = p.getX(); viewBlockZ = p.getZ(); }
            zoomIndex = DEFAULT_ZOOM_INDEX;
        }).bounds(this.width - 88, 8, 80, 20).build();
        addRenderableWidget(recenterButton);

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
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderMap(gfx, partialTick);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);

        if (activePanel == null) {
            MarkerHit hit = pickMarker(mouseX, mouseY);
            if (hit != null) {
                renderVillageTooltip(gfx, mouseX, mouseY, hit.marker());
            }
        }

        if (activePanel != null) {
            gfx.fill(panelX - 4, panelY - 4, panelX + panelW + 4, panelY + panelH + 4, 0xCC000000);
            gfx.drawCenteredString(this.font, activePanel.title(),
                    panelX + panelW / 2, panelY + 4, 0xFFAA00);
            gfx.fill(panelX, panelY + 16, panelX + panelW, panelY + 17, 0x55FFFFFF);
            activePanel.render(gfx, mouseX, mouseY, partialTick);
        }
    }

    private void renderMap(GuiGraphics gfx, float partialTick) {
        markerHits.clear();
        gfx.fill(0, 0, this.width, this.height, COLOR_SCREEN_BG);

        LocalPlayer lp = Minecraft.getInstance().player;
        if (lp == null) return;
        if (followPlayer) {
            viewBlockX = lp.getX();
            viewBlockZ = lp.getZ();
        }

        int pxPerChunk = pixelsPerChunk();

        // Visible chunk rect derived from viewport center + map size.
        int mapTop = 0;
        int mapBottom = this.height - TAB_ROW_HEIGHT - 12;
        int mapLeft = 0;
        int mapRight = this.width;
        int mapW = mapRight - mapLeft;
        int mapH = mapBottom - mapTop;

        // Block at (mapLeft, mapTop)
        double blocksPerPx = 16.0 / pxPerChunk;
        double leftBlockX  = viewBlockX - (mapW * blocksPerPx) / 2.0;
        double topBlockZ   = viewBlockZ - (mapH * blocksPerPx) / 2.0;
        int minChunkX = (int) Math.floor(leftBlockX) >> 4;
        int minChunkZ = (int) Math.floor(topBlockZ) >> 4;
        int maxChunkX = (int) Math.floor(leftBlockX + mapW * blocksPerPx) >> 4;
        int maxChunkZ = (int) Math.floor(topBlockZ + mapH * blocksPerPx) >> 4;

        // Screen X of chunk boundary at (cx * 16).
        // Put the viewport center at map center; solve for chunk-to-screen transform.
        double pxPerBlock = pxPerChunk / 16.0;
        int originX = (int) Math.round(mapLeft + mapW / 2.0 - viewBlockX * pxPerBlock);
        int originY = (int) Math.round(mapTop  + mapH / 2.0 - viewBlockZ * pxPerBlock);

        // Fog under unknown chunks.
        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                if (MapTileCache.INSTANCE.get(cx, cz) == null) {
                    int px = originX + cx * 16 * pxPerChunk / 16;
                    int py = originY + cz * 16 * pxPerChunk / 16;
                    gfx.fill(px, py, px + pxPerChunk, py + pxPerChunk, COLOR_FOG);
                }
            }
        }

        renderAutotiledTerrain(gfx, originX, originY, minChunkX, minChunkZ, maxChunkX, maxChunkZ, pxPerChunk);

        // Village markers — icon varies by MarkerType + zoom LOD.
        for (MapRegionPayload.VillageMarker m : MapTileCache.INSTANCE.villages()) {
            int mcx = m.blockX() >> 4;
            int mcz = m.blockZ() >> 4;
            if (mcx < minChunkX - 2 || mcx > maxChunkX + 2 || mcz < minChunkZ - 2 || mcz > maxChunkZ + 2) continue;

            int sx = (int) Math.round(originX + m.blockX() * pxPerBlock);
            int sy = (int) Math.round(originY + m.blockZ() * pxPerBlock);
            MarkerType mt = MarkerType.fromId(m.markerTypeId());
            int markerSize = mt.baseSize(pxPerChunk) + m.tier() * 4;

            gfx.setColor(
                    ((m.colorRGB() >> 16) & 0xFF) / 255f,
                    ((m.colorRGB() >> 8) & 0xFF) / 255f,
                    (m.colorRGB() & 0xFF) / 255f,
                    1f);
            gfx.blit(TEX_MARKER_FRAME, sx - markerSize / 2, sy - markerSize / 2,
                    0, 0, markerSize, markerSize, markerSize, markerSize);
            gfx.setColor(1f, 1f, 1f, 1f);
            int iconSize = markerSize - 4;
            ResourceLocation iconTex = mt.iconFor(pxPerChunk);
            gfx.blit(iconTex, sx - iconSize / 2, sy - iconSize / 2,
                    0, 0, iconSize, iconSize, iconSize, iconSize);

            markerHits.add(new MarkerHit(sx, sy, markerSize / 2 + 2, m));
        }

        // Rotated player arrow.
        double px = originX + lerp(lp.xo, lp.getX(), partialTick) * pxPerBlock;
        double py = originY + lerp(lp.zo, lp.getZ(), partialTick) * pxPerBlock;
        int arrowW = Math.max(10, pxPerChunk);
        int arrowH = Math.max(12, pxPerChunk + 2);
        gfx.fill((int)(px - arrowW / 2.0 - 1), (int)(py - arrowH / 2.0 - 1),
                 (int)(px + arrowW / 2.0 + 1), (int)(py + arrowH / 2.0 + 1), COLOR_PLAYER_HALO);

        float yaw = lp.getYRot();
        PoseStack ps = gfx.pose();
        ps.pushPose();
        ps.translate(px, py, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(yaw));
        ps.translate(-arrowW / 2.0, -arrowH / 2.0, 0);
        // 10-arg blit: dest is arrowW×arrowH, source is 7×8 from a 7×8 texture.
        gfx.blit(TEX_PLAYER, 0, 0, arrowW, arrowH, 0, 0, 7, 8, 7, 8);
        ps.popPose();

        renderScaleBar(gfx, mapBottom, pxPerChunk);
        renderCoords(gfx, lp, pxPerChunk);
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    private void renderAutotiledTerrain(GuiGraphics gfx, int originX, int originY,
                                        int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ,
                                        int pxPerChunk) {
        int tileVersion = MapTileCache.INSTANCE.version();
        int resolveVersion = TileTextureRegistry.cacheVersion();
        boolean cacheValid = terrainCachePxPerChunk == pxPerChunk
                && terrainCacheKeyMinX == minChunkX && terrainCacheKeyMinZ == minChunkZ
                && terrainCacheKeyMaxX == maxChunkX && terrainCacheKeyMaxZ == maxChunkZ
                && terrainCacheTileVersion == tileVersion
                && terrainCacheResolveVersion == resolveVersion;

        if (!cacheValid) {
            rebuildTerrainQuadCache(minChunkX, minChunkZ, maxChunkX, maxChunkZ, pxPerChunk);
            terrainCachePxPerChunk = pxPerChunk;
            terrainCacheKeyMinX = minChunkX; terrainCacheKeyMinZ = minChunkZ;
            terrainCacheKeyMaxX = maxChunkX; terrainCacheKeyMaxZ = maxChunkZ;
            terrainCacheTileVersion = tileVersion;
            terrainCacheResolveVersion = resolveVersion;
        }

        int halfChunkPx = pxPerChunk / 2;
        int mapLeft = 0;
        int mapTop = 0;
        int mapRight = this.width;
        int mapBottom = this.height - TAB_ROW_HEIGHT - 12;

        SetTileRenderer renderer = new SetTileRenderer(gfx, pxPerChunk);
        for (TerrainQuad q : terrainQuadCache) {
            int sx = originX + q.baseX();
            int sy = originY + q.baseY();
            // Draw-time screen-bounds cull (origin changes each frame under
            // followPlayer, so this must run per-frame, not at cache time).
            if (sx + halfChunkPx <= mapLeft || sy + halfChunkPx <= mapTop
                || sx >= mapRight || sy >= mapBottom) continue;
            renderer.add(q.tex(), sx, sy, q.u(), q.v());
        }
        renderer.flush();
    }

    private void rebuildTerrainQuadCache(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ,
                                         int pxPerChunk) {
        terrainQuadCache.clear();
        TileRenderIterator it = new TileRenderIterator(
                (cx, cz) -> MapTileCache.INSTANCE.biomeAt(cx, cz),
                TileTextureRegistry::resolveByLocation);
        it.setScope(minChunkX, minChunkZ, maxChunkX, maxChunkZ);

        int halfChunkPx = pxPerChunk / 2;
        for (SubTileQuartet q : it) {
            for (SubTile s : q) {
                if (s.tile == null) continue;
                TileTextureSet set = TileTextureRegistry.resolveByLocation(s.tile);
                if (set == null || set.variants().isEmpty()) continue;
                ResourceLocation tex = set.pickForSeed(s.variationNumber);
                // Store origin-independent coords; draw path adds originX/Y.
                int baseX = minChunkX * pxPerChunk + s.x * halfChunkPx;
                int baseY = minChunkZ * pxPerChunk + s.y * halfChunkPx;
                terrainQuadCache.add(new TerrainQuad(tex, baseX, baseY, s.getTextureU(), s.getTextureV()));
            }
        }
    }

    private void renderScaleBar(GuiGraphics gfx, int mapBottom, int pxPerChunk) {
        // "N chunks" bar: width always ~100 px; compute how many chunks that represents.
        int barPx = 100;
        int blocksRepresented = (int) Math.round(barPx * 16.0 / pxPerChunk);
        int x0 = 12;
        int y0 = mapBottom - 18;
        gfx.fill(x0 - 4, y0 - 4, x0 + barPx + 4, y0 + 12, COLOR_SCALE_BG);
        gfx.fill(x0, y0, x0 + barPx, y0 + 2, COLOR_SCALE_BAR);
        // Tick marks at quarters.
        for (int i = 0; i <= 4; i++) {
            int tx = x0 + (i * barPx) / 4;
            gfx.fill(tx - 1, y0 - 2, tx + 1, y0 + 4, COLOR_SCALE_BAR);
        }
        gfx.drawString(this.font, blocksRepresented + " blocks", x0, y0 + 6, COLOR_SCALE_BAR, false);
    }

    private void renderCoords(GuiGraphics gfx, LocalPlayer lp, int pxPerChunk) {
        String label = String.format("X %d   Z %d   \u00a77zoom %d px/chunk",
                (int) lp.getX(), (int) lp.getZ(), pxPerChunk);
        gfx.fill(8, this.height - TAB_ROW_HEIGHT - 30, 8 + this.font.width(label) + 8,
                 this.height - TAB_ROW_HEIGHT - 14, COLOR_SCALE_BG);
        gfx.drawString(this.font, label, 12, this.height - TAB_ROW_HEIGHT - 28, COLOR_FRAME_HI, false);
    }

    private @Nullable MarkerHit pickMarker(int mouseX, int mouseY) {
        for (MarkerHit h : markerHits) {
            int dx = mouseX - h.cx;
            int dy = mouseY - h.cy;
            if (dx * dx + dy * dy <= h.radius * h.radius) return h;
        }
        return null;
    }

    private void renderVillageTooltip(GuiGraphics gfx, int mouseX, int mouseY, MapRegionPayload.VillageMarker m) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(m.name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        MarkerType mt = MarkerType.fromId(m.markerTypeId());
        String typeLabel = switch (mt) {
            case CAPITAL        -> "Capital";
            case SEAT_OF_VASSAL -> "Vassal Seat";
            case TOWN           -> "Town";
        };
        lines.add(Component.literal(typeLabel).withStyle(ChatFormatting.AQUA));
        if (!m.polityName().isEmpty()) {
            lines.add(Component.literal(m.polityName()).withStyle(ChatFormatting.YELLOW));
        }
        lines.add(Component.literal(m.leaderLine()).withStyle(ChatFormatting.WHITE));
        lines.add(Component.literal("Pop: " + m.population()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("X " + m.blockX() + "   Z " + m.blockZ()).withStyle(ChatFormatting.DARK_GRAY));
        gfx.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    @Override
    public void tick() {
        super.tick();
        LocalPlayer lp = Minecraft.getInstance().player;
        if (lp == null) return;
        // Terrain tiles come from ClientMapCapture as the client loads chunks.
        // Here we only poll for village markers, which the server authors.
        // A wide rect centered on the player covers anything the player can
        // plausibly see on the map.
        long now = lp.level().getGameTime();
        if (lastMarkerRequestTick < 0 || now - lastMarkerRequestTick >= 40L) {
            lastMarkerRequestTick = now;
            int pcx = ((int) Math.floor(lp.getX())) >> 4;
            int pcz = ((int) Math.floor(lp.getZ())) >> 4;
            int r = 256;
            MapTileCache.INSTANCE.requestRegion(pcx - r, pcz - r, pcx + r, pcz + r);
        }
    }

    private long lastMarkerRequestTick = Long.MIN_VALUE;

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (activePanel != null && activePanel.mouseClicked(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dmx, double dmy) {
        if (activePanel != null) return super.mouseDragged(mx, my, button, dmx, dmy);
        if (button != 0) return super.mouseDragged(mx, my, button, dmx, dmy);
        double blocksPerPx = 16.0 / pixelsPerChunk();
        viewBlockX -= dmx * blocksPerPx;
        viewBlockZ -= dmy * blocksPerPx;
        followPlayer = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (activePanel != null) return super.mouseScrolled(mx, my, dx, dy);
        if (dy > 0) zoomIndex = Math.min(ZOOM_LEVELS.length - 1, zoomIndex + 1);
        else if (dy < 0) zoomIndex = Math.max(0, zoomIndex - 1);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256 && activePanel != null) {
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
        // Intentionally not clearing MapTileCache — terrain tiles accumulate
        // as the client naturally loads chunks, and should persist across
        // hub open/close cycles. Only clear on world change.
        super.onClose();
    }

    public @Nullable HubPanel getActivePanel() { return activePanel; }
    public ClanPanel getClanPanel() { return (ClanPanel) clanPanel; }
}
