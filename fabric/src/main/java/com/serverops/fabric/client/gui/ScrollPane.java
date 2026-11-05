package com.serverops.fabric.client.gui;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;

/** A clipped, smoothly scrollable vertical stack of {@link Row}s with a slim draggable scrollbar. */
public final class ScrollPane {
    public int x;
    public int y;
    public int w;
    public int h;

    private static final int PAD = 10;
    private static final int GAP = 4;
    private static final int TOP = 8;

    private List<Row> rows = List.of();
    private Row active;
    private float scroll;
    private float target;
    private int contentH;
    private boolean dragBar;

    public void setRows(List<Row> rows) {
        this.rows = rows;
        if (active != null && !rows.contains(active)) active = null;
    }

    public void resetScroll() {
        scroll = 0;
        target = 0;
    }

    private int cx() {
        return x + PAD;
    }

    private int cw() {
        return Math.max(60, w - PAD * 2 - 4);
    }

    private int[] layout() {
        int[] offs = new int[rows.size()];
        int off = TOP;
        for (int i = 0; i < offs.length; i++) {
            offs[i] = off;
            off += rows.get(i).height(cw()) + GAP;
        }
        contentH = off + TOP;
        return offs;
    }

    private void clamp() {
        float max = Math.max(0, contentH - h);
        target = Math.max(0, Math.min(max, target));
        scroll = Math.max(0, Math.min(max, scroll));
    }

    public void render(GuiGraphics g, int mx, int my) {
        int[] offs = layout();
        clamp();
        scroll += (target - scroll) * 0.35f; // smooth scrolling
        if (Math.abs(target - scroll) < 0.4f) scroll = target;

        boolean inView = Theme.in(mx, my, x, y, w, h);
        int hx = inView ? mx : -10000;
        int hy = inView ? my : -10000;

        g.enableScissor(x, y, x + w, y + h);
        for (int i = 0; i < offs.length; i++) {
            Row r = rows.get(i);
            int rh = r.height(cw());
            int ry = y - Math.round(scroll) + offs[i];
            if (ry + rh < y || ry > y + h) continue;
            r.render(g, cx(), ry, cw(), hx, hy);
        }
        g.disableScissor();

        if (contentH > h) {
            int bh = Math.max(16, h * h / contentH);
            float frac = scroll / (contentH - h);
            int by = y + 2 + Math.round((h - 4 - bh) * frac);
            g.fill(x + w - 5, y + 2, x + w - 3, y + h - 2, 0x22FFFFFF);
            g.fill(x + w - 5, by, x + w - 3, by + bh, dragBar ? Theme.GOLD : 0xAA9C8A5A);
        }
    }

    public void blur() {
        for (Row r : rows) r.blur();
    }

    public boolean click(double mx, double my, int button) {
        if (!Theme.in(mx, my, x, y, w, h)) return false;
        blur();
        if (contentH > h && mx >= x + w - 9) {
            dragBar = true;
            dragBarTo(my);
            return true;
        }
        int[] offs = layout();
        for (int i = 0; i < offs.length; i++) {
            Row r = rows.get(i);
            int rh = r.height(cw());
            int ry = y - Math.round(scroll) + offs[i];
            if (my >= ry && my < ry + rh) {
                active = r;
                return r.click(cx(), ry, cw(), mx, my, button);
            }
        }
        return true;
    }

    private void dragBarTo(double my) {
        int bh = Math.max(16, h * h / Math.max(1, contentH));
        double frac = (my - y - bh / 2.0) / Math.max(1, h - bh);
        target = (float) (Math.max(0, Math.min(1, frac)) * (contentH - h));
        scroll = target;
    }

    public boolean drag(double mx, double my) {
        if (dragBar) {
            dragBarTo(my);
            return true;
        }
        if (active == null) return false;
        int[] offs = layout();
        int i = rows.indexOf(active);
        if (i < 0) return false;
        int ry = y - Math.round(scroll) + offs[i];
        return active.drag(cx(), ry, cw(), mx, my);
    }

    public void release() {
        if (active != null) active.release();
        active = null;
        dragBar = false;
    }

    public boolean scroll(double mx, double my, double delta) {
        if (!Theme.in(mx, my, x, y, w, h)) return false;
        target -= (float) delta * 22f;
        clamp();
        return true;
    }

    public boolean key(int key, int scan, int mods) {
        for (Row r : rows) if (r.key(key, scan, mods)) return true;
        return false;
    }

    public boolean chr(char c, int mods) {
        for (Row r : rows) if (r.chr(c, mods)) return true;
        return false;
    }
}
