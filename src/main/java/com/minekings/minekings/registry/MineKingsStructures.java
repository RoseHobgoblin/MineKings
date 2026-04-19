package com.minekings.minekings.registry;

import com.minekings.minekings.MineKings;
import com.minekings.minekings.worldgen.VillageStructure;
import com.minekings.minekings.worldgen.VillageStructurePiece;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MineKingsStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, MineKings.MODID);

    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, MineKings.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<VillageStructure>> VILLAGE_TYPE =
            STRUCTURE_TYPES.register("village", () -> () -> VillageStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> VILLAGE_PIECE =
            STRUCTURE_PIECES.register("village", () -> (ctx, tag) -> new VillageStructurePiece(tag));

    private MineKingsStructures() {}
}
