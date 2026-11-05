package com.serverops.fabric.adminlog;

import net.fabricmc.api.ModInitializer;

/**
 * Entrypoint of the admin-action notifier. Registered as a THIRD "main" entrypoint in fabric.mod.json, so
 * ServerOpsMod / OpsService stay exactly as they are.
 */
public class AdminLogMod implements ModInitializer {
    @Override
    public void onInitialize() {
        AdminLogger.install();
    }
}
