package com.minekings.minekings.politics.embodiment;

import com.minekings.minekings.MineKings;
import com.minekings.minekings.politics.compat.mca.MCAEmbodimentAdapter;
import com.minekings.minekings.politics.compat.mca.MCAVillageBridge;
import net.neoforged.fml.ModList;

/**
 * Static holder for the active {@link EmbodimentAdapter}.
 *
 * <p>{@link #init()} runs exactly once inside
 * {@code FMLCommonSetupEvent.enqueueWork} so the {@code ModList} is
 * populated. After init, {@link #ACTIVE} is the single adapter used by
 * {@code PoliticsManager} for every bind/unbind/refresh.
 *
 * <p>The MCA adapter is reflection-based (see {@link MCAEmbodimentAdapter}),
 * so it ships in every MineKings jar and simply fails its constructor
 * when MCA isn't present — no separate build artifact, no optional source
 * set, no user action required.
 */
public final class Embodiment {
    public static volatile EmbodimentAdapter ACTIVE = new VanillaEmbodimentAdapter();

    private Embodiment() {}

    public static void init() {
        if (!ModList.get().isLoaded("mca")) {
            MineKings.LOGGER.info("MineKings: MCA not detected — using vanilla embodiment adapter.");
            return;
        }
        try {
            ACTIVE = new MCAEmbodimentAdapter();
            MineKings.LOGGER.info("MineKings: MCA detected — using MCA embodiment adapter.");
        } catch (ReflectiveOperationException | LinkageError e) {
            MineKings.LOGGER.error("MineKings: MCA detected but adapter init failed; falling back to vanilla.", e);
        }
        try {
            MCAVillageBridge.ACTIVE = new MCAVillageBridge();
            MineKings.LOGGER.info("MineKings: MCA village bridge installed — MineKings villages are now MCA residency targets.");
        } catch (ReflectiveOperationException | LinkageError e) {
            MineKings.LOGGER.error("MineKings: MCA village bridge init failed; MCA villagers will not find MineKings villages as home.", e);
        }
    }
}
