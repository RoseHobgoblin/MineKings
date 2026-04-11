/*
 * Derived from Minecraft Comes Alive Reborn (https://github.com/Luke100000/minecraft-comes-alive)
 * Copyright (c) 2016-2024 The MCA Project contributors
 *
 * Licensed under the GNU General Public License v3.0.
 * See LICENSE in this project's root for full text.
 */
package com.minekings.minekings.village.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.function.BiFunction;
import java.util.function.Function;

public interface MKWorldUtils {
    @SuppressWarnings("DataFlowIssue")
    static <T extends SavedData> T loadData(ServerLevel world, BiFunction<CompoundTag, HolderLookup.Provider, T> loader, Function<ServerLevel, T> factory, String dataId) {
        return world.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        () -> factory.apply(world),
                        loader,
                        null
                ),
                dataId);
    }

    static boolean isChunkLoaded(ServerLevel world, Vec3i pos) {
        return isChunkLoaded(world, new BlockPos(pos));
    }

    static boolean isChunkLoaded(ServerLevel world, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        LevelChunk worldChunk = world.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        if (worldChunk != null) {
            return worldChunk.getFullStatus() == FullChunkStatus.ENTITY_TICKING && world.areEntitiesLoaded(chunkPos.toLong());
        }
        return false;
    }
}
