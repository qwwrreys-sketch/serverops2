package com.serverops.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything persisted per world. Plain fields so Gson can (de)serialise it directly. */
public class OpsData {
    /** Hierarchy: index 0 is the most senior role. */
    public List<RoleDef> roles = new ArrayList<>();
    /** player uuid -> role id */
    public Map<String, String> playerRoles = new HashMap<>();
    /** uuids that were manually granted the panel by a master */
    public Set<String> panelAccess = new HashSet<>();
    /** uuid -> staff-only ban note */
    public Map<String, String> privateNotes = new HashMap<>();
    /** uuid -> last known name of players banned through the panel */
    public Map<String, String> banNames = new HashMap<>();
    public String season = Season.SUMMER.name();

    public static OpsData defaults() {
        OpsData d = new OpsData();
        d.roles.add(new RoleDef("principal", "Principal", 0xF2C14E, Perm.all()));
        d.roles.add(new RoleDef("teacher", "Teacher", 0x5AA9E6,
                Perm.PANEL_ACCESS.bit() | Perm.HEAL.bit() | Perm.TELEPORT.bit() | Perm.RESPAWN.bit()
                        | Perm.KICK.bit() | Perm.FREEZE.bit() | Perm.SIGHT.bit()));
        d.roles.add(new RoleDef("student", "Student", 0x7BC47F, 0L));
        return d;
    }

    /** Repair anything missing after loading an older / hand-edited file. */
    public OpsData sanitize() {
        if (roles == null) roles = new ArrayList<>();
        if (playerRoles == null) playerRoles = new HashMap<>();
        if (panelAccess == null) panelAccess = new HashSet<>();
        if (privateNotes == null) privateNotes = new HashMap<>();
        if (banNames == null) banNames = new HashMap<>();
        if (season == null) season = Season.SUMMER.name();
        roles.removeIf(r -> r == null || r.id == null || r.name == null);
        playerRoles.values().removeIf(id -> role(id) == null);
        return this;
    }

    public RoleDef role(String id) {
        if (id == null) return null;
        for (RoleDef r : roles) if (r.id.equals(id)) return r;
        return null;
    }

    public int rankOfRole(String id) {
        for (int i = 0; i < roles.size(); i++) if (roles.get(i).id.equals(id)) return i;
        return AccessRules.UNRANKED;
    }

    public RoleDef roleOf(String playerUuid) {
        return role(playerRoles.get(playerUuid));
    }

    public String uniqueRoleId(String name) {
        String base = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.isEmpty()) base = "role";
        String id = base;
        int n = 2;
        while (role(id) != null) id = base + "-" + n++;
        return id;
    }
}
