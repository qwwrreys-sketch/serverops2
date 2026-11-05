package com.serverops.fabric.sidemenu.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.serverops.fabric.client.ClientNet;
import com.serverops.fabric.sidemenu.MenuConfig;
import com.serverops.fabric.sidemenu.MenuNet;
import com.serverops.fabric.sidemenu.client.gui.SideMenuScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Client entrypoint of the side menu. It is registered as a SECOND "client" entrypoint in fabric.mod.json,
 * next to ServerOpsClient, so ServerOpsClient does not need a single changed line.
 */
public class MenuClient implements ClientModInitializer {
    /** Opens / closes the side menu. Rebindable under Controls > Server Ops. */
    public static KeyMapping MENU_KEY;
    /** Toggles freecam without opening the menu. */
    public static KeyMapping FREECAM_KEY;

    private boolean handshakeDone;
    private boolean autoOpened;

    @Override
    public void onInitializeClient() {
        // same category as the admin panel's "O" key so both show up together in Controls
        MENU_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.serverops_menu.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.serverops"));
        FREECAM_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.serverops_menu.freecam", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, "key.categories.serverops"));

        // ---- server -> client packets (decode now, apply on the client thread)
        ClientPlayNetworking.registerGlobalReceiver(MenuNet.S2C_PROFILE, (client, handler, buf, sender) -> {
            MenuNet.MyProfile profile = MenuNet.MyProfile.read(buf);
            client.execute(() -> MenuClientState.applyProfile(profile));
        });
        ClientPlayNetworking.registerGlobalReceiver(MenuNet.S2C_DIRECTORY, (client, handler, buf, sender) -> {
            var list = MenuNet.readDirectory(buf);
            client.execute(() -> MenuClientState.applyDirectory(list));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            MenuClientState.reset();
            Freecam.forceStop();
            handshakeDone = false;
            autoOpened = false;
        }));

        ClientTickEvents.START_CLIENT_TICK.register(Freecam::beforeTick);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        MenuOverlay.register();
    }

    private void onTick(Minecraft mc) {
        Freecam.afterTick(mc);

        while (MENU_KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null) mc.setScreen(new SideMenuScreen(SideMenuScreen.Page.NONE, false));
        }
        while (FREECAM_KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null) Freecam.toggle();
        }

        if (mc.player == null) return;

        // Once per session, shortly after joining: ask for my profile, and ask the server's EXISTING admin
        // check (via the panel's own refresh packet) whether I am allowed - that fills ClientState.allowed().
        if (!handshakeDone && mc.player.tickCount > 10) {
            handshakeDone = true;
            MenuClientNet.request();
            ClientNet.requestRefresh();
        }

        // New players: pop the Character tab open once, when the world is up and no other screen is open.
        if (MenuConfig.AUTO_OPEN_CHARACTER_FOR_NEW_PLAYERS && !autoOpened && MenuClientState.profileLoaded
                && !MenuClientState.profile.created() && mc.screen == null && mc.player.tickCount > 40) {
            autoOpened = true;
            mc.setScreen(new SideMenuScreen(SideMenuScreen.Page.CHARACTER, false));
        }
    }
}
