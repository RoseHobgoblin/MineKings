/*
 * Derived from Minecraft Comes Alive Reborn (https://github.com/Luke100000/minecraft-comes-alive)
 * Copyright (c) 2016-2024 The MCA Project contributors
 *
 * Licensed under the GNU General Public License v3.0.
 * See LICENSE in this project's root for full text.
 */
package com.minekings.minekings.village.util;

import com.mojang.datafixers.util.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface MKNbtHelper {

    @SuppressWarnings("unchecked")
    static <T extends Tag> T computeIfAbsent(CompoundTag nbt, String key, int type, Supplier<T> factory) {
        if (!nbt.contains(key, type)) {
            nbt.put(key, factory.get());
        }
        return (T) nbt.get(key);
    }

    static CompoundTag copyTo(CompoundTag from, CompoundTag to) {
        from.getAllKeys().forEach(key -> to.put(key, from.get(key)));
        return to;
    }

    static <V> List<V> toList(Tag nbt, Function<Tag, V> valueMapper) {
        return toStream(nbt, valueMapper).collect(Collectors.toList());
    }

    static <V> Stream<V> toStream(Tag nbt, Function<Tag, V> valueMapper) {
        return ((ListTag) nbt).stream().map(valueMapper);
    }

    static <K, V> Map<K, V> toMap(CompoundTag nbt, Function<String, K> keyMapper, Function<Tag, V> valueMapper) {
        return toMap(nbt, keyMapper, (k, e) -> valueMapper.apply(e));
    }

    static <K, V> Map<K, V> toMap(CompoundTag nbt, Function<String, K> keyMapper, BiFunction<K, Tag, V> valueMapper) {
        return nbt.getAllKeys().stream()
                .map(e -> {
                    K k = keyMapper.apply(e);
                    if (k == null) return null;
                    V v = valueMapper.apply(k, nbt.get(e));
                    if (v == null) return null;
                    return new Pair<>(k, v);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond)
                );
    }

    static <V> ListTag fromList(Iterable<V> list, Function<V, Tag> valueMapper) {
        ListTag output = new ListTag();
        list.forEach(item -> output.add(valueMapper.apply(item)));
        return output;
    }

    static <K, V> CompoundTag fromMap(CompoundTag output, Map<K, V> map, Function<K, String> keyMapper, Function<V, Tag> valueMapper) {
        map.forEach((key, value) -> output.put(keyMapper.apply(key), valueMapper.apply(value)));
        return output;
    }
}
