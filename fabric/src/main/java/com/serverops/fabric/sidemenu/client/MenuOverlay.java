package com.serverops.fabric.sidemenu.client;

import com.serverops.fabric.client.gui.OpsScreen;
import com.serverops.fabric.client.gui.Theme;
import com.serverops.fabric.sidemenu.MenuConfig;
import com.serverops.fabric.sidemenu.client.gui.SideMenuScreen;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws the little toggle tab on the left edge of the screen (Photo 1) and makes it clickable on every
 * screen that has a free mouse cursor (chat, pause menu, inventory, even the admin panel). While plain
 * playing the mouse is captured by the game, so there the tab is only an indicator - use the menu key.
 *
 * <p>Uses Fabric's screen events, so no existing screen class is touched or subclassed.
 */
public final class MenuOverlay {
    private MenuOverlay() {}

    public static final int TAB_W = 11;
    public static final int TAB_H = 44;

    public static void register() {
        HudRenderCallback.EVENT.register(MenuOverlay::renderHud);

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            // only inside a world, and never on top of ourselves (the menu draws its own tab)
            if (client.level == null || screen instanceof SideMenuScreen) return;

            ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> {
                if (!MenuConfig.SHOW_HUD_TAB) return;
                int y = tabY(client);
                drawTab(g, 0, y, false, Theme.in(mx, my, 0, y, TAB_W, TAB_H));
            });

            ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, button) -> {
                if (MenuConfig.SHOW_HUD_TAB && button == 0 && Theme.in(mx, my, 0, tabY(client), TAB_W, TAB_H)) {
                    client.setScreen(new SideMenuScreen(SideMenuScreen.Page.NONE, s instanceof OpsScreen));
                    return false; // swallow the click
                }
                return true;
            });
        });
    }

    private static int tabY(Minecraft mc) {
        return mc.getWindow().getGuiScaledHeight() / 2 - TAB_H / 2;
    }

    /** The tab: a small dark slab with a chevron ("<" when the drawer is open, ">" when it is closed). */
    public static void drawTab(GuiGraphics g, int x, int y, boolean open, boolean hover) {
        Theme.roundBorder(g, x, y, TAB_W, TAB_H, Theme.SIDE, hover ? Theme.GOLD : Theme.BORDER);
        String chevron = open ? "<" : ">";
        Theme.text(g, chevron, x + (TAB_W - Theme.font().width(chevron)) / 2, y + (TAB_H - 8) / 2,
                hover ? Theme.GOLD : Theme.BEIGE);
    }

    // ---------------------------------------------------------------- in-game HUD

    private static void renderHud(GuiGraphics g, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        if (mc.screen == null && MenuConfig.SHOW_HUD_TAB) {
            drawTab(g, 0, tabY(mc), false, false);
        }

        if (Freecam.isActive()) {
            String key = MenuClient.FREECAM_KEY == null ? "" : MenuClient.FREECAM_KEY.getTranslatedKeyMessage().getString();
            String text = "FREECAM  -  WASD / Space / Shift to fly, Ctrl to go faster, [" + key + "] to exit";
            int w = Theme.font().width(text) + 16;
            int x = (mc.getWindow().getGuiScaledWidth() - w) / 2;
            Theme.roundBorder(g, x, 6, w, 16, Theme.BG, Theme.GOLD);
            Theme.text(g, text, x + 8, 11, Theme.BEIGE);
        }
    }
}
