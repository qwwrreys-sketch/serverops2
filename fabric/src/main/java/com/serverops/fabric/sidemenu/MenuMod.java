package com.serverops.fabric.sidemenu;

import net.fabricmc.api.ModInitializer;

/**
 * Server-side entrypoint of the side menu. It is registered as a SECOND "main" entrypoint in
 * fabric.mod.json, so ServerOpsMod / OpsService do not need a single changed line.
 */
public class MenuMod implements ModInitializer {
    @Override
    public void onInitialize() {
        MenuService.init();
    }
}
