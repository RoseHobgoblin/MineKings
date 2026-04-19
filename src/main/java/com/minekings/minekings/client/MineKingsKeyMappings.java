package com.minekings.minekings.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class MineKingsKeyMappings {
    public static final String CATEGORY = "key.categories.minekings";

    public static final KeyMapping OPEN_HUB = new KeyMapping(
            "key.minekings.open_hub",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_M),
            CATEGORY
    );

    private MineKingsKeyMappings() {}
}
