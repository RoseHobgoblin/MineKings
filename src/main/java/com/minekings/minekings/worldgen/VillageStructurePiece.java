package com.minekings.minekings.worldgen;

import com.minekings.minekings.MineKings;
import com.minekings.minekings.registry.MineKingsBlocks;
import com.minekings.minekings.registry.MineKingsStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Procedural starter village: a central stone-brick monument with the
 * Founding Stone on top, four small oak huts at cardinal offsets, and
 * cobblestone paths connecting them.
 *
 * <p>Intended as a v0.7 placeholder — once NBT schematics are authored,
 * swap {@link #postProcess} for {@code StructureTemplate.placeInWorld}.
 */
public class VillageStructurePiece extends StructurePiece {
    private static final int HUT_OFFSET = 9;      // distance from center to each hut's center
    private static final int VILLAGE_RADIUS = 12; // piece bounding box extent from origin

    private final BlockPos origin;

    public VillageStructurePiece(BlockPos origin) {
        super(MineKingsStructures.VILLAGE_PIECE.get(), 0, boxFor(origin));
        this.origin = origin;
        setOrientation(Direction.NORTH);
    }

    public VillageStructurePiece(CompoundTag tag) {
        super(MineKingsStructures.VILLAGE_PIECE.get(), tag);
        this.origin = new BlockPos(tag.getInt("oX"), tag.getInt("oY"), tag.getInt("oZ"));
    }

    private static BoundingBox boxFor(BlockPos origin) {
        return new BoundingBox(
                origin.getX() - VILLAGE_RADIUS, origin.getY() - 1,              origin.getZ() - VILLAGE_RADIUS,
                origin.getX() + VILLAGE_RADIUS, origin.getY() + 5,              origin.getZ() + VILLAGE_RADIUS
        );
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("oX", origin.getX());
        tag.putInt("oY", origin.getY());
        tag.putInt("oZ", origin.getZ());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager sm, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos worldAnchor) {
        MineKings.LOGGER.info("[MK] VillageStructurePiece.postProcess origin={} chunk={}", origin, chunkPos);

        buildMonument(level, box);

        buildHut(level, origin.offset(0, 0, -HUT_OFFSET), Direction.SOUTH, box);
        buildHut(level, origin.offset(0, 0,  HUT_OFFSET), Direction.NORTH, box);
        buildHut(level, origin.offset(-HUT_OFFSET, 0, 0), Direction.EAST,  box);
        buildHut(level, origin.offset( HUT_OFFSET, 0, 0), Direction.WEST,  box);

        buildPath(level, origin, origin.offset(0, 0, -HUT_OFFSET + 2), box);
        buildPath(level, origin, origin.offset(0, 0,  HUT_OFFSET - 2), box);
        buildPath(level, origin, origin.offset(-HUT_OFFSET + 2, 0, 0), box);
        buildPath(level, origin, origin.offset( HUT_OFFSET - 2, 0, 0), box);
    }

    private void buildMonument(WorldGenLevel level, BoundingBox box) {
        BlockState baseStone = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState capStone  = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
        BlockState founding  = MineKingsBlocks.FOUNDING_STONE.get().defaultBlockState();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                placeIfInBox(level, origin.offset(dx, 0, dz), baseStone, box);
            }
        }
        placeIfInBox(level, origin.above(1), baseStone, box);
        placeIfInBox(level, origin.above(2), baseStone, box);
        placeIfInBox(level, origin.above(3), capStone, box);
        placeIfInBox(level, origin.above(4), founding, box);
    }

    /**
     * 5×5 hut, 3 walls tall + flat oak roof, with a door centered on the
     * wall facing {@code doorSide} (i.e. toward the central monument).
     */
    private void buildHut(WorldGenLevel level, BlockPos center, Direction doorSide, BoundingBox box) {
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState log    = Blocks.OAK_LOG.defaultBlockState();

        // Floor
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                placeIfInBox(level, center.offset(dx, -1, dz), planks, box);
            }
        }
        // Corner posts (logs) + walls (planks) for 3 block heights
        for (int dy = 0; dy <= 2; dy++) {
            for (int i = -2; i <= 2; i++) {
                boolean isCorner = Math.abs(i) == 2;
                BlockState n = isCorner ? log : planks;
                placeIfInBox(level, center.offset(i, dy, -2), n, box);
                placeIfInBox(level, center.offset(i, dy,  2), n, box);
                placeIfInBox(level, center.offset(-2, dy, i), isCorner ? log : planks, box);
                placeIfInBox(level, center.offset( 2, dy, i), isCorner ? log : planks, box);
            }
        }
        // Flat roof
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                placeIfInBox(level, center.offset(dx, 3, dz), planks, box);
            }
        }

        // Carve a doorway on the facing wall and place an oak door
        BlockPos doorPos = center.offset(doorSide.getStepX() * 2, 0, doorSide.getStepZ() * 2);
        placeIfInBox(level, doorPos, Blocks.AIR.defaultBlockState(), box);
        placeIfInBox(level, doorPos.above(), Blocks.AIR.defaultBlockState(), box);

        BlockState lower = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, doorSide)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upper = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, doorSide)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        placeIfInBox(level, doorPos, lower, box);
        placeIfInBox(level, doorPos.above(), upper, box);
    }

    /** 1-block-wide cobble path on the ground from {@code a} to {@code b}. */
    private static void buildPath(WorldGenLevel level, BlockPos a, BlockPos b, BoundingBox box) {
        int dx = Integer.signum(b.getX() - a.getX());
        int dz = Integer.signum(b.getZ() - a.getZ());
        BlockPos p = a;
        int steps = 0, maxSteps = 32;
        while (!p.equals(b) && steps++ < maxSteps) {
            placeIfInBox(level, p.below(), Blocks.COBBLESTONE.defaultBlockState(), box);
            p = p.offset(dx, 0, dz);
        }
    }

    private static boolean placeIfInBox(WorldGenLevel level, BlockPos pos, BlockState state, BoundingBox box) {
        if (!box.isInside(pos)) return false;
        level.setBlock(pos, state, 2);
        return true;
    }
}
