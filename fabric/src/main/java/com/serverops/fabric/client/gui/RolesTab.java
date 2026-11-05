package com.serverops.fabric.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.serverops.common.Perm;
import com.serverops.fabric.client.ClientNet;
import com.serverops.fabric.client.ClientState;
import com.serverops.fabric.client.gui.Rows.Btn;
import com.serverops.fabric.net.Net;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

/**
 * Role hierarchy. The list order IS the hierarchy (top = most senior). Rows can be dragged by their grip,
 * or moved with the arrow buttons. Clicking a role opens its editor (name, colour, permissions).
 */
public final class RolesTab extends Tab {
    private static final int[] SWATCHES = {
            0xF2C14E, 0xE8A33D, 0xE0707A, 0xD6609E, 0xA879E0, 0x5AA9E6,
            0x4FC3C7, 0x7BC47F, 0xB5D65A, 0xEADFC4, 0xA0A0A8, 0xFFFFFF};

    /** null = list view, "" = creating a new role, otherwise the id of the role being edited. */
    private String editId;
    private long editPerms;
    private final EditBox nameBox = Theme.box("Role name", 24);
    private final EditBox colorBox = Theme.box("#RRGGBB", 7);

    private List<String> order = new ArrayList<>();
    private List<String> orderBeforeDrag = new ArrayList<>();
    private boolean dragging;
    private String dragId = "";
    private final Map<String, RoleRow> rowCache = new HashMap<>();

    public RolesTab(OpsScreen scr) {
        super(scr);
    }

    @Override
    public String title() {
        return "Roles";
    }

    @Override
    public void onEnter() {
        editId = null;
        dragging = false;
    }

    @Override
    public List<Row> build() {
        return editId == null ? list() : editor();
    }

    // ------------------------------------------------------------ hierarchy list

    private List<Row> list() {
        if (!dragging) {
            order = new ArrayList<>();
            for (Net.RoleInfo r : ClientState.roles()) order.add(r.id());
        }
        rowCache.keySet().retainAll(order);

        List<Row> rows = new ArrayList<>();
        rows.add(Rows.heading("Roles", "Create roles and decide who outranks whom."));
        rows.add(Rows.note("Higher roles are senior. Drag a role by its grip (or use the arrows) to change its rank. "
                + "Staff can never freeze, ban, kick or edit a player whose role is higher than theirs. "
                + "Players with no role have no panel access (unless they are an operator or were given access)."));
        rows.add(Rows.section("Hierarchy - most senior first"));
        for (int i = 0; i < order.size(); i++) {
            RoleRow rr = rowCache.computeIfAbsent(order.get(i), RoleRow::new);
            rr.index = i;
            rows.add(rr);
        }
        rows.add(Rows.buttons(List.of(Btn.of("+ NEW ROLE", () -> startEdit("")).when(() -> ClientState.has(Perm.MANAGE_ROLES))), false));
        return rows;
    }

    private void startEdit(String id) {
        editId = id;
        if (id.isEmpty()) {
            nameBox.setValue("");
            colorBox.setValue("#EADFC4");
            editPerms = 0;
        } else {
            Net.RoleInfo r = ClientState.role(id);
            if (r == null) {
                editId = null;
                return;
            }
            nameBox.setValue(r.name());
            colorBox.setValue(String.format("#%06X", r.color() & 0xFFFFFF));
            editPerms = r.perms();
        }
        scr.resetScroll();
        scr.markDirty();
    }

    private void stopEdit() {
        editId = null;
        scr.resetScroll();
        scr.markDirty();
    }

    private void move(int from, int to, boolean sendNow) {
        if (from == to || from < 0 || to < 0 || from >= order.size() || to >= order.size()) return;
        if (!ClientState.canEditRoleAt(from) || !ClientState.canEditRoleAt(to)) return;
        order.add(to, order.remove(from));
        if (sendNow) sendOrder();
        scr.markDirty();
    }

    private void sendOrder() {
        ClientNet.role(Net.RoleOp.REORDER, "", "", 0, 0, new ArrayList<>(order));
    }

    // ------------------------------------------------------------ one hierarchy row

    private final class RoleRow implements Row {
        private static final int H = 26;
        final String id;
        int index;
        int lastY = Integer.MIN_VALUE;

        RoleRow(String id) {
            this.id = id;
        }

        @Override public int height(int w) { return H; }

        private boolean editable() {
            return ClientState.canEditRoleAt(index);
        }

        @Override
        public void render(GuiGraphics g, int x, int y, int w, int mx, int my) {
            lastY = y;
            Net.RoleInfo r = ClientState.role(id);
            if (r == null) return;
            boolean hover = Theme.in(mx, my, x, y, w, H);
            boolean beingDragged = dragging && id.equals(dragId);
            Theme.roundBorder(g, x, y, w, H, hover || beingDragged ? Theme.HOVER : Theme.PANEL2,
                    beingDragged ? Theme.GOLD : Theme.BORDER);

            boolean ed = editable();
            // grip
            int gc = ed ? Theme.MUTED : Theme.DISABLED;
            for (int i = 0; i < 3; i++) g.fill(x + 7, y + 8 + i * 4, x + 15, y + 9 + i * 4, gc);
            // colour + rank + name
            g.fill(x + 22, y + 8, x + 32, y + 18, 0xFF000000 | r.color());
            Theme.text(g, "#" + (index + 1), x + 37, y + 9, Theme.DISABLED);
            int nameX = x + 37 + Theme.font().width("#" + (index + 1)) + 5;
            Theme.text(g, Theme.fit(r.name(), w - (nameX - x) - 100), nameX, y + 9, 0xFF000000 | r.color());

            // arrows + edit
            Theme.button(g, x + w - 98, y + 4, 16, 18, "^", ed && index > 0, false, false,
                    ed && Theme.in(mx, my, x + w - 98, y + 4, 16, 18), -1);
            Theme.button(g, x + w - 80, y + 4, 16, 18, "v", ed && index < order.size() - 1, false, false,
                    ed && Theme.in(mx, my, x + w - 80, y + 4, 16, 18), -1);
            Theme.button(g, x + w - 60, y + 4, 54, 18, ed ? "EDIT" : "VIEW", true, false, false,
                    Theme.in(mx, my, x + w - 60, y + 4, 54, 18), -1);
        }

        @Override
        public boolean click(int x, int y, int w, double mx, double my, int button) {
            if (button != 0) return false;
            if (Theme.in(mx, my, x + w - 98, y + 4, 16, 18)) {
                if (editable() && index > 0) move(index, index - 1, true);
                return true;
            }
            if (Theme.in(mx, my, x + w - 80, y + 4, 16, 18)) {
                if (editable() && index < order.size() - 1) move(index, index + 1, true);
                return true;
            }
            if (Theme.in(mx, my, x, y, 20, H)) { // grip -> start dragging
                if (editable()) {
                    dragging = true;
                    dragId = id;
                    orderBeforeDrag = new ArrayList<>(order);
                }
                return true;
            }
            startEdit(id); // anywhere else (name, EDIT button)
            return true;
        }

        @Override
        public boolean drag(int x, int y, int w, double mx, double my) {
            if (!dragging || !id.equals(dragId)) return false;
            int cur = order.indexOf(id);
            for (int i = 0; i < order.size(); i++) {
                RoleRow other = rowCache.get(order.get(i));
                if (other == null || other.lastY == Integer.MIN_VALUE) continue;
                if (my >= other.lastY && my < other.lastY + H + 4) {
                    if (i != cur) move(cur, i, false);
                    break;
                }
            }
            return true;
        }

        @Override
        public void release() {
            if (!dragging) return;
            dragging = false;
            if (!order.equals(orderBeforeDrag)) sendOrder();
            scr.markDirty();
        }
    }

    // ------------------------------------------------------------ role editor

    private List<Row> editor() {
        boolean isNew = editId.isEmpty();
        Net.RoleInfo role = isNew ? null : ClientState.role(editId);
        if (!isNew && role == null) { // deleted by someone else
            editId = null;
            return list();
        }
        int idx = isNew ? ClientState.roles().size() : ClientState.roleIndex(editId);
        boolean canEdit = isNew ? ClientState.has(Perm.MANAGE_ROLES) : ClientState.canEditRoleAt(idx);

        List<Row> rows = new ArrayList<>();
        rows.add(Rows.link("< ALL ROLES", this::stopEdit));
        rows.add(Rows.heading(isNew ? "New role" : role.name(),
                isNew ? "New roles start at the bottom of the hierarchy." : "Position #" + (idx + 1) + " in the hierarchy."));
        if (!canEdit) rows.add(Rows.note("This role is not below your own, so you can only look at it."));

        rows.add(Rows.input("Name", "Shown above the player's name as [Name].", nameBox));
        rows.add(Rows.input("Colour", "Hex colour of the name tag, for example #F2C14E.", colorBox));
        rows.add(swatches(canEdit));

        rows.add(Rows.section("Permissions"));
        for (Perm p : Perm.values()) {
            if (!scr.query.isEmpty() && !p.label.toLowerCase().contains(scr.query)) continue;
            rows.add(Rows.toggle(p.label, p.description,
                    () -> (editPerms & p.bit()) != 0,
                    v -> editPerms = v ? (editPerms | p.bit()) : (editPerms & ~p.bit()),
                    () -> canEdit && (ClientState.master() || ClientState.has(p))));
        }

        List<Btn> actions = new ArrayList<>();
        actions.add(Btn.of(isNew ? "CREATE ROLE" : "SAVE", this::save).when(() -> canEdit));
        if (!isNew) {
            actions.add(Btn.of("DELETE", () -> {
                ClientNet.role(Net.RoleOp.DELETE, editId, "", 0, 0, List.of());
                stopEdit();
            }).danger().when(() -> canEdit));
        }
        rows.add(Rows.space(4));
        rows.add(Rows.buttons(actions, false));
        rows.add(Rows.space(6));
        return rows;
    }

    private void save() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) return;
        boolean isNew = editId.isEmpty();
        ClientNet.role(isNew ? Net.RoleOp.CREATE : Net.RoleOp.UPDATE, editId, name, parseColor(colorBox.getValue()),
                editPerms, List.of());
        stopEdit();
    }

    private static int parseColor(String s) {
        try {
            String h = s.trim();
            if (h.startsWith("#")) h = h.substring(1);
            return Integer.parseInt(h, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0xEADFC4;
        }
    }

    private Row swatches(boolean canEdit) {
        return new Row() {
            private static final int S = 14;
            private static final int G = 4;

            @Override public int height(int w) { return S + 2; }

            @Override
            public void render(GuiGraphics g, int x, int y, int w, int mx, int my) {
                int current = parseColor(colorBox.getValue());
                for (int i = 0; i < SWATCHES.length; i++) {
                    int sx = x + i * (S + G);
                    if (sx + S > x + w) break;
                    boolean sel = SWATCHES[i] == current;
                    boolean hover = canEdit && Theme.in(mx, my, sx, y, S, S);
                    Theme.round(g, sx - 1, y - 1, S + 2, S + 2, sel ? Theme.BEIGE : (hover ? Theme.GOLD : Theme.BORDER));
                    g.fill(sx, y, sx + S, y + S, 0xFF000000 | SWATCHES[i]);
                }
            }

            @Override
            public boolean click(int x, int y, int w, double mx, double my, int button) {
                if (!canEdit || button != 0) return false;
                for (int i = 0; i < SWATCHES.length; i++) {
                    if (Theme.in(mx, my, x + i * (S + G), y, S, S)) {
                        colorBox.setValue(String.format("#%06X", SWATCHES[i]));
                        return true;
                    }
                }
                return false;
            }
        };
    }
}
