package com.serverops.fabric.client.gui;

import java.util.List;

import com.serverops.fabric.client.ClientState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The compact, centered dashboard window (NOT full screen): header with search, a three-tab sidebar
 * (Server / Players / Roles) and one scrollable content pane.
 */
public final class OpsScreen extends Screen {
    private static final int SIDE_W = 88;
    private static final int HEADER_H = 38;
    private static final String SUBTITLE = "Private server tools, player controls, and role setup.";

    private final Tab[] tabs = new Tab[3];
    private final ScrollPane pane = new ScrollPane();
    private EditBox search;
    private int active = 1; // open on Players, like the reference design

    private int wx;
    private int wy;
    private int ww;
    private int wh;
    private int seenVersion = -1;
    private boolean dirty = true;

    /** Lower-case text of the header search box; tabs use it to filter. */
    public String query = "";

    public OpsScreen() {
        super(Component.literal("Server Ops"));
    }

    public void markDirty() {
        dirty = true;
    }

    public void resetScroll() {
        pane.resetScroll();
    }

    @Override
    protected void init() {
        ww = Math.min(width - 8, 440);
        wh = Math.min(height - 8, 290);
        wx = (width - ww) / 2;
        wy = (height - wh) / 2;

        pane.x = wx + SIDE_W;
        pane.y = wy + HEADER_H;
        pane.w = ww - SIDE_W - 1;
        pane.h = wh - HEADER_H - 1;

        if (tabs[0] == null) {
            tabs[0] = new ServerTab(this);
            tabs[1] = new PlayersTab(this);
            tabs[2] = new RolesTab(this);
        }
        if (search == null) {
            search = Theme.box("Search this panel", 32);
            search.setResponder(v -> {
                query = v.trim().toLowerCase();
                search.setSuggestion(v.isEmpty() ? "Search this panel" : null);
                markDirty();
            });
        }
        markDirty();
    }

    private void setTab(int i) {
        if (i == active) return;
        active = i;
        pane.resetScroll();
        tabs[i].onEnter();
        markDirty();
    }

    private void rebuild() {
        List<Row> rows = tabs[active].build();
        pane.setRows(rows);
        seenVersion = ClientState.version;
        dirty = false;
    }

    // ------------------------------------------------------------------ render

    private int closeX() {
        return wx + ww - 22;
    }

    private int searchX() {
        return wx + ww - 22 - 8 - 120;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        if (dirty || seenVersion != ClientState.version) rebuild();

        // window
        Theme.roundBorder(g, wx, wy, ww, wh, Theme.BG, Theme.BORDER);

        // header: title + subtitle (or the latest server notice)
        g.pose().pushPose();
        g.pose().translate(wx + 14, wy + 9, 0);
        g.pose().scale(1.4f, 1.4f, 1f);
        g.drawString(font, Component.literal("SERVER OPS").withStyle(ChatFormatting.BOLD), 0, 0, Theme.BEIGE, false);
        g.pose().popPose();

        boolean showNotice = System.currentTimeMillis() < ClientState.noticeUntil && !ClientState.notice.isEmpty();
        String sub = showNotice ? ClientState.notice : SUBTITLE;
        Theme.text(g, Theme.fit(sub, ww - 28), wx + 14, wy + 26, showNotice ? Theme.GOLD : Theme.MUTED);

        // search box
        int sx = searchX();
        Theme.roundBorder(g, sx, wy + 8, 120, 16, Theme.INPUT, search.isFocused() ? Theme.GOLD : Theme.BORDER);
        search.setX(sx + 6);
        search.setY(wy + 8 + 4);
        search.setWidth(108);
        search.render(g, mx, my, partial);

        // close
        boolean closeHover = Theme.in(mx, my, closeX() - 3, wy + 6, 16, 18);
        Theme.text(g, "X", closeX() + 2, wy + 12, closeHover ? Theme.GOLD : Theme.MUTED);

        g.fill(wx + 1, wy + HEADER_H, wx + ww - 1, wy + HEADER_H + 1, Theme.BORDER);

        // sidebar
        g.fill(wx + 1, wy + HEADER_H + 1, wx + SIDE_W, wy + wh - 1, Theme.SIDE);
        g.fill(wx + SIDE_W, wy + HEADER_H + 1, wx + SIDE_W + 1, wy + wh - 1, Theme.BORDER);
        for (int i = 0; i < tabs.length; i++) {
            int ty = wy + HEADER_H + 10 + i * 24;
            boolean sel = i == active;
            boolean hover = Theme.in(mx, my, wx + 6, ty, SIDE_W - 12, 20);
            if (sel) {
                Theme.round(g, wx + 6, ty, SIDE_W - 12, 20, Theme.PANEL2);
                g.fill(wx + 6, ty + 3, wx + 8, ty + 17, Theme.GOLD);
            } else if (hover) {
                Theme.round(g, wx + 6, ty, SIDE_W - 12, 20, Theme.PANEL);
            }
            Theme.text(g, tabs[i].title(), wx + 16, ty + 6, sel ? Theme.BEIGE : (hover ? Theme.TEXT : Theme.MUTED));
        }

        // content
        pane.render(g, mx, my);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (Theme.in(mx, my, closeX() - 3, wy + 6, 16, 18)) {
                onClose();
                return true;
            }
            for (int i = 0; i < tabs.length; i++) {
                if (Theme.in(mx, my, wx + 6, wy + HEADER_H + 10 + i * 24, SIDE_W - 12, 20)) {
                    setTab(i);
                    return true;
                }
            }
            if (Theme.in(mx, my, searchX(), wy + 8, 120, 16)) {
                pane.blur();
                search.setFocused(true);
                int cx = (int) Math.max(search.getX(), Math.min(search.getX() + search.getWidth() - 1, mx));
                search.mouseClicked(cx, search.getY() + 2, button);
                return true;
            }
        }
        search.setFocused(false);
        if (pane.click(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && pane.drag(mx, my)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        pane.release();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (pane.scroll(mx, my, delta)) return true;
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (search.isFocused() && search.keyPressed(key, scan, mods)) return true;
        if (pane.key(key, scan, mods)) return true;
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (search.isFocused() && search.charTyped(c, mods)) return true;
        if (pane.chr(c, mods)) return true;
        return super.charTyped(c, mods);
    }
}
