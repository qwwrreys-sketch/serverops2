package com.serverops.fabric.adminlog.client;

import com.serverops.fabric.adminlog.AdminLogNet;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;

/**
 * Client entrypoint of the admin notifier (4th line in fabric.mod.json). Receives the cards and draws them:
 * in-game through the HUD callback, and on top of every open screen (chat, pause, admin panel, side menu...).
 */
public class AdminLogClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(AdminLogNet.S2C_TOAST, (client, handler, buf, sender) -> {
            AdminLogNet.Toast t = AdminLogNet.Toast.read(buf);
            client.execute(() -> AdminToasts.push(t));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(AdminToasts::clear));

        // no screen open: the HUD draws them (with a screen open the HUD is covered, so the screen draws them)
        HudRenderCallback.EVENT.register((g, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) AdminToasts.render(g, mc);
        });
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) ->
                ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> AdminToasts.render(g, client)));
    }
}
