package com.serverops.fabric.adminlog.client;

import java.util.ArrayList;
import java.util.List;

import com.serverops.fabric.adminlog.AdminLogNet;
import com.serverops.fabric.client.gui.Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The admin-notification cards in the TOP-RIGHT corner. Same look as the admin panel: dark #121214 card,
 * 1px border, gold/beige text, a coloured accent bar per action type. Cards stack downwards (never overlap),
 * slide in from the right, and fade out by themselves.
 */
public final class AdminToasts {
    private AdminToasts() {}

    // ---- tweak here ----
    private static final long LIFE_MS = 6500;     // total time on screen
    private static final long FADE_IN_MS = 260;   // slide-in
    private static final long FADE_OUT_MS = 800;  // fade-out
    private static final int MAX_VISIBLE = 5;     // older cards are dropped when more arrive
    private static final int WIDTH = 180;
    private static final int GAP = 4;
    private static final int MARGIN = 6;

    private record Card(AdminLogNet.Toast data, long born) {}

    private static final List<Card> CARDS = new ArrayList<>();

    static void push(AdminLogNet.Toast t) {
        CARDS.add(new Card(t, System.currentTimeMillis()));
        while (CARDS.size() > MAX_VISIBLE) CARDS.remove(0);
    }

    static void clear() {
        CARDS.clear();
    }

    private static int accent(AdminLogNet.Kind k) {
        return switch (k) {
            case BAN -> Theme.RED;
            case FREEZE -> 0xFF5AA9E6;
            case GOD -> Theme.GOLD;
            case INVIS -> 0xFFA78BFA;
            case VIEW -> 0xFF7BC47F;
            case ROLE -> Theme.BEIGE;
            case WORLD -> 0xFF6FB7C9;
            default -> Theme.MUTED;
        };
    }

    /** Same colour with its alpha multiplied by {@code a} (0..1). */
    private static int fade(int argb, float a) {
        int al = Math.round(((argb >>> 24) & 0xFF) * a);
        return (al << 24) | (argb & 0xFFFFFF);
    }

    public static void render(GuiGraphics g, Minecraft mc) {
        if (CARDS.isEmpty() || mc.player == null || mc.options.hideGui) return;
        long now = System.currentTimeMillis();
        CARDS.removeIf(c -> now - c.born() > LIFE_MS);

        int screenW = mc.getWindow().getGuiScaledWidth();
        // vanilla draws potion-effect icons in the top-right; start below them when any are active
        int y = mc.player.getActiveEffects().isEmpty() ? MARGIN : 56;

        for (Card c : CARDS) {
            long age = now - c.born();
            float in = Math.min(1f, age / (float) FADE_IN_MS);
            float out = Math.min(1f, (LIFE_MS - age) / (float) FADE_OUT_MS);
            float alpha = Math.max(0f, Math.min(1f, Math.min(in, out)));
            float ease = 1f - (1f - in) * (1f - in);
            int slide = Math.round((1f - ease) * (WIDTH + MARGIN));

            // text: first part = headline, the rest (e.g. "Reason: ..", "Duration: ..") = muted detail lines
            String[] parts = c.data().text().split("\\s{2}\\|\\s{2}");
            List<FormattedCharSequence> head = Theme.font().split(Component.literal(parts[0]), WIDTH - 20);
            List<List<FormattedCharSequence>> details = new ArrayList<>();
            int lines = head.size();
            for (int i = 1; i < parts.length; i++) {
                List<FormattedCharSequence> l = Theme.font().split(Component.literal(parts[i]), WIDTH - 20);
                details.add(l);
                lines += l.size();
            }
            int h = 8 + 11 + lines * 10 + 5;
            int x = screenW - WIDTH - MARGIN + slide;

            Theme.roundBorder(g, x, y, WIDTH, h, fade(Theme.BG, alpha * 0.96f), fade(Theme.BORDER, alpha));
            g.fill(x, y + 1, x + 2, y + h - 1, fade(accent(c.data().kind()), alpha));

            if (alpha > 0.06f) {
                // top row: acting admin on the left, action type on the right
                Theme.text(g, Theme.fit(c.data().actor(), WIDTH - 80), x + 9, y + 6, fade(Theme.BEIGE, alpha));
                Theme.textRight(g, c.data().kind().label, x + WIDTH - 7, y + 6, fade(accent(c.data().kind()), alpha));
                int ty = y + 19;
                for (FormattedCharSequence s : head) {
                    g.drawString(Theme.font(), s, x + 9, ty, fade(Theme.TEXT, alpha), false);
                    ty += 10;
                }
                for (List<FormattedCharSequence> l : details) {
                    for (FormattedCharSequence s : l) {
                        g.drawString(Theme.font(), s, x + 9, ty, fade(Theme.MUTED, alpha), false);
                        ty += 10;
                    }
                }
            }
            y += h + GAP; // next card goes BELOW this one: no overlap
        }
    }
}
