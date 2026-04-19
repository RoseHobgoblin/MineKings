package com.minekings.minekings.worldgen;

import com.minekings.minekings.MineKings;
import com.minekings.minekings.registry.MineKingsStructures;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * MineKings starter village. Vanilla-style placement: samples the 3×3
 * corners of the chunk center for terrain flatness; rejects spots that
 * are too steep or at/below sea level (would be water or a cliff).
 * Accepted positions place a procedural mini-village via
 * {@link VillageStructurePiece}.
 *
 * <p>The Founding Stone on the central monument auto-registers the
 * village + NPC polity when the chunk loads.
 */
public class VillageStructure extends Structure {
    public static final MapCodec<VillageStructure> CODEC = simpleCodec(VillageStructure::new);

    /** Reject placements where the surface rises/falls more than this within the ~8-block sample area. */
    private static final int MAX_TERRAIN_VARIANCE = 4;
    /** Placements at-or-below this Y are considered water/shore and rejected. */
    private static final int MIN_ACCEPTABLE_Y = 63;

    public VillageStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        ChunkPos chunkPos = ctx.chunkPos();
        int cx = chunkPos.getMinBlockX() + 8;
        int cz = chunkPos.getMinBlockZ() + 8;

        int[] offsets = {-4, 0, 4};
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int dx : offsets) {
            for (int dz : offsets) {
                int sy = ctx.chunkGenerator().getFirstOccupiedHeight(
                        cx + dx, cz + dz,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        ctx.heightAccessor(), ctx.randomState());
                if (sy < minY) minY = sy;
                if (sy > maxY) maxY = sy;
            }
        }

        if (maxY - minY > MAX_TERRAIN_VARIANCE) {
            MineKings.LOGGER.debug("[MK] Rejecting chunk {} — terrain variance {} exceeds {}",
                    chunkPos, maxY - minY, MAX_TERRAIN_VARIANCE);
            return Optional.empty();
        }
        if (minY < MIN_ACCEPTABLE_Y) {
            MineKings.LOGGER.debug("[MK] Rejecting chunk {} — min Y {} below sea-level threshold",
                    chunkPos, minY);
            return Optional.empty();
        }

        int y = ctx.chunkGenerator().getFirstOccupiedHeight(
                cx, cz, Heightmap.Types.WORLD_SURFACE_WG,
                ctx.heightAccessor(), ctx.randomState());
        BlockPos origin = new BlockPos(cx, y + 1, cz);
        MineKings.LOGGER.info("[MK] VillageStructure.findGenerationPoint chunk={} origin={}", chunkPos, origin);
        return Optional.of(new GenerationStub(origin, builder ->
                builder.addPiece(new VillageStructurePiece(origin))));
    }

    @Override
    public StructureType<?> type() {
        return MineKingsStructures.VILLAGE_TYPE.get();
    }
}
