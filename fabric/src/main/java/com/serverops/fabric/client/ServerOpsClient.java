package com.serverops.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.serverops.fabric.client.gui.OpsScreen;
import com.serverops.fabric.net.Net;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

public class ServerOpsClient implements ClientModInitializer {
    public static KeyMapping OPEN_KEY;
    private static int refreshTimer;

    @Override
    public void onInitializeClient() {
        OPEN_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.serverops.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.serverops"));

        // ---- server -> client packets (decode now, apply on the client thread)
        ClientPlayNetworking.registerGlobalReceiver(Net.S2C_SYNC, (client, handler, buf, sender) -> {
            Net.Sync sync = Net.Sync.read(buf);
            client.execute(() -> onSync(client, sync));
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.S2C_VISUALS, (client, handler, buf, sender) -> {
            Net.Visuals visuals = Net.Visuals.read(buf);
            client.execute(() -> ClientState.applyVisuals(visuals));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientState::reset));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_KEY.consumeClick()) {
                if (mc.player != null && mc.screen == null) ClientNet.requestOpen();
            }
            // While the panel is open, poll once per second so other staff's changes show up.
            if (mc.screen instanceof OpsScreen) {
                if (++refreshTimer >= 20) {
                    refreshTimer = 0;
                    ClientNet.requestRefresh();
                }
            } else {
                refreshTimer = 0;
            }
        });

        registerSeasonColors();
    }

    private static void onSync(Minecraft mc, Net.Sync sync) {
        ClientState.applySync(sync);
        if (sync.open() && sync.allowed() && !(mc.screen instanceof OpsScreen)) {
            mc.setScreen(new OpsScreen());
        } else if (!sync.allowed() && mc.screen instanceof OpsScreen) {
            mc.setScreen(null); // access was revoked while the panel was open
        }
    }

    /** Season = tint of grass / foliage. Summer leaves the vanilla colours untouched. */
    private static void registerSeasonColors() {
        ColorProviderRegistry.BLOCK.register((state, view, pos, tintIndex) -> {
            int base = view != null && pos != null ? BiomeColors.getAverageGrassColor(view, pos) : GrassColor.get(0.5D, 1.0D);
            return ClientState.season.tintGrass(base);
        }, Blocks.GRASS_BLOCK, Blocks.GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN);

        ColorProviderRegistry.BLOCK.register((state, view, pos, tintIndex) -> {
            int base = view != null && pos != null ? BiomeColors.getAverageFoliageColor(view, pos) : FoliageColor.getDefaultColor();
            return ClientState.season.tintLeaves(base);
        }, Blocks.OAK_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES,
                Blocks.MANGROVE_LEAVES, Blocks.VINE);
    }

}
