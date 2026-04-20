package com.minekings.minekings.politics.compat.mca;

import com.minekings.minekings.MineKings;
import com.minekings.minekings.village.Village;
import com.minekings.minekings.village.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Makes MCA Reborn use MineKings villages as its residency backend.
 *
 * <p>MCA's own village detection (POI scanning + bounding-box merging in
 * {@code VillageManager.processBuilding}) is controlled by its per-village
 * {@code autoScan} flag, which derives from
 * {@code Config.enableAutoScanByDefault} — already {@code false} out of the
 * box. So MCA doesn't flood-fill on its own; we just need to give it
 * something to find when {@code Residency.seekHome} runs.
 *
 * <p>This bridge mirrors MineKings villages into MCA's
 * {@code VillageManager.villages} map with synthetic IDs offset by
 * {@link #MCA_ID_OFFSET}. Each mirror has its {@code box} field set to the
 * MineKings village's bounding box, which is all
 * {@link net.conczin.mca.entity.ai.Residency#seekHome} needs to match a
 * villager to it. Buildings are intentionally left empty — MCA-specific
 * building behaviors (guards, graveyards, beds) stay inert; the economy,
 * attribute derivation, and structure detection remain MineKings' job.
 *
 * <p>Mirrors are regenerated on each {@link #sync} and not persisted into
 * MCA's save file, so a world without MineKings won't inherit orphan
 * mirror records.
 */
public final class MCAVillageBridge {
    public static volatile MCAVillageBridge ACTIVE;

    /**
     * Synthetic MCA village IDs start here. Large offset keeps us clear of
     * any MCA-native villages the world might already have from a prior
     * save (MCA's {@code lastVillageId} is an int starting at 0).
     */
    public static final int MCA_ID_OFFSET = 1_000_000;

    private final MethodHandle villageManagerGet;   // static (ServerLevel) -> VillageManager
    private final Constructor<?> villageCtor;        // (int, ServerLevel)
    private final Field villageManagerVillages;      // Map<Integer, Village> (private)
    private final Field villageBox;                  // BlockBoxExtended (private)
    private final Constructor<?> blockBoxCtor;       // (int, int, int, int, int, int)
    private final MethodHandle villageSetName;       // (String) -> void

    public MCAVillageBridge() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();

        Class<?> vmClass = Class.forName("net.conczin.mca.server.world.data.VillageManager");
        Class<?> vClass = Class.forName("net.conczin.mca.server.world.data.Village");
        Class<?> boxClass = Class.forName("net.conczin.mca.util.BlockBoxExtended");

        this.villageManagerGet = lookup.findStatic(vmClass, "get",
                MethodType.methodType(vmClass, ServerLevel.class));
        this.villageCtor = vClass.getDeclaredConstructor(int.class, ServerLevel.class);
        this.villageCtor.setAccessible(true);
        this.villageManagerVillages = vmClass.getDeclaredField("villages");
        this.villageManagerVillages.setAccessible(true);
        this.villageBox = vClass.getDeclaredField("box");
        this.villageBox.setAccessible(true);
        this.blockBoxCtor = boxClass.getDeclaredConstructor(
                int.class, int.class, int.class, int.class, int.class, int.class);
        this.villageSetName = lookup.findVirtual(vClass, "setName",
                MethodType.methodType(void.class, String.class));

        MineKings.LOGGER.info("MineKings: MCA village bridge bindings resolved.");
    }

    public void sync(ServerLevel level) {
        try {
            doSync(level);
        } catch (Throwable t) {
            MineKings.LOGGER.error("MineKings: MCA village bridge sync failed", t);
        }
    }

    @SuppressWarnings("unchecked")
    private void doSync(ServerLevel level) throws Throwable {
        Object mcaManager = villageManagerGet.invoke(level);
        Map<Integer, Object> mcaVillages =
                (Map<Integer, Object>) villageManagerVillages.get(mcaManager);

        VillageManager mkManager = VillageManager.get(level);
        Set<Integer> kept = new HashSet<>();

        for (Village mk : mkManager) {
            int mirrorId = MCA_ID_OFFSET + mk.getId();
            kept.add(mirrorId);

            Object mirror = mcaVillages.get(mirrorId);
            if (mirror == null) {
                mirror = villageCtor.newInstance(mirrorId, level);
                villageSetName.invoke(mirror, mk.getName());
                mcaVillages.put(mirrorId, mirror);
            }

            BoundingBox b = mk.getBox();
            Object box = blockBoxCtor.newInstance(
                    b.minX(), b.minY(), b.minZ(),
                    b.maxX(), b.maxY(), b.maxZ());
            villageBox.set(mirror, box);
        }

        mcaVillages.entrySet().removeIf(e ->
                e.getKey() >= MCA_ID_OFFSET && !kept.contains(e.getKey()));
    }
}
