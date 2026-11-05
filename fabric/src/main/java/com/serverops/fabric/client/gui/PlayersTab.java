package com.serverops.fabric.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import com.serverops.common.BanDurations;
import com.serverops.common.Perm;
import com.serverops.fabric.client.ClientNet;
import com.serverops.fabric.client.ClientState;
import com.serverops.fabric.client.gui.Rows.Btn;
import com.serverops.fabric.net.Net;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;

/** Active player list; selecting a player opens their profile (actions, role, state, sight, bans). */
public final class PlayersTab extends Tab {
    private UUID selected;
    private int banStep;
    private final EditBox publicReason = Theme.box("Why they are being banned", 200);
    private final EditBox privateReason = Theme.box("Note for other staff", 200);
    private final EditBox unbanBox = Theme.box("Username or user ID", 64);

    public PlayersTab(OpsScreen scr) {
        super(scr);
    }

    @Override
    public String title() {
        return "Players";
    }

    @Override
    public void onEnter() {
        selected = null;
    }

    private void select(UUID id) {
        selected = id;
        banStep = 0;
        publicReason.setValue("");
        privateReason.setValue("");
        scr.resetScroll();
        scr.markDirty();
    }

    private void back() {
        selected = null;
        scr.resetScroll();
        scr.markDirty();
    }

    private static UUID localId() {
        var p = Minecraft.getInstance().player;
        return p == null ? new UUID(0, 0) : p.getUUID();
    }

    @Override
    public List<Row> build() {
        if (selected != null && ClientState.player(selected) == null) selected = null; // they left
        return selected == null ? list() : profile(ClientState.player(selected));
    }

    // ------------------------------------------------------------ list view

    private List<Row> list() {
        List<Row> r = new ArrayList<>();
        r.add(Rows.heading("Players", "Find a player and act on them."));

        List<Net.PlayerEntry> shown = new ArrayList<>();
        for (Net.PlayerEntry p : ClientState.sync.players()) {
            if (scr.query.isEmpty() || p.name().toLowerCase().contains(scr.query)) shown.add(p);
        }
        r.add(Rows.section("Active players (" + shown.size() + ")"));
        if (shown.isEmpty()) {
            r.add(Rows.note(scr.query.isEmpty() ? "Nobody is online." : "No player matches \"" + scr.query + "\"."));
        }
        for (Net.PlayerEntry p : shown) r.add(Rows.playerCard(p, () -> select(p.id())));

        r.add(Rows.section("Server bans"));
        boolean canBan = ClientState.has(Perm.BAN);
        if (ClientState.sync.bans().isEmpty()) {
            r.add(Rows.note("Nobody is banned from this server. Bans you make show up here."));
        }
        for (Net.BanEntry b : ClientState.sync.bans()) {
            r.add(Rows.banEntry(b, canBan, () -> ClientNet.unban(b.uuid())));
        }
        return r;
    }

    // ------------------------------------------------------------ profile view

    private boolean flag(UUID id, Function<Net.PlayerEntry, Boolean> f) {
        Net.PlayerEntry e = ClientState.player(id);
        return e != null && f.apply(e);
    }

    private Rows.Btn quick(String label, Perm perm, Net.Act act, Net.PlayerEntry p, boolean needsTouch, boolean notSelf) {
        UUID id = p.id();
        boolean self = id.equals(localId());
        return Btn.of(label, () -> ClientNet.action(act, id)).when(() ->
                ClientState.has(perm) && (!needsTouch || p.canAct()) && !(notSelf && self));
    }

    /** Staff must outrank both the role being taken away and the role being handed out. */
    private static boolean canSwap(Net.PlayerEntry p, String newRoleId) {
        return p.canAct() && ClientState.canGrant(newRoleId)
                && (p.roleId().isEmpty() || ClientState.canGrant(p.roleId()));
    }

    private Row stateToggle(Net.PlayerEntry p, String title, String desc, Perm perm, Net.Act act,
                            Function<Net.PlayerEntry, Boolean> getter, boolean notSelf) {
        UUID id = p.id();
        boolean self = id.equals(localId());
        return Rows.toggle(title, desc, () -> flag(id, getter), v -> ClientNet.toggle(act, id, v),
                () -> ClientState.has(perm) && p.canAct() && !(notSelf && self));
    }

    private List<Row> profile(Net.PlayerEntry p) {
        UUID id = p.id();
        List<Row> r = new ArrayList<>();
        r.add(Rows.link("< ALL PLAYERS", this::back));
        r.add(Rows.profileHeader(p));

        // ---- quick actions
        r.add(Rows.section("Actions"));
        r.add(Rows.buttons(List.of(
                quick("BRING", Perm.TELEPORT, Net.Act.BRING, p, true, true),
                quick("GO TO", Perm.TELEPORT, Net.Act.GOTO, p, false, true),
                quick("RESPAWN", Perm.RESPAWN, Net.Act.RESPAWN, p, true, false),
                quick("HEAL", Perm.HEAL, Net.Act.HEAL, p, true, false),
                quick("KICK", Perm.KICK, Net.Act.KICK, p, true, true).danger()), true));

        // ---- role assignment (chips wrap, so they stay readable with many roles)
        r.add(Rows.section("Role"));
        r.add(Rows.label("Role", "Pick a role. Roles higher in the Roles tab are more senior."));
        List<Btn> chips = new ArrayList<>();
        chips.add(Btn.of("None", () -> ClientNet.setRole(id, ""))
                .selected(() -> flag(id, e -> e.roleId().isEmpty()))
                .when(() -> canSwap(p, "")));
        for (Net.RoleInfo role : ClientState.roles()) {
            chips.add(Btn.of(role.name(), () -> ClientNet.setRole(id, role.id()))
                    .dot(role.color())
                    .selected(() -> flag(id, e -> e.roleId().equals(role.id())))
                    .when(() -> canSwap(p, role.id())));
        }
        r.add(Rows.buttons(chips, false));

        r.add(Rows.toggle("Panel access",
                "Gives this one player the full commands panel, without touching their role.",
                () -> flag(id, Net.PlayerEntry::panel), v -> ClientNet.toggle(Net.Act.PANEL, id, v),
                ClientState::master));

        // ---- state
        r.add(Rows.section("State"));
        r.add(stateToggle(p, "Godmode", "Nothing can damage them.", Perm.GODMODE, Net.Act.GOD, Net.PlayerEntry::god, false));
        r.add(stateToggle(p, "Freeze", "Locks them where they stand.", Perm.FREEZE, Net.Act.FREEZE, Net.PlayerEntry::freeze, true));
        r.add(stateToggle(p, "Invisible", "Hides them from everyone else in the server.", Perm.INVISIBLE, Net.Act.INVIS, Net.PlayerEntry::invis, false));
        r.add(stateToggle(p, "Fly", "Lets them fly.", Perm.FLY, Net.Act.FLY, Net.PlayerEntry::fly, false));

        // ---- sight
        r.add(Rows.section("Sight"));
        r.add(stateToggle(p, "ESP", "Lets them see other players through walls.", Perm.SIGHT, Net.Act.ESP, Net.PlayerEntry::esp, false));
        r.add(stateToggle(p, "See other health bars", "Shows them everyone else's health, not just their own.",
                Perm.SIGHT, Net.Act.HEALTHBARS, Net.PlayerEntry::healthBars, false));

        // ---- ban
        boolean canBan = ClientState.has(Perm.BAN);
        boolean self = id.equals(localId());
        r.add(Rows.section("Ban"));
        r.add(Rows.slider("Duration", "How long the ban lasts. Drag it to zero for a permanent one.",
                BanDurations.steps(), () -> banStep, v -> banStep = v, BanDurations::label));
        r.add(Rows.input("Public reason", "Shown to the player on the ban screen.", publicReason));
        r.add(Rows.input("Private reason", "Staff only. The player never sees this.", privateReason));
        r.add(Rows.rightButton(Btn.of("BAN", () -> {
            ClientNet.ban(id, BanDurations.minutes(banStep), publicReason.getValue().trim(), privateReason.getValue().trim());
            back();
        }).danger().when(() -> canBan && p.canAct() && !self)));

        // ---- unban
        r.add(Rows.section("Unban"));
        r.add(Rows.input("Player", "Username or user ID. Lifts a game ban.", unbanBox));
        r.add(Rows.rightButton(Btn.of("UNBAN", () -> {
            ClientNet.unban(unbanBox.getValue().trim());
            unbanBox.setValue("");
        }).when(() -> canBan)));

        // ---- current bans
        r.add(Rows.section("Server bans"));
        r.add(Rows.rightButton(Btn.of("REFRESH", ClientNet::requestRefresh)));
        if (ClientState.sync.bans().isEmpty()) {
            r.add(Rows.note("Nobody is banned from this server. Bans you make show up here."));
        }
        for (Net.BanEntry b : ClientState.sync.bans()) {
            r.add(Rows.banEntry(b, canBan, () -> ClientNet.unban(b.uuid())));
        }
        r.add(Rows.space(6));
        return r;
    }
}
