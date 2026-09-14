package com.dex;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(DEXMod.MOD_ID)
public class DEXMod {
    public static final String MOD_ID = "dex";
    public static final String MOD_NAME = "DEX";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public DEXMod(IEventBus modEventBus) {
        LOGGER.info("{} initialized on side: {}", MOD_NAME, FMLEnvironment.dist);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.dex.client.DEXClient.init(modEventBus);
        }
    }
}
