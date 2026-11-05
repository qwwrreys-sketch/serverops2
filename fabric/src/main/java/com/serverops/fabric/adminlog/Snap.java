package com.serverops.fabric.adminlog;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.serverops.common.OpsData;
import com.serverops.common.RoleDef;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * A frozen copy of everything the notifier compares: taken just BEFORE and just AFTER the admin panel
 * handles a request. Whatever differs between the two is what really happened (a refused action changes
 * nothing, so it is never announced).
 */
final class Snap {
    UUID target;
    String targetName = "";
    boolean targetOnline;
    OpsBridge.Acc actor;
    OpsBridge.Acc targetAcc;

    boolean god;
    boolean invis;
    boolean fly;
    boolean esp;
    boolean health;
    boolean frozen;

    String roleId = "";
    boolean panelGrant;
    Set<String> banKeys = new HashSet<>();
    Map<String, String> banNames = new HashMap<>();
    String rolesSig = "";
    Map<String, String> roleNames = new HashMap<>();

    String season = "";
    boolean rain;
    boolean thunder;
    long dayTime;

    static Snap take(MinecraftServer s, ServerPlayer actorPlayer, UUID target) throws ReflectiveOperationException {
        Snap n = new Snap();
        n.target = target;
        n.actor = OpsBridge.access(actorPlayer);

        ServerPlayer t = target == null ? null : s.getPlayerList().getPlayer(target);
        if (t != null) {
            n.targetOnline = true;
            n.targetName = t.getGameProfile().getName();
            n.targetAcc = OpsBridge.access(t);
        }
        if (target != null) {
            n.god = OpsBridge.set("GOD").contains(target);
            n.invis = OpsBridge.set("INVIS").contains(target);
            n.fly = OpsBridge.set("FLY").contains(target);
            n.esp = OpsBridge.set("ESP").contains(target);
            n.health = OpsBridge.set("HEALTH_BARS").contains(target);
            n.frozen = OpsBridge.frozen().containsKey(target);
        }

        OpsData d = OpsBridge.data();
        String key = target == null ? "" : target.toString();
        n.roleId = d.playerRoles.getOrDefault(key, "");
        n.panelGrant = d.panelAccess.contains(key);
        n.banNames = new HashMap<>(d.banNames);
        n.season = d.season;
        StringBuilder sig = new StringBuilder();
        for (RoleDef r : d.roles) {
            sig.append(r.id).append(':').append(r.name).append(':').append(r.color).append(':').append(r.perms).append(';');
            n.roleNames.put(r.id, r.name);
        }
        n.rolesSig = sig.toString();

        n.banKeys = new HashSet<>(Arrays.asList(s.getPlayerList().getBans().getUserList()));

        var level = s.overworld();
        n.rain = level.getLevelData().isRaining();
        n.thunder = level.getLevelData().isThundering();
        n.dayTime = level.getDayTime();
        return n;
    }
}
