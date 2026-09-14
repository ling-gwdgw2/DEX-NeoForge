package com.dex.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

public class DEXClient {
    public static void init(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(DEXClientEvents.class);
    }
}
