package com.serverops.fabric.sidemenu.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.serverops.common.menu.CharacterRules;
import com.serverops.common.menu.Race;
import com.serverops.fabric.client.ClientNet;
import com.serverops.fabric.client.ClientState;
import com.serverops.fabric.client.gui.Row;
import com.serverops.fabric.client.gui.Rows;
import com.serverops.fabric.client.gui.Rows.Btn;
import com.serverops.fabric.client.gui.ScrollPane;
import com.serverops.fabric.client.gui.Theme;
import com.serverops.fabric.sidemenu.MenuNet;
import com.serverops.fabric.sidemenu.MenuNet.CharOp;
import com.serverops.fabric.sidemenu.client.Freecam;
import com.serverops.fabric.sidemenu.client.MenuClient;
import com.serverops.fabric.sidemenu.client.MenuClientNet;
import com.serverops.fabric.sidemenu.client.MenuClientState;
import com.serverops.fabric.sidemenu.client.MenuOverlay;
import com.serverops.fabric.sidemenu.client.gui.MenuRows.Cell;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The left-side menu: a slide-out drawer (STATS / PLAYER LIST / CODES / COMMANDS) plus a centered window in
 * the same style as the admin panel that shows the selected page. Re-uses the admin panel's public
 * {@link Theme}, {@link Rows} and {@link ScrollPane} helpers so every colour, font and padding matches.
 */
public final class SideMenuScreen extends Screen {
    public enum Page { NONE, CHARACTER, PLAYERS, CODES }

    private static final int DRAW_W = 118;
    private static final int HEADER_H = 38;
    private static final int ITEM_H = 20;

    // ---------------------------------------------------------------- drawer model
    private record Item(String label, Supplier<String> badge, Runnable action, BooleanSupplier selected,
                        BooleanSupplier visible) {}

    private record Section(String title, List<Item> items) {}

    private final List<Section> sections = new ArrayList<>();

    // ---------------------------------------------------------------- state
    private final boolean adminPanelWasOpen;
    private Page page;
    private boolean drawerOpen = true;
    private float slide; // 0 = hidden, 1 = fully out (animated)

    private final ScrollPane pane = new ScrollPane();
    private EditBox search;
    private EditBox nameBox;
    private EditBox codeBox;
    private String query = "";

    private int wx;
    private int wy;
    private int ww;
    private int wh;
    private boolean dirty = true;
    private int seenVersion = -1;
    private int seenOnline;
    private int ticks;

    // drafts for the character page
    private int draftYear = CharacterRules.BIRTH_YEAR_DEFAULT;
    private Race draftRace; // null = not chosen yet (creation form)
    // two-click confirmation for actions that cost ReRolls
    private CharOp armed;
    private long armedAt;

    /**
     * @param page              page to show next to the drawer (NONE = only the drawer)
     * @param adminPanelWasOpen true when the menu was opened on top of the admin panel (then the Commands
     *                          button reads "Close Admin Panel")
     */
    public SideMenuScreen(Page page, boolean adminPanelWasOpen) {
        super(Component.literal("Menu"));
        this.page = page;
        this.adminPanelWasOpen = adminPanelWasOpen;
        buildSections();
    }

    private void buildSections() {
        sections.add(new Section("Stats", List.of(
                new Item("Character Status", () -> "", () -> open(Page.CHARACTER),
                        () -> page == Page.CHARACTER, () -> true))));

        sections.add(new Section("Player List", List.of(
                new Item("Online Players", () -> String.valueOf(MenuClientState.online("").size()),
                        () -> open(Page.PLAYERS), () -> page == Page.PLAYERS, () -> true))));

        sections.add(new Section("Codes", List.of(
                new Item("Redeem Code", () -> "", () -> open(Page.CODES), () -> page == Page.CODES, () -> true))));

        sections.add(new Section("Commands", List.of(
                new Item("Freecam", () -> Freecam.isActive() ? "ON" : "OFF", this::toggleFreecam,
                        Freecam::isActive, Freecam::canUse),
                // Only shown when the server's EXISTING admin-panel check said "allowed" (OP / panel role).
                new Item("Admin Panel", () -> adminPanelWasOpen ? "CLOSE" : "O", this::toggleAdminPanel,
                        () -> adminPanelWasOpen, ClientState::allowed))));
    }

    private void open(Page p) {
        if (page == p) return;
        page = p;
        pane.resetScroll();
        armed = null;
        markDirty();
    }

    private void toggleFreecam() {
        Freecam.toggle();
        onClose(); // back to the game so you can fly
    }

    /**
     * The "Commands" button. It does exactly what the O key does (ClientNet.requestOpen); the server then runs
     * its normal permission check and, if it says yes, the existing handler opens the admin panel.
     */
    private void toggleAdminPanel() {
        if (!ClientState.allowed()) return; // belt and braces: the button is hidden anyway
        onClose();
        if (!adminPanelWasOpen) ClientNet.requestOpen();
    }

    private void markDirty() {
        dirty = true;
    }

    // ================================================================== layout

    @Override
    protected void init() {
        int avail = width - DRAW_W - 16;
        ww = Math.max(160, Math.min(380, avail));
        wh = Math.min(height - 8, 270);
        wx = DRAW_W + 8 + Math.max(0, (avail - ww) / 2);
        wy = (height - wh) / 2;

        pane.x = wx + 1;
        pane.y = wy + HEADER_H + 1;
        pane.w = ww - 2;
        pane.h = wh - HEADER_H - 2;

        if (search == null) {
            search = Theme.box("Search players", 32);
            search.setResponder(v -> {
                query = v.trim().toLowerCase(Locale.ROOT);
                search.setSuggestion(v.isEmpty() ? "Search players" : null);
                markDirty();
            });
            nameBox = Theme.box("Type a first name", CharacterRules.NAME_MAX);
            nameBox.setFilter(s -> s.matches("[\\p{L}'-]*"));
            codeBox = Theme.box("Enter promo code", 32);
            codeBox.setFilter(s -> s.matches("[A-Za-z0-9_-]*"));
        }
        markDirty();
        // make sure we know the current admin-panel permission + our profile
        ClientNet.requestRefresh();
        MenuClientNet.request();
    }

    @Override
    public void tick() {
        ticks++;
        if (ticks % 20 == 0) ClientNet.requestRefresh();      // keeps "is the Admin Panel button allowed" fresh
        if (ticks % 60 == 0) MenuClientNet.request();          // refreshes roster + names
    }

    private int drawerX() {
        return Math.round(-(1f - slide) * DRAW_W);
    }

    private int tabY() {
        return height / 2 - MenuOverlay.TAB_H / 2;
    }

    private int closeX() {
        return wx + ww - 22;
    }

    private int searchX() {
        return closeX() - 8 - 120;
    }

    // ================================================================== rendering

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);

        // slide animation
        float goal = drawerOpen ? 1f : 0f;
        slide += (goal - slide) * 0.3f;
        if (Math.abs(goal - slide) < 0.01f) slide = goal;

        if (armed != null && System.currentTimeMillis() - armedAt > 5000) {
            armed = null;
            markDirty();
        }
        if (page == Page.PLAYERS) {
            int sig = MenuClientState.onlineSignature();
            if (sig != seenOnline) {
                seenOnline = sig;
                markDirty();
            }
        }
        if (dirty || seenVersion != MenuClientState.version) rebuild();

        if (page != Page.NONE) renderWindow(g, mx, my, partial);
        renderDrawer(g, mx, my);
    }

    private void renderWindow(GuiGraphics g, int mx, int my, float partial) {
        Theme.roundBorder(g, wx, wy, ww, wh, Theme.BG, Theme.BORDER);

        // title (+ scaled bold like the admin panel) and subtitle / latest notice
        String title;
        String sub;
        MenuNet.MyProfile me = MenuClientState.profile;
        switch (page) {
            case CHARACTER -> {
                title = me.created() ? (me.firstName() + " " + me.surname()).toUpperCase() : "CHARACTER STATUS";
                sub = "Your identity on this server.";
            }
            case PLAYERS -> {
                title = "PLAYERS";
                sub = MenuClientState.online("").size() + " players online";
            }
            default -> {
                title = "REDEEM CODE";
                sub = "Enter a promo code to earn ReRolls.";
            }
        }
        int titleMax = (int) ((ww - 28 - (page == Page.PLAYERS ? 140 : 110)) / 1.4f);
        g.pose().pushPose();
        g.pose().translate(wx + 14, wy + 9, 0);
        g.pose().scale(1.4f, 1.4f, 1f);
        g.drawString(font, Component.literal(Theme.fit(title, titleMax)).withStyle(ChatFormatting.BOLD), 0, 0, Theme.BEIGE, false);
        g.pose().popPose();

        boolean showNotice = System.currentTimeMillis() < MenuClientState.noticeUntil && !MenuClientState.notice.isEmpty();
        Theme.text(g, Theme.fit(showNotice ? MenuClientState.notice : sub, ww - 28), wx + 14, wy + 26,
                showNotice ? Theme.GOLD : Theme.MUTED);

        // right side of the header: search (players) or ReRoll balance (others), then the close X
        if (page == Page.PLAYERS) {
            int sx = searchX();
            Theme.roundBorder(g, sx, wy + 8, 120, 16, Theme.INPUT, search.isFocused() ? Theme.GOLD : Theme.BORDER);
            search.setX(sx + 6);
            search.setY(wy + 8 + 4);
            search.setWidth(108);
            search.render(g, mx, my, partial);
        } else {
            Theme.textRight(g, "Rerolls: " + me.rerolls(), closeX() - 8, wy + 12, Theme.TEXT);
        }
        boolean closeHover = Theme.in(mx, my, closeX() - 3, wy + 6, 16, 18);
        Theme.text(g, "X", closeX() + 2, wy + 12, closeHover ? Theme.GOLD : Theme.MUTED);

        g.fill(wx + 1, wy + HEADER_H, wx + ww - 1, wy + HEADER_H + 1, Theme.BORDER);
        pane.render(g, mx, my);
    }

    private void renderDrawer(GuiGraphics g, int mx, int my) {
        int dx = drawerX();
        if (slide > 0f) {
            g.fill(dx, 0, dx + DRAW_W, height, Theme.SIDE);
            g.fill(dx + DRAW_W - 1, 0, dx + DRAW_W, height, Theme.BORDER);

            g.pose().pushPose();
            g.pose().translate(dx + 14, 12, 0);
            g.pose().scale(1.4f, 1.4f, 1f);
            g.drawString(font, Component.literal("MENU").withStyle(ChatFormatting.BOLD), 0, 0, Theme.BEIGE, false);
            g.pose().popPose();
            boolean closeHover = Theme.in(mx, my, dx + DRAW_W - 25, 8, 16, 18);
            Theme.text(g, "X", dx + DRAW_W - 20, 14, closeHover ? Theme.GOLD : Theme.MUTED);
            g.fill(dx + 8, HEADER_H, dx + DRAW_W - 8, HEADER_H + 1, Theme.BORDER);

            int y = HEADER_H + 8;
            for (Section s : sections) {
                if (visibleItems(s).isEmpty()) continue;
                String t = s.title().toUpperCase();
                Theme.text(g, t, dx + 10, y + 3, Theme.MUTED);
                int lx = dx + 10 + font.width(t) + 6;
                if (lx < dx + DRAW_W - 10) g.fill(lx, y + 7, dx + DRAW_W - 10, y + 8, Theme.BORDER);
                y += 14;
                for (Item it : visibleItems(s)) {
                    int ix = dx + 8;
                    int iw = DRAW_W - 16;
                    boolean sel = it.selected().getAsBoolean();
                    boolean hover = Theme.in(mx, my, ix, y, iw, ITEM_H);
                    if (sel) {
                        Theme.round(g, ix, y, iw, ITEM_H, Theme.PANEL2);
                        g.fill(ix, y + 3, ix + 2, y + ITEM_H - 3, Theme.GOLD);
                    } else if (hover) {
                        Theme.round(g, ix, y, iw, ITEM_H, Theme.PANEL);
                    }
                    String label = it.label();
                    if (it.label().equals("Admin Panel") && adminPanelWasOpen) label = "Close Admin Panel";
                    String badge = it.badge().get();
                    int labelW = iw - 16 - (badge.isEmpty() ? 0 : font.width(badge) + 8);
                    Theme.text(g, Theme.fit(label, labelW), ix + 10, y + 6, sel ? Theme.BEIGE : (hover ? Theme.TEXT : Theme.MUTED));
                    if (!badge.isEmpty()) {
                        int badgeColor = badge.equals("ON") ? Theme.GOLD : Theme.DISABLED;
                        Theme.textRight(g, badge, ix + iw - 6, y + 6, badgeColor);
                    }
                    y += ITEM_H + 4;
                }
                y += 6;
            }
        }
        // the toggle tab always hugs the drawer's right edge ("<" open, ">" collapsed)
        int tx = dx + DRAW_W;
        MenuOverlay.drawTab(g, tx, tabY(), drawerOpen, Theme.in(mx, my, tx, tabY(), MenuOverlay.TAB_W, MenuOverlay.TAB_H));
    }

    private List<Item> visibleItems(Section s) {
        List<Item> out = new ArrayList<>();
        for (Item it : s.items()) if (it.visible().getAsBoolean()) out.add(it);
        return out;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ================================================================== page content

    private void rebuild() {
        pane.setRows(switch (page) {
            case CHARACTER -> buildCharacter();
            case PLAYERS -> buildPlayers();
            case CODES -> buildCodes();
            default -> List.of();
        });
        seenVersion = MenuClientState.version;
        dirty = false;
    }

    private boolean nameValid() {
        return CharacterRules.cleanName(nameBox.getValue()) != null;
    }

    private boolean nameChanged() {
        String n = CharacterRules.cleanName(nameBox.getValue());
        return n != null && !n.equalsIgnoreCase(MenuClientState.profile.firstName());
    }

    private List<Row> buildCharacter() {
        List<Row> r = new ArrayList<>();
        MenuNet.MyProfile me = MenuClientState.profile;
        if (!MenuClientState.profileLoaded) {
            r.add(Rows.note("Loading your character..."));
            return r;
        }

        // ------------------------------------------------------------ first time: free creation form
        if (!me.created()) {
            r.add(Rows.heading("Create your character", "You type your name once. Choose carefully."));
            r.add(Rows.note("Pick a first name, a birth year and a race. Your surname is rolled for you from that race's "
                    + "name list. Your BIRTH YEAR IS PERMANENT. Your first name, surname and race can be changed "
                    + "later, as often as you like, for ReRolls."));
            r.add(Rows.input("First name", "Letters only, " + CharacterRules.NAME_MIN + "-" + CharacterRules.NAME_MAX
                    + " characters.", nameBox));
            r.add(MenuRows.yearPicker("Birth year", "Between " + CharacterRules.BIRTH_YEAR_MIN + " and "
                            + CharacterRules.BIRTH_YEAR_MAX + ".", CharacterRules.BIRTH_YEAR_MIN, CharacterRules.BIRTH_YEAR_MAX,
                    () -> draftYear, v -> draftYear = v, y -> "Age " + CharacterRules.age(y)));
            r.add(Rows.section("Race"));
            r.add(Rows.buttons(List.of(
                    Btn.of(Race.EUROPEAN.label, () -> draftRace = Race.EUROPEAN).selected(() -> draftRace == Race.EUROPEAN),
                    Btn.of(Race.JAPANESE.label, () -> draftRace = Race.JAPANESE).selected(() -> draftRace == Race.JAPANESE)), true));
            r.add(Rows.buttons(List.of(
                    Btn.of("Confirm character", () -> MenuClientNet.character(CharOp.CREATE, nameBox.getValue(),
                            draftYear, draftRace.ordinal())).when(() -> nameValid() && draftRace != null)), true));
            return r;
        }

        // ------------------------------------------------------------ existing character: locked fields
        Race race = Race.byOrdinal(me.race());
        r.add(Rows.section("Identity"));
        r.add(MenuRows.fields(List.of(new Cell("First name", me.firstName(), true), new Cell("Last name", me.surname(), true))));
        r.add(MenuRows.fields(List.of(
                new Cell("Age", String.valueOf(CharacterRules.age(me.birthYear())), true),
                new Cell("Birth year", String.valueOf(me.birthYear()), true),
                new Cell("Ethnicity", race == null ? "-" : race.label, true))));

        r.add(Rows.section("Change identity"));
        r.add(Rows.note("Your birth year is permanent. Name, surname and race can be changed as often as you like - each change costs ReRolls "
                + "(you have " + me.rerolls() + "). Get more with promo codes. Click a button twice to confirm."));

        r.add(Rows.input("New first name", "Unlimited changes, " + CharacterRules.COST_NAME_CHANGE + " ReRoll each.", nameBox));
        r.add(Rows.buttons(List.of(paid(CharOp.CHANGE_NAME, "Change name", CharacterRules.COST_NAME_CHANGE, this::nameChanged,
                () -> MenuClientNet.character(CharOp.CHANGE_NAME, nameBox.getValue(), 0, 0))), true));

        r.add(Rows.section("Race and surname"));
        Race other = race == Race.JAPANESE ? Race.EUROPEAN : Race.JAPANESE;
        r.add(Rows.buttons(List.of(
                paid(CharOp.REROLL_SURNAME, "Reroll surname", CharacterRules.COST_SURNAME_REROLL, () -> true,
                        () -> MenuClientNet.character(CharOp.REROLL_SURNAME, "", 0, 0)),
                paid(CharOp.CHANGE_RACE, "Become " + other.label, CharacterRules.COST_RACE_CHANGE, () -> true,
                        () -> MenuClientNet.character(CharOp.CHANGE_RACE, "", 0, other.ordinal()))), true));
        r.add(Rows.note("A new surname is always drawn from your race's own list only."));
        return r;
    }

    /** A button that costs ReRolls: first click arms it ("Confirm"), second click within 5 s sends it. */
    private Btn paid(CharOp op, String label, int cost, BooleanSupplier valid, Runnable send) {
        boolean isArmed = armed == op;
        String text = (isArmed ? "Confirm" : label) + " (" + cost + ")";
        return Btn.of(text, () -> {
            if (cost <= 0 || (armed == op && System.currentTimeMillis() - armedAt <= 5000)) {
                armed = null;
                send.run();
            } else {
                armed = op;
                armedAt = System.currentTimeMillis();
            }
            markDirty();
        }).when(() -> valid.getAsBoolean() && MenuClientState.profile.rerolls() >= cost).selected(() -> armed == op);
    }

    private List<Row> buildPlayers() {
        List<Row> r = new ArrayList<>();
        List<MenuClientState.Online> list = MenuClientState.online(query);
        r.add(Rows.section("Online now (" + list.size() + ")"));
        for (MenuClientState.Online p : list) r.add(MenuRows.playerCard(p));
        if (list.isEmpty()) r.add(Rows.note("Nobody matches that search."));
        return r;
    }

    private List<Row> buildCodes() {
        List<Row> r = new ArrayList<>();
        r.add(Rows.heading("Promo codes", "Codes give you ReRolls."));
        r.add(Rows.input("Code", "Letters and numbers. Every code works once per player.", codeBox));
        r.add(Rows.buttons(List.of(Btn.of("Redeem", this::redeem).when(() -> !codeBox.getValue().isBlank())), true));
        r.add(Rows.note("ReRolls pay for changing your name, birth year, race or surname. You have "
                + MenuClientState.profile.rerolls() + "."));
        return r;
    }

    private void redeem() {
        String code = codeBox.getValue().trim();
        if (code.isEmpty()) return;
        MenuClientNet.code(code);
        codeBox.setValue("");
    }

    // ================================================================== input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int dx = drawerX();
            // the toggle tab
            if (Theme.in(mx, my, dx + DRAW_W, tabY(), MenuOverlay.TAB_W, MenuOverlay.TAB_H)) {
                drawerOpen = !drawerOpen;
                return true;
            }
            if (slide > 0.5f && Theme.in(mx, my, 0, 0, dx + DRAW_W, height)) {
                if (Theme.in(mx, my, dx + DRAW_W - 25, 8, 16, 18)) {
                    onClose();
                    return true;
                }
                int y = HEADER_H + 8;
                for (Section s : sections) {
                    List<Item> items = visibleItems(s);
                    if (items.isEmpty()) continue;
                    y += 14;
                    for (Item it : items) {
                        if (Theme.in(mx, my, dx + 8, y, DRAW_W - 16, ITEM_H)) {
                            it.action().run();
                            return true;
                        }
                        y += ITEM_H + 4;
                    }
                    y += 6;
                }
                return true; // clicks on the empty drawer do nothing
            }
            if (page != Page.NONE) {
                if (Theme.in(mx, my, closeX() - 3, wy + 6, 16, 18)) {
                    page = Page.NONE;
                    markDirty();
                    return true;
                }
                if (page == Page.PLAYERS && Theme.in(mx, my, searchX(), wy + 8, 120, 16)) {
                    pane.blur();
                    search.setFocused(true);
                    int cx = (int) Math.max(search.getX(), Math.min(search.getX() + search.getWidth() - 1, mx));
                    search.mouseClicked(cx, search.getY() + 2, button);
                    return true;
                }
            }
        }
        if (search != null) search.setFocused(false);
        if (page != Page.NONE && pane.click(mx, my, button)) return true;
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
        if (page != Page.NONE && pane.scroll(mx, my, delta)) return true;
        return super.mouseScrolled(mx, my, delta);
    }

    private boolean typing() {
        return search.isFocused() || nameBox.isFocused() || codeBox.isFocused();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (page == Page.CODES && codeBox.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            redeem();
            return true;
        }
        if (search.isFocused() && search.keyPressed(key, scan, mods)) return true;
        if (pane.key(key, scan, mods)) return true;
        // pressing the menu key again closes the menu (unless you are typing in a field)
        if (!typing() && MenuClient.MENU_KEY != null && MenuClient.MENU_KEY.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (search.isFocused() && search.charTyped(c, mods)) return true;
        if (pane.chr(c, mods)) return true;
        return super.charTyped(c, mods);
    }
}
