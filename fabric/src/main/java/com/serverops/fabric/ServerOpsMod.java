package com.serverops.fabric;

import com.serverops.fabric.server.OpsService;

import net.fabricmc.api.ModInitializer;

/** Common (server + client) entrypoint. All gameplay logic lives in {@link OpsService}. */
public class ServerOpsMod implements ModInitializer {
    @Override
    public void onInitialize() {
        OpsService.init();
    }
}
