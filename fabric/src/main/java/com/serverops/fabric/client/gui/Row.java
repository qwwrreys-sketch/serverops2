package com.serverops.fabric.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/** One horizontal strip inside a {@link ScrollPane}. Coordinates passed in are already scrolled. */
public interface Row {
    int height(int w);

    void render(GuiGraphics g, int x, int y, int w, int mx, int my);

    default boolean click(int x, int y, int w, double mx, double my, int button) {
        return false;
    }

    default boolean drag(int x, int y, int w, double mx, double my) {
        return false;
    }

    default void release() {}

    default boolean key(int key, int scan, int mods) {
        return false;
    }

    default boolean chr(char c, int mods) {
        return false;
    }

    /** Called when the user clicks anywhere else (drop text-field focus). */
    default void blur() {}
}
