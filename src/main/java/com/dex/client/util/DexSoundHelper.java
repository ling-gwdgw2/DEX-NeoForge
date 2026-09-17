package com.dex.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Utility for playing tactile UI sound effects in DEX.
 */
public final class DexSoundHelper {
    private DexSoundHelper() {}

    /**
     * Play the standard button click sound effect.
     */
    public static void playButtonClick() {
        playButtonClick(1.0F);
    }

    /**
     * Play the button click sound effect with a custom pitch.
     * @param pitch Sound pitch (e.g. 1.0F default, 1.2F for subtle accent).
     */
    public static void playButtonClick(float pitch) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
            }
        } catch (Throwable ignored) {}
    }
}
