package com.serverops.fabric.sidemenu.client.gui;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

import com.serverops.fabric.client.ClientState;
import com.serverops.fabric.client.gui.Row;
import com.serverops.fabric.client.gui.Rows;
import com.serverops.fabric.client.gui.Theme;
import com.serverops.fabric.net.Net;
import com.serverops.fabric.sidemenu.client.MenuClientState;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;

/**
 * Extra row types for the side menu. They implement the existing {@link Row} interface and only read the
 * existing {@link Theme}; nothing in the admin panel is modified.
 */
final class MenuRows {
    private MenuRows() {}

    // ================================================================ read-only identity fields (Photo 2)

    /** One cell of the identity grid: small label on top, value below, optional padlock. */
    record Cell(String label, String value, boolean locked) {}

    /** A line of 1-3 equal-width "field cards" (label / value / padlock), like the profile screen reference. */
    static Row fields(List<Cell> cells) {
        return new Row() {
            @Override public int height(int w) { return 30; }

            @Override
            public void render(GuiGraphics g, int x, int y, int w, int mx, int my) {
                int n = cells.size();
                int cw = (w - 4 * (n - 1)) / Math.max(1, n);
                for (int i = 0; i < n; i++) {
                    Cell c = cells.get(i);
                    int cx = x + i * (cw + 4);
                    Theme.roundBorder(g, cx, y, cw, 30, Theme.PANEL, Theme.BORDER);
                    // tiny label (75% size)
                    g.pose().pushPose();
                    g.pose().translate(cx + 7, y + 5, 0);
                    g.pose().scale(0.75f, 0.75f, 1f);
                    g.drawString(Theme.font(), c.label().toUpperCase(), 0, 0, Theme.MUTED, false);
                    g.pose().popPose();
                    Theme.text(g, Theme.fit(c.value(), cw - 14 - (c.locked() ? 12 : 0)), cx + 7, y + 16, Theme.TEXT);
                    if (c.locked()) padlock(g, cx + cw - 13, y + 17, Theme.MUTED);
                }
            }
        };
    }

    /** 7x8 padlock drawn from rectangles (no texture needed). */
    static void padlock(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y, x + 6, y + 1, color);       // shackle top
        g.fill(x + 1, y, x + 2, y + 4, color);       // shackle left
        g.fill(x + 5, y, x + 6, y + 4, color);       // shackle right
        g.fill(x, y + 3, x + 7, y + 8, color);       // body
        g.fill(x + 3, y + 5, x + 4, y + 7, Theme.PANEL); // keyhole
    }

    // ================================================================ year picker  [<]  1985  [>]

    /** Birth-year selector: two arrow buttons around the current value, with a small side note (e.g. the age). */
    static Row yearPicker(String title, String desc, int min, int max, IntSupplier get, IntConsumer set,
                          IntFunction<String> note) {
        return new Row() {
            private static final int BTN = 22;

            @Override public int height(int w) { return 46; }

            @Override
            public void render(GuiGraphics g, int x, int y, int w, int mx, int my) {
                Theme.text(g, title, x, y + 1, Theme.TEXT);
                Theme.text(g, Theme.fit(desc, w), x, y + 12, Theme.MUTED);
                int v = get.getAsInt();
                int by = y + 24;
                Theme.button(g, x, by, BTN, 20, "<", v > min, false, false, Theme.in(mx, my, x, by, BTN, 20) && v > min, -1);
                Theme.button(g, x + w - BTN, by, BTN, 20, ">", v < max, false, false,
                        Theme.in(mx, my, x + w - BTN, by, BTN, 20) && v < max, -1);
                int bx = x + BTN + 4;
                int bw = w - 2 * BTN - 8;
                Theme.roundBorder(g, bx, by, bw, 20, Theme.INPUT, Theme.BORDER);
                Theme.textCentered(g, String.valueOf(v), bx + bw / 2, by + 6, Theme.BEIGE);
                Theme.textRight(g, note.apply(v), bx + bw - 6, by + 6, Theme.MUTED);
            }

            @Override
            public boolean click(int x, int y, int w, double mx, double my, int button) {
                if (button != 0) return false;
                int by = y + 24;
                if (Theme.in(mx, my, x, by, BTN, 20)) {
                    set.accept(Math.max(min, get.getAsInt() - 1));
                    return true;
                }
                if (Theme.in(mx, my, x + w - BTN, by, BTN, 20)) {
                    set.accept(Math.min(max, get.getAsInt() + 1));
                    return true;
                }
                return false;
            }
        };
    }

    // ================================================================ player list card (Photo 3)

    /** Face, roleplay name, "@username - Playtime ...", role tag, and "YOU" on your own row. */
    static Row playerCard(MenuClientState.Online p) {
        return new Row() {
            @Override public int height(int w) { return 30; }

            @Override
            public void render(GuiGraphics g, int x, int y, int w, int mx, int my) {
                boolean hover = Theme.in(mx, my, x, y, w, 30);
                Theme.roundBorder(g, x, y, w, 30, hover ? Theme.HOVER : Theme.PANEL2, p.me() ? Theme.GOLD : Theme.BORDER);
                PlayerFaceRenderer.draw(g, Rows.skin(p.id()), x + 5, y + 5, 20);

                int rightReserve = 0;
                if (p.me()) {
                    Theme.textRight(g, "YOU", x + w - 7, y + 6, Theme.MUTED);
                    rightReserve = 26;
                }
                Theme.text(g, Theme.fit(p.display(), w - 40 - rightReserve), x + 32, y + 6, Theme.TEXT);

                String time = MenuClientState.formatPlaytime(MenuClientState.playSeconds(p.id()));
                // role tag colour/name come from the admin panel's existing public client state (read only)
                Net.Tag tag = ClientState.tag(p.id());
                String sub = "@" + p.username() + "  -  Playtime " + time;
                int subW = w - 40;
                if (tag != null) {
                    String t = Theme.fit(tag.name(), 70);
                    Theme.textRight(g, t, x + w - 7, y + 17, 0xFF000000 | tag.color());
                    subW -= Theme.font().width(t) + 8;
                }
                Theme.text(g, Theme.fit(sub, subW), x + 32, y + 17, Theme.MUTED);
            }
        };
    }
}
