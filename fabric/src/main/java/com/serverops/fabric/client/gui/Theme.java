package com.serverops.fabric.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Premium dark palette (#121214 + gold / beige accents) and a few drawing helpers. */
public final class Theme {
    private Theme() {}

    public static final int BG = 0xFF121214;
    public static final int SIDE = 0xFF0E0E10;
    public static final int PANEL = 0xFF18181B;
    public static final int PANEL2 = 0xFF1F1F23;
    public static final int HOVER = 0xFF27272C;
    public static final int INPUT = 0xFF0C0C0E;
    public static final int BORDER = 0xFF2A2A2F;
    public static final int TEXT = 0xFFEDE8DC;
    public static final int MUTED = 0xFF8B8B94;
    public static final int DISABLED = 0xFF55555C;
    public static final int GOLD = 0xFFD8B25A;
    public static final int BEIGE = 0xFFEADFC4;
    public static final int DARK_TEXT = 0xFF1A1A1C;
    public static final int RED = 0xFFE87A84;
    public static final int RED_BG = 0xFF33191D;
    public static final int RED_BORDER = 0xFF8A3A44;

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Filled rectangle with 1px cut corners. */
    public static void round(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + w, y + h - 1, color);
    }

    public static void roundBorder(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        round(g, x, y, w, h, border);
        round(g, x + 1, y + 1, w - 2, h - 2, fill);
    }

    public static void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font(), s, x, y, color, false);
    }

    public static void text(GuiGraphics g, Component c, int x, int y, int color) {
        g.drawString(font(), c, x, y, color, false);
    }

    public static void textRight(GuiGraphics g, String s, int right, int y, int color) {
        g.drawString(font(), s, right - font().width(s), y, color, false);
    }

    public static void textCentered(GuiGraphics g, String s, int cx, int y, int color) {
        g.drawString(font(), s, cx - font().width(s) / 2, y, color, false);
    }

    /** Truncate with "..." so the text fits in maxW pixels. */
    public static String fit(String s, int maxW) {
        Font f = font();
        if (maxW <= 0) return "";
        if (f.width(s) <= maxW) return s;
        return f.plainSubstrByWidth(s, Math.max(0, maxW - f.width("..."))) + "...";
    }

    /** Small push button. */
    public static void button(GuiGraphics g, int x, int y, int w, int h, String label,
                              boolean enabled, boolean selected, boolean danger, boolean hover, int dot) {
        int fill;
        int border;
        int fg;
        if (!enabled) {
            fill = PANEL;
            border = BORDER;
            fg = DISABLED;
        } else if (selected) {
            fill = BEIGE;
            border = GOLD;
            fg = DARK_TEXT;
        } else if (danger) {
            fill = hover ? 0xFF4A2128 : RED_BG;
            border = RED_BORDER;
            fg = RED;
        } else {
            fill = hover ? HOVER : PANEL2;
            border = hover ? GOLD : BORDER;
            fg = TEXT;
        }
        roundBorder(g, x, y, w, h, fill, border);
        int tw = font().width(label) + (dot >= 0 ? 10 : 0);
        int tx = x + (w - tw) / 2;
        int ty = y + (h - 8) / 2;
        if (dot >= 0) {
            g.fill(tx, ty + 1, tx + 6, ty + 7, 0xFF000000 | dot);
            tx += 10;
        }
        text(g, label, tx, ty, fg);
    }

    /** ON / OFF switch, 26x12. */
    public static void toggle(GuiGraphics g, int x, int y, boolean on, boolean enabled) {
        int track = on ? (enabled ? BEIGE : 0xFF8A8472) : 0xFF2B2B31;
        roundBorder(g, x, y, 26, 12, track, on ? GOLD : BORDER);
        int kx = on ? x + 15 : x + 2;
        round(g, kx, y + 2, 9, 8, on ? DARK_TEXT : (enabled ? 0xFF9A9AA3 : 0xFF55555C));
    }

    /** Borderless EditBox whose hint ("suggestion") is only shown while empty. */
    public static EditBox box(String hint, int maxLength) {
        EditBox b = new EditBox(font(), 0, 0, 100, 10, Component.empty());
        b.setBordered(false);
        b.setMaxLength(maxLength);
        b.setSuggestion(hint);
        b.setResponder(v -> b.setSuggestion(v.isEmpty() ? hint : null));
        return b;
    }
}
