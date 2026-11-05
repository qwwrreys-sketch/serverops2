package com.serverops.fabric.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.serverops.common.AccessRules;
import com.serverops.common.Perm;
import com.serverops.common.Season;
import com.serverops.fabric.net.Net;

import net.minecraft.client.Minecraft;

/** Everything the client knows. Only touched from the client thread. */
public final class ClientState {
    private ClientState() {}

    /** Latest panel snapshot (only meaningful when {@code sync.allowed()}). */
    public static Net.Sync sync = Net.Sync.denied();
    /** Incremented on every snapshot so the screen knows to rebuild. */
    public static int version;

    /** Role tags of every player on the server (rendered above their nametag). */
    private static final Map<UUID, Net.Tag> TAGS = new HashMap<>();
    public static boolean esp;
    public static boolean healthBars;
    public static Season season = Season.SUMMER;

    public static String notice = "";
    public static long noticeUntil;

    public static void applySync(Net.Sync s) {
        sync = s;
        version++;
        if (!s.notice().isEmpty()) {
            notice = s.notice();
            noticeUntil = System.currentTimeMillis() + 4500;
        }
    }

    public static void applyVisuals(Net.Visuals v) {
        TAGS.clear();
        for (Net.Tag t : v.tags()) TAGS.put(t.id(), t);
        esp = v.esp();
        healthBars = v.healthBars();
        Season next = Season.byOrdinal(v.season());
        if (next != season) {
            season = next;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) mc.levelRenderer.allChanged(); // re-colour grass & leaves
        }
    }

    public static void reset() {
        sync = Net.Sync.denied();
        version++;
        TAGS.clear();
        esp = false;
        healthBars = false;
        notice = "";
        if (season != Season.SUMMER) {
            season = Season.SUMMER;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) mc.levelRenderer.allChanged();
        }
    }

    public static Net.Tag tag(UUID id) {
        return TAGS.get(id);
    }

    // ---------------------------------------------------------------- helpers for the GUI

    public static boolean allowed() {
        return sync.allowed();
    }

    public static boolean has(Perm p) {
        return sync.allowed() && Perm.has(sync.perms(), p);
    }

    public static boolean master() {
        return sync.master();
    }

    public static List<Net.RoleInfo> roles() {
        return sync.roles();
    }

    public static Net.PlayerEntry player(UUID id) {
        for (Net.PlayerEntry p : sync.players()) if (p.id().equals(id)) return p;
        return null;
    }

    public static Net.RoleInfo role(String id) {
        for (Net.RoleInfo r : sync.roles()) if (r.id().equals(id)) return r;
        return null;
    }

    public static int roleIndex(String id) {
        List<Net.RoleInfo> rs = sync.roles();
        for (int i = 0; i < rs.size(); i++) if (rs.get(i).id().equals(id)) return i;
        return AccessRules.UNRANKED;
    }

    /** Can the local staff member hand out / take away this role? (server re-checks everything) */
    public static boolean canGrant(String roleId) {
        if (!has(Perm.ASSIGN_ROLES)) return false;
        if (roleId.isEmpty()) return true;
        return AccessRules.canGrantRole(sync.master(), sync.rank(), roleIndex(roleId));
    }

    public static boolean canEditRoleAt(int index) {
        return has(Perm.MANAGE_ROLES) && AccessRules.canEditRole(sync.master(), sync.rank(), index);
    }
}
