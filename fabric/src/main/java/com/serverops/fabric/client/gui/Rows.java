package com.serverops.fabric.client.gui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

import com.serverops.fabric.client.ClientState;
import com.serverops.fabric.net.Net;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** Factory + implementations of every row type used by the tabs. */
public final class Rows {
    private Rows() {}

    // ================================================================ button model

    public record Btn(String label, Runnable action, BooleanSupplier enabled, BooleanSupplier selected,
                      boolean dangerous, int dot) {
        public static Btn of(String label, Runnable action) {
            return new Btn(label, action, () -> true, () -> false, false, -1);
        }

        public Btn when(BooleanSupplier e) {
            return new Btn(label, action, e, selected, dangerous, dot);
        }

        public Btn selected(BooleanSupplier s) {
            return new Btn(label, action, enabled, s, dangerous, dot);
        }

        public Btn danger() {
            return new Btn(label, action, enabled, selected, true, dot);
        }

        public Btn dot(int rgb) {
            return new Btn(label, action, enabled, selected, dangerous, rgb);
        }
    }

    // ================================================================ simple rows

    public static Row space(int h) {
        return new Row() {
            @Override public int height(int w) { return h; }
            @Override public void render(GuiGraphics g, int x, int y, int w, int mx, int my) { }
        };
    }

    /** Page heading with a one line description. */
    public static Row heading(String title, String sub) {
        return new Row() {
            @Override public int height(int w) { return 26; }

            @Override
            public void render(GuiGraphics g, int x, int y, int w, int mx, int my) {
                Theme.text(g, Component.literal(title).withStyle(ChatFormatting.BOLD), x, y, Theme.BEIGE);
                Theme.text(g, Theme.fit(sub, w), x, y + 12, Theme.MUTED);
            }
        };
    }

    /** "ROLE ------------" divider. */
    public static Row section(String title) {
        return new Row() {
            @Override public int height(int w) { return 16; }

            @Override
            public void render(GuiGraphics g, int x, int y, int w, int mx, int my) {
                String t = title.toUpperCase();
                Theme.text(g, t, x, y + 5, Theme.MUTED);
                int lx = x + Theme.font().width(t) + 6;
                if (lx < x + w) g.fill(lx, y + 9, x + w, y + 10, Theme.BORDER);
            }
        };
    }

    /** Title + muted description (e.g. above a text field). */
    public static Row label(String title, String sub) {
        return new Row() {
            @Override public int height(int w) { return 24; }

            @Override
            public void render(GuiGraphics g, int x, int y, int w, int mx, int my) {
                Theme.text(g, title, x, y + 1, Theme.TEXT);
                Theme.text(g, Theme.fit(sub, w), x, y + 12, Theme.MUTED);
            }
        };
    }

    /** Wrapped info box with a gold bar on the left. */
    public static Row note(String text) {
        return new Row() {
            private List<FormattedCharSequence> lines(int w) {
                return Theme.font().split(Component.literal(text), Math.max(20, w - 16));
            }

            @Override public int height(int w) { return lines(w).size() * 10 + 10; }

            @Override
            public void render(GuiGraphics g, int x, int y, int w, int mx, int my) {
                List<FormattedCharSequence> ls = lines(w);
                int h = ls.size() * 10 + 10;
                Theme.round(g, x, y, w, h, Theme.PANEL);
                g.fill(x, y, x + 2, y + h, Theme.GOLD);
                for (int i = 0; i < ls.size(); i++) g.drawString(Theme.font(), ls.get(i), x + 9, y + 5 + i * 10, Theme.MUTED, false);
            }
        };
    }

    /** Clickable text link, e.g. "< ALL PLAYERS". */
    public static Row link(String text, Runnable action) {
        return new Row() {
            @Override public int height(int w) { return 14; }

            @Override
            public void render(GuiGraphics g, int x, int y, int w, int mx, int my) {
                boolean hover = Theme.in(mx, my, x, y, Theme.font().width(text) + 6, 12);
                Theme.text(g, text, x, y + 2, hover ? Theme.GOLD : Theme.MUTED);
            }

            @Override
            public boolean click(int x, int y, int w, double mx, double my, int button) {
                if (button == 0 && Theme.in(mx, my, x, y, Theme.font().width(text) + 6, 12)) {
                    action.run();
                    return true;
                }
                return false;
            }
        };
    }

    // ================================================================ toggle

    public static Row toggle(String title, String desc, BooleanSupplier get, Consumer<Boolean> set, BooleanSupplier enabled) {
        return new Row() {
            @Override public int height(int w) { return 28; }

            @Override
            public void render(GuiGraphics g, int x, int y, int w, int mx, int my) {
                boolean en = enabled.getAsBoolean();
                boolean on = get.getAsBoolean();
                if (en && Theme.in(mx, my, x - 4, y, w + 8, 28)) Theme.round(g, x - 4, y, w + 8, 28, Theme.PANEL);
                int textW = w - 70;
                Theme.text(g, Theme.fit(title, textW), x, y + 3, en ? Theme.TEXT : Theme.DISABLED);
                Theme.text(g, Theme.fit(desc, textW), x, y + 14, en ? Theme.MUTED : Theme.DISABLED);
                Theme.toggle(g, x + w - 26, y + 8, on, en);
                Theme.textRight(g, on ? "ON" : "OFF", x + w - 32, y + 10, on ? Theme.GOLD : Theme.DISABLED);
            }

            @Override
            public boolean click(int x, int y, int w, double mx, double my, int button) {
                if (button == 0 && enabled.getAsBoolean() && Theme.in(mx, my, x - 4, y, w + 8, 28)) {
                    set.accept(!get.getAsBoolean());
                    return true;
                }
                return false;
            }
        };
    }

    // ================================================================ buttons / chips

    /** Row of buttons. {@code fill}: stretch to the full width on one line; otherwise flow and wrap. */
    public static Row buttons(List<Btn> buttons, boolean fill) {
        return new ButtonsRow(buttons, fill, false);
    }

    /** A single button aligned to the right edge. */
    public static Row rightButton(Btn b) {
        return new ButtonsRow(List.of(b), false, true);
    }

    private static final class ButtonsRow implements Row {
        private static final int BH = 18;
        private static final int LINE = BH + 4;
        private final List<Btn> btns;
        private final boolean fill;
        private final boolean right;

        ButtonsRow(List<Btn> btns, boolean fill, boolean right) {
            this.btns = btns;
            this.fill = fill;
            this.right = right;
        }

        /** returns {x, line, width} per button */
        private int[][] layout(int w) {
            Font f = Theme.font();
            int n = btns.size();
            int[][] r = new int[n][3];
            if (fill) {
                int bw = (w - 4 * (n - 1)) / Math.max(1, n);
                for (int i = 0; i < n; i++) r[i] = new int[] {i * (bw + 4), 0, bw};
                return r;
            }
            int x = 0;
            int line = 0;
            for (int i = 0; i < n; i++) {
                Btn b = btns.get(i);
                int bw = f.width(b.label()) + 18 + (b.dot() >= 0 ? 10 : 0);
                if (x > 0 && x + bw > w) {
                    x = 0;
                    line++;
                }
                r[i] = new int[] {x, line, bw};
                x += bw + 4;
            }
            if (right && n == 1) r[0][0] = w - r[0][2];
            return r;
        }

        @Override
        public int height(int w) {
            int lines = 1;
            for (int[] r : layout(w)) lines = Math.max(lines, r[1] + 1);
            return lines * LINE - 4;
        }

        @Override
        public void render(GuiGraphics g, int x, int y, int w, int mx, int my) {
            int[][] l = layout(w);
            for (int i = 0; i < l.length; i++) {
                Btn b = btns.get(i);
                int bx = x + l[i][0];
                int by = y + l[i][1] * LINE;
                boolean en = b.enabled().getAsBoolean();
                boolean hover = en && Theme.in(mx, my, bx, by, l[i][2], BH);
                Theme.button(g, bx, by, l[i][2], BH, b.label(), en, b.selected().getAsBoolean(), b.dangerous(), hover, b.dot());
            }
        }

        @Override
        public boolean click(int x, int y, int w, double mx, double my, int button) {
            if (button != 0) return false;
            int[][] l = layout(w);
            for (int i = 0; i < l.length; i++) {
                Btn b = btns.get(i);
                if (Theme.in(mx, my, x + l[i][0], y + l[i][1] * LINE, l[i][2], BH)) {
                    if (b.enabled().getAsBoolean()) b.action().run();
                    return true;
                }
            }
            return false;
        }
    }

    // ================================================================ slider

    public static Row slider(String title, String desc, int steps, IntSupplier get, IntConsumer set, IntFunction<String> label) {
        return new Row() {
            private void setFrom(int x, int w, double mx) {
                double frac = (mx - x - 3) / Math.max(1, w - 6);
                int step = (int) Math.round(Math.max(0, Math.min(1, frac)) * (steps - 1));
                set.accept(step);
            }

            @Override public int height(int w) { return 42; }

            @Override
            public void render(GuiGraphics g, int x, int y, int w, int mx, int my) {
                Theme.text(g, title, x, y + 1, Theme.TEXT);
                Theme.text(g, Theme.fit(desc, w - 70), x, y + 12, Theme.MUTED);
                Component val = Component.literal(label.apply(get.getAsInt())).withStyle(ChatFormatting.BOLD);
                Theme.text(g, val, x + w - Theme.font().width(val), y + 1, Theme.BEIGE);
                int ty = y + 31;
                g.fill(x, ty, x + w, ty + 2, 0xFF2B2B31);
                int hx = x + Math.round((w - 7) * (get.getAsInt() / (float) Math.max(1, steps - 1)));
                g.fill(x, ty, hx + 3, ty + 2, Theme.GOLD);
                Theme.round(g, hx, ty - 5, 7, 12, Theme.BEIGE);
            }

            @Override
            public boolean click(int x, int y, int w, double mx, double my, int button) {
                if (button != 0 || !Theme.in(mx, my, x - 4, y + 22, w + 8, 20)) return false;
                setFrom(x, w, mx);
                return true;
            }

            @Override
            public boolean drag(int x, int y, int w, double mx, double my) {
                setFrom(x, w, mx);
                return true;
            }
        };
    }

    // ================================================================ text input

    public static Row input(String title, String desc, EditBox box) {
        return new Row() {
            @Override public int height(int w) { return 46; }

            private boolean inside(int x, int y, int w, double mx, double my) {
                return Theme.in(mx, my, x, y + 24, w, 20);
            }

            @Override
            public void render(GuiGraphics g, int x, int y, int w, int mx, int my) {
                Theme.text(g, title, x, y + 1, Theme.TEXT);
                Theme.text(g, Theme.fit(desc, w), x, y + 12, Theme.MUTED);
                Theme.roundBorder(g, x, y + 24, w, 20, Theme.INPUT, box.isFocused() ? Theme.GOLD : Theme.BORDER);
                box.setX(x + 6);
                box.setY(y + 24 + 6);
                box.setWidth(w - 12);
                box.render(g, mx, my, 0f);
            }

            @Override
            public boolean click(int x, int y, int w, double mx, double my, int button) {
                if (!inside(x, y, w, mx, my)) return false;
                box.setFocused(true);
                int cx = (int) Math.max(box.getX(), Math.min(box.getX() + box.getWidth() - 1, mx));
                box.mouseClicked(cx, box.getY() + 2, button);
                return true;
            }

            @Override
            public boolean key(int key, int scan, int mods) {
                return box.isFocused() && box.keyPressed(key, scan, mods);
            }

            @Override
            public boolean chr(char c, int mods) {
                return box.isFocused() && box.charTyped(c, mods);
            }

            @Override
            public void blur() {
                box.setFocused(false);
            }
        };
    }

    // ================================================================ players

    public static ResourceLocation skin(UUID id) {
        ClientPacketListener c = Minecraft.getInstance().getConnection();
        PlayerInfo info = c == null ? null : c.getPlayerInfo(id);
        return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(id);
    }

    public static Row playerCard(Net.PlayerEntry p, Runnable select) {
        return new Row() {
            @Override public int height(int w) { return 30; }

            @Override
            public void render(GuiGraphics g, int x, int y, int w, int mx, int my) {
                boolean hover = Theme.in(mx, my, x, y, w, 30);
                Theme.roundBorder(g, x, y, w, 30, hover ? Theme.HOVER : Theme.PANEL2, hover ? Theme.GOLD : Theme.BORDER);
                PlayerFaceRenderer.draw(g, skin(p.id()), x + 5, y + 5, 20);
                int btnW = 50;
                int textW = w - 36 - btnW - 8;
                Net.RoleInfo role = ClientState.role(p.roleId());
                String name = p.name();
                Theme.text(g, Theme.fit(name, textW), x + 32, y + 6, Theme.TEXT);
                String sub = role != null ? role.name() : (p.master() ? "Operator" : "No role");
                int subColor = role != null ? 0xFF000000 | role.color() : Theme.MUTED;
                Theme.text(g, Theme.fit("@" + p.name() + "  -  " + sub, textW), x + 32, y + 17, subColor);
                Theme.button(g, x + w - btnW - 5, y + 6, btnW, 18, "SELECT", true, false, false, hover, -1);
            }

            @Override
            public boolean click(int x, int y, int w, double mx, double my, int button) {
                if (button == 0 && Theme.in(mx, my, x, y, w, 30)) {
                    select.run();
                    return true;
                }
                return false;
            }
        };
    }

    /** Big avatar + display name + @username (short id). */
    public static Row profileHeader(Net.PlayerEntry p) {
        return new Row() {
            @Override public int height(int w) { return 52; }

            @Override
            public void render(GuiGraphics g, int x, int y, int w, int mx, int my) {
                Theme.roundBorder(g, x, y, 52, 52, Theme.PANEL2, Theme.BORDER);
                PlayerFaceRenderer.draw(g, skin(p.id()), x + 4, y + 4, 44);
                Theme.text(g, Component.literal(p.name()).withStyle(ChatFormatting.BOLD), x + 62, y + 8, Theme.TEXT);
                Theme.text(g, "@" + p.name() + "  (" + p.id().toString().substring(0, 8) + ")", x + 62, y + 22, Theme.MUTED);
                Net.RoleInfo role = ClientState.role(p.roleId());
                String r = role != null ? role.name() : (p.master() ? "Operator / Owner" : "No role");
                int rc = role != null ? 0xFF000000 | role.color() : Theme.MUTED;
                Theme.text(g, r, x + 62, y + 35, rc);
            }
        };
    }

    // ================================================================ bans

    private static final SimpleDateFormat DATE = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public static Row banEntry(Net.BanEntry b, boolean canUnban, Runnable unban) {
        return new Row() {
            private boolean hasNote() {
                return !b.privateReason().isBlank();
            }

            @Override public int height(int w) { return hasNote() ? 42 : 32; }

            @Override
            public void render(GuiGraphics g, int x, int y, int w, int mx, int my) {
                int h = height(w);
                Theme.roundBorder(g, x, y, w, h, Theme.PANEL2, Theme.BORDER);
                int btnW = 52;
                int textW = w - btnW - 18;
                Theme.text(g, Theme.fit(b.name(), textW), x + 7, y + 5, Theme.TEXT);
                String exp = b.expires() < 0 ? "Permanent" : "Until " + DATE.format(new Date(b.expires()));
                Theme.text(g, Theme.fit("Reason: " + b.publicReason() + "  -  " + exp, textW), x + 7, y + 16, Theme.MUTED);
                if (hasNote()) Theme.text(g, Theme.fit("Staff note: " + b.privateReason(), textW), x + 7, y + 27, 0xFF8F8466);
                boolean hover = canUnban && Theme.in(mx, my, x + w - btnW - 5, y + 7, btnW, 18);
                Theme.button(g, x + w - btnW - 5, y + 7, btnW, 18, "UNBAN", canUnban, false, false, hover, -1);
            }

            @Override
            public boolean click(int x, int y, int w, double mx, double my, int button) {
                if (button == 0 && canUnban && Theme.in(mx, my, x + w - 57, y + 7, 52, 18)) {
                    unban.run();
                    return true;
                }
                return false;
            }
        };
    }
}
