package com.minekings.minekings.registry;

import com.minekings.minekings.MineKings;
import com.minekings.minekings.village.FoundingStoneBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MineKingsBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MineKings.MODID);

    public static final DeferredBlock<Block> FOUNDING_STONE = BLOCKS.registerBlock(
            "founding_stone",
            FoundingStoneBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0f, 9.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
    );

    private MineKingsBlocks() {}
}
