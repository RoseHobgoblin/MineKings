package com.minekings.minekings.registry;

import com.minekings.minekings.MineKings;
import com.minekings.minekings.village.FoundingStoneBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MineKingsBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MineKings.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FoundingStoneBlockEntity>> FOUNDING_STONE =
            BLOCK_ENTITIES.register(
                    "founding_stone",
                    () -> BlockEntityType.Builder.of(
                            FoundingStoneBlockEntity::new,
                            MineKingsBlocks.FOUNDING_STONE.get()
                    ).build(null)
            );

    private MineKingsBlockEntities() {}
}
