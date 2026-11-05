package com.serverops.fabric.server;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.serverops.common.AccessRules;
import com.serverops.common.OpsData;
import com.serverops.common.Perm;
import com.serverops.common.RoleDef;
import com.serverops.common.Season;
import com.serverops.fabric.net.Net;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.phys.Vec3;

/**
 * All server-side logic: who may use the panel, the role hierarchy, every staff action and the state
 * (godmode, freeze, ...) that those actions toggle. Everything here runs on the server thread.
 */
public final class OpsService {
    private OpsService() {}

    private static final String NO_PERM = "You don't have permission to do that.";
    private static final String TOO_SENIOR = "That player is more senior than you.";
    private static final String TOO_SENIOR_ROLE = "That role is not below your own.";

    private static MinecraftServer server;
    private static OpsData data = OpsData.defaults();

    private static final Set<UUID> GOD = new HashSet<>();
    private static final Set<UUID> INVIS = new HashSet<>();
    private static final Set<UUID> FLY = new HashSet<>();
    private static final Set<UUID> ESP = new HashSet<>();
    private static final Set<UUID> HEALTH_BARS = new HashSet<>();
    private static final Map<UUID, Vec3> FROZEN = new HashMap<>();

    /** Evaluated fresh for every request, so role / OP changes take effect immediately. */
    private record Access(boolean master, boolean manual, boolean panel, int rank, long perms) {
        boolean has(Perm p) {
            return Perm.has(perms, p);
        }
    }

    // ================================================================== setup

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            data = OpsStore.load(s);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            if (server != null) save();
            GOD.clear();
            INVIS.clear();
            FLY.clear();
            ESP.clear();
            HEALTH_BARS.clear();
            FROZEN.clear();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> broadcastVisuals(null));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
            ServerPlayer p = handler.player;
            UUID id = p.getUUID();
            if (INVIS.remove(id)) p.removeEffect(MobEffects.INVISIBILITY);
            GOD.remove(id);
            FLY.remove(id);
            ESP.remove(id);
            HEALTH_BARS.remove(id);
            FROZEN.remove(id);
            broadcastVisuals(id);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            UUID id = newPlayer.getUUID();
            if (FLY.contains(id)) applyFly(newPlayer, true);
            if (INVIS.contains(id)) applyInvisible(newPlayer, true);
            if (FROZEN.containsKey(id)) FROZEN.put(id, newPlayer.position());
            broadcastVisuals(null);
        });

        // Godmode: block everything except "bypasses invulnerability" damage (void, /kill).
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer p && GOD.contains(p.getUUID())
                    && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return false;
            }
            return true;
        });

        // Freeze: snap frozen players back to where they were frozen.
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (FROZEN.isEmpty()) return;
            for (Map.Entry<UUID, Vec3> e : FROZEN.entrySet()) {
                ServerPlayer p = s.getPlayerList().getPlayer(e.getKey());
                if (p == null) continue;
                Vec3 at = e.getValue();
                if (p.position().distanceToSqr(at) > 0.0025) {
                    p.connection.teleport(at.x, at.y, at.z, p.getYRot(), p.getXRot());
                    p.setDeltaMovement(Vec3.ZERO);
                }
            }
        });

        // ---- network receivers (decode on the network thread, act on the server thread)
        ServerPlayNetworking.registerGlobalReceiver(Net.C2S_OPEN,
                (srv, player, handler, buf, sender) -> srv.execute(() -> handleOpen(player)));
        ServerPlayNetworking.registerGlobalReceiver(Net.C2S_REFRESH,
                (srv, player, handler, buf, sender) -> srv.execute(() -> handleRefresh(player)));
        ServerPlayNetworking.registerGlobalReceiver(Net.C2S_ACTION, (srv, player, handler, buf, sender) -> {
            Net.Action action = Net.Action.read(buf);
            srv.execute(() -> handleAction(player, action));
        });
        ServerPlayNetworking.registerGlobalReceiver(Net.C2S_ROLE, (srv, player, handler, buf, sender) -> {
            Net.RoleEdit edit = Net.RoleEdit.read(buf);
            srv.execute(() -> handleRole(player, edit));
        });
        ServerPlayNetworking.registerGlobalReceiver(Net.C2S_WORLD, (srv, player, handler, buf, sender) -> {
            int kind = buf.readVarInt();
            int value = buf.readVarInt();
            srv.execute(() -> handleWorld(player, kind, value));
        });
    }

    private static void save() {
        OpsStore.save(server, data);
    }

    // ================================================================== access

    private static Access access(ServerPlayer p) {
        String key = p.getUUID().toString();
        // "Master key": vanilla OP level 4 (/op) or the singleplayer host.
        boolean master = p.hasPermissions(4) || server.isSingleplayerOwner(p.getGameProfile());
        boolean manual = data.panelAccess.contains(key);
        RoleDef role = data.roleOf(key);
        boolean panel = master || manual || (role != null && role.has(Perm.PANEL_ACCESS));
        long perms = (master || manual) ? Perm.all() : (role != null ? role.perms : 0L);
        int rank = master ? AccessRules.MASTER : (role != null ? data.roles.indexOf(role) : AccessRules.UNRANKED);
        return new Access(master, manual, panel, rank, perms);
    }

    // ================================================================== requests

    private static void handleOpen(ServerPlayer p) {
        if (access(p).panel()) {
            sendSync(p, true, "");
        } else {
            p.sendSystemMessage(Component.literal("You don't have access to the Server Ops panel.")
                    .withStyle(ChatFormatting.RED));
        }
    }

    private static void handleRefresh(ServerPlayer p) {
        if (access(p).panel()) {
            sendSync(p, false, "");
        } else {
            send(p, Net.Sync.denied());
        }
    }

    private static void handleAction(ServerPlayer actor, Net.Action in) {
        if (in.act() == null) return;
        Access a = access(actor);
        if (!a.panel()) {
            send(actor, Net.Sync.denied());
            return;
        }
        String notice = apply(actor, a, in);
        save();
        broadcastVisuals(null);
        sendSync(actor, false, notice == null ? "" : notice);
    }

    private static void handleRole(ServerPlayer actor, Net.RoleEdit edit) {
        Access a = access(actor);
        if (!a.panel()) {
            send(actor, Net.Sync.denied());
            return;
        }
        String notice = editRoles(a, edit);
        save();
        broadcastVisuals(null);
        sendSync(actor, false, notice == null ? "" : notice);
    }

    private static void handleWorld(ServerPlayer actor, int kind, int value) {
        Access a = access(actor);
        if (!a.panel()) {
            send(actor, Net.Sync.denied());
            return;
        }
        String notice = null;
        if (!a.has(Perm.SERVER_WORLD)) {
            notice = NO_PERM;
        } else {
            ServerLevel w = server.overworld();
            switch (kind) {
                case Net.WORLD_WEATHER -> {
                    if (value == 0) w.setWeatherParameters(6000, 0, false, false);
                    else if (value == 1) w.setWeatherParameters(0, 6000, true, false);
                    else w.setWeatherParameters(0, 6000, true, true);
                }
                case Net.WORLD_TIME -> {
                    long day = (w.getDayTime() / 24000L) * 24000L;
                    w.setDayTime(day + Math.max(0, Math.min(23999, value)));
                }
                case Net.WORLD_SEASON -> data.season = Season.byOrdinal(value).name();
                default -> { }
            }
        }
        save();
        broadcastVisuals(null);
        sendSync(actor, false, notice == null ? "" : notice);
    }

    // ================================================================== actions on players

    /** @return a message for the actor if the action was refused, otherwise null. */
    private static String apply(ServerPlayer actor, Access a, Net.Action in) {
        Net.Act act = in.act();

        if (act == Net.Act.UNBAN) {
            return a.has(Perm.BAN) ? unban(in.s1()) : NO_PERM;
        }

        ServerPlayer t = server.getPlayerList().getPlayer(in.target());
        if (t == null) return "That player is no longer online.";
        Access ta = access(t);
        boolean self = t.getUUID().equals(actor.getUUID());
        boolean on = in.value() != 0;

        // Hierarchy: you can never act on someone more senior than you (going TO them is allowed).
        if (act != Net.Act.GOTO && !AccessRules.canTouch(a.master(), a.rank(), ta.master(), ta.rank())) {
            return TOO_SENIOR;
        }

        switch (act) {
            case BRING -> {
                if (!a.has(Perm.TELEPORT)) return NO_PERM;
                if (!self) {
                    t.teleportTo((ServerLevel) actor.level(), actor.getX(), actor.getY(), actor.getZ(),
                            actor.getYRot(), actor.getXRot());
                }
            }
            case GOTO -> {
                if (!a.has(Perm.TELEPORT)) return NO_PERM;
                if (!self) {
                    actor.teleportTo((ServerLevel) t.level(), t.getX(), t.getY(), t.getZ(), t.getYRot(), t.getXRot());
                }
            }
            case RESPAWN -> {
                if (!a.has(Perm.RESPAWN)) return NO_PERM;
                server.getPlayerList().respawn(t, true);
            }
            case HEAL -> {
                if (!a.has(Perm.HEAL)) return NO_PERM;
                t.setHealth(t.getMaxHealth());
                t.getFoodData().setFoodLevel(20);
                t.getFoodData().setSaturation(5.0F);
                t.clearFire();
                t.setAirSupply(t.getMaxAirSupply());
            }
            case KICK -> {
                if (!a.has(Perm.KICK)) return NO_PERM;
                if (self) return "You can't kick yourself.";
                t.connection.disconnect(Component.literal("You were kicked from the server."));
            }
            case SET_ROLE -> {
                if (!a.has(Perm.ASSIGN_ROLES)) return NO_PERM;
                return setRole(a, t, in.s1());
            }
            case PANEL -> {
                if (!a.master()) return "Only the server owner / an operator can grant panel access.";
                String key = t.getUUID().toString();
                if (on) data.panelAccess.add(key);
                else data.panelAccess.remove(key);
            }
            case GOD -> {
                if (!a.has(Perm.GODMODE)) return NO_PERM;
                toggle(GOD, t.getUUID(), on);
            }
            case FREEZE -> {
                if (!a.has(Perm.FREEZE)) return NO_PERM;
                if (self) return "You can't freeze yourself.";
                if (on) FROZEN.put(t.getUUID(), t.position());
                else FROZEN.remove(t.getUUID());
            }
            case INVIS -> {
                if (!a.has(Perm.INVISIBLE)) return NO_PERM;
                toggle(INVIS, t.getUUID(), on);
                applyInvisible(t, on);
            }
            case FLY -> {
                if (!a.has(Perm.FLY)) return NO_PERM;
                toggle(FLY, t.getUUID(), on);
                applyFly(t, on);
            }
            case ESP -> {
                if (!a.has(Perm.SIGHT)) return NO_PERM;
                toggle(ESP, t.getUUID(), on);
            }
            case HEALTHBARS -> {
                if (!a.has(Perm.SIGHT)) return NO_PERM;
                toggle(HEALTH_BARS, t.getUUID(), on);
            }
            case BAN -> {
                if (!a.has(Perm.BAN)) return NO_PERM;
                if (self) return "You can't ban yourself.";
                ban(actor, t, in.value(), clip(in.s1(), 256), clip(in.s2(), 256));
            }
            default -> { }
        }
        return null;
    }

    private static String setRole(Access a, ServerPlayer t, String newRoleId) {
        String key = t.getUUID().toString();
        RoleDef current = data.roleOf(key);
        RoleDef next = newRoleId.isEmpty() ? null : data.role(newRoleId);
        if (!newRoleId.isEmpty() && next == null) return "That role no longer exists.";
        if (current != null && !AccessRules.canGrantRole(a.master(), a.rank(), data.roles.indexOf(current))) {
            return TOO_SENIOR_ROLE;
        }
        if (next != null && !AccessRules.canGrantRole(a.master(), a.rank(), data.roles.indexOf(next))) {
            return TOO_SENIOR_ROLE;
        }
        if (next == null) data.playerRoles.remove(key);
        else data.playerRoles.put(key, next.id);
        return null;
    }

    private static void ban(ServerPlayer actor, ServerPlayer t, int minutes, String publicReason, String privateReason) {
        String reason = publicReason.isBlank() ? "Banned by staff." : publicReason;
        Date expires = minutes <= 0 ? null : new Date(System.currentTimeMillis() + minutes * 60_000L);
        UserBanList bans = server.getPlayerList().getBans();
        bans.add(new UserBanListEntry(t.getGameProfile(), new Date(), actor.getGameProfile().getName(), expires, reason));
        String key = t.getUUID().toString();
        data.banNames.put(key, t.getGameProfile().getName());
        if (privateReason.isBlank()) data.privateNotes.remove(key);
        else data.privateNotes.put(key, privateReason);
        t.connection.disconnect(Component.literal("You are banned from this server.\nReason: " + reason));
    }

    private static String unban(String query) {
        String q = query.trim();
        if (q.isEmpty()) return "Enter a username or user ID.";
        UserBanList bans = server.getPlayerList().getBans();
        for (String key : bans.getUserList()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (key.equalsIgnoreCase(q) || banName(id).equalsIgnoreCase(q)) {
                bans.remove(new GameProfile(id, null));
                data.privateNotes.remove(key);
                data.banNames.remove(key);
                return "Unbanned " + banName(id) + ".";
            }
        }
        return "Nobody banned matches \"" + q + "\".";
    }

    private static String banName(UUID id) {
        String key = id.toString();
        String n = data.banNames.get(key);
        if (n != null) return n;
        try {
            GameProfileCache cache = server.getProfileCache();
            if (cache != null) {
                Optional<GameProfile> p = cache.get(id);
                if (p.isPresent() && p.get().getName() != null) return p.get().getName();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return key;
    }

    private static void toggle(Set<UUID> set, UUID id, boolean on) {
        if (on) set.add(id);
        else set.remove(id);
    }

    private static void applyFly(ServerPlayer p, boolean on) {
        Abilities ab = p.getAbilities();
        if (on) {
            ab.mayfly = true;
        } else if (!p.isCreative() && !p.isSpectator()) {
            ab.mayfly = false;
            ab.flying = false;
        }
        p.onUpdateAbilities();
    }

    private static void applyInvisible(ServerPlayer p, boolean on) {
        if (on) {
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0,
                    false, false, false));
        } else {
            p.removeEffect(MobEffects.INVISIBILITY);
        }
    }

    // ================================================================== role editing

    private static String editRoles(Access a, Net.RoleEdit e) {
        if (!a.has(Perm.MANAGE_ROLES)) return NO_PERM;
        if (e.op() == null) return null;

        switch (e.op()) {
            case CREATE -> {
                String name = clip(e.name().trim(), 24);
                if (name.isEmpty()) return "Give the role a name.";
                if (data.roles.size() >= 40) return "There are too many roles already.";
                long perms = AccessRules.clampPerms(a.master(), a.perms(), e.perms(), 0L);
                // New roles always go to the bottom of the hierarchy.
                data.roles.add(new RoleDef(data.uniqueRoleId(name), name, e.color(), perms));
            }
            case UPDATE -> {
                RoleDef r = data.role(e.id());
                if (r == null) return "That role no longer exists.";
                if (!AccessRules.canEditRole(a.master(), a.rank(), data.roles.indexOf(r))) return TOO_SENIOR_ROLE;
                String name = clip(e.name().trim(), 24);
                if (name.isEmpty()) return "Give the role a name.";
                r.name = name;
                r.color = e.color() & 0xFFFFFF;
                r.perms = AccessRules.clampPerms(a.master(), a.perms(), e.perms(), r.perms);
            }
            case DELETE -> {
                RoleDef r = data.role(e.id());
                if (r == null) return null;
                if (!AccessRules.canEditRole(a.master(), a.rank(), data.roles.indexOf(r))) return TOO_SENIOR_ROLE;
                data.roles.remove(r);
                data.playerRoles.values().removeIf(id -> id.equals(r.id));
            }
            case REORDER -> {
                List<String> order = e.order();
                if (order.size() != data.roles.size() || new HashSet<>(order).size() != order.size()) {
                    return "Invalid role order.";
                }
                List<RoleDef> sorted = new ArrayList<>();
                for (String id : order) {
                    RoleDef r = data.role(id);
                    if (r == null) return "Invalid role order.";
                    sorted.add(r);
                }
                if (!a.master()) {
                    for (int i = 0; i <= a.rank() && i < data.roles.size(); i++) {
                        if (!sorted.get(i).id.equals(data.roles.get(i).id)) {
                            return "You can only reorder roles below your own.";
                        }
                    }
                }
                data.roles = sorted;
            }
            default -> { }
        }
        return null;
    }

    // ================================================================== outgoing

    private static void send(ServerPlayer p, Net.Sync s) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        s.write(buf);
        ServerPlayNetworking.send(p, Net.S2C_SYNC, buf);
    }

    private static void sendSync(ServerPlayer actor, boolean open, String notice) {
        Access a = access(actor);
        if (!a.panel()) {
            send(actor, Net.Sync.denied());
            return;
        }

        List<Net.RoleInfo> roles = new ArrayList<>();
        for (RoleDef r : data.roles) roles.add(new Net.RoleInfo(r.id, r.name, r.color, r.perms));

        List<Net.PlayerEntry> players = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            Access ta = access(p);
            players.add(new Net.PlayerEntry(id, p.getGameProfile().getName(),
                    data.playerRoles.getOrDefault(id.toString(), ""),
                    ta.panel(), GOD.contains(id), FROZEN.containsKey(id), INVIS.contains(id), FLY.contains(id),
                    ESP.contains(id), HEALTH_BARS.contains(id),
                    AccessRules.canTouch(a.master(), a.rank(), ta.master(), ta.rank()), ta.master()));
        }

        List<Net.BanEntry> banList = new ArrayList<>();
        UserBanList bans = server.getPlayerList().getBans();
        for (String key : bans.getUserList()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            UserBanListEntry e = bans.get(new GameProfile(id, null));
            if (e == null) continue; // expired
            Date exp = e.getExpires();
            banList.add(new Net.BanEntry(key, banName(id), String.valueOf(e.getReason()),
                    data.privateNotes.getOrDefault(key, ""), exp == null ? -1L : exp.getTime(),
                    String.valueOf(e.getSource())));
        }

        send(actor, new Net.Sync(true, open, a.master(), a.rank(), a.perms(), Season.byName(data.season).ordinal(),
                notice, roles, players, banList));
    }

    /** Role tags for everyone + each player's own sight flags + the season. */
    private static void broadcastVisuals(UUID exclude) {
        if (server == null) return;
        List<Net.Tag> tags = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(exclude)) continue;
            RoleDef r = data.roleOf(p.getUUID().toString());
            if (r != null) tags.add(new Net.Tag(p.getUUID(), r.name, r.color));
        }
        int season = Season.byName(data.season).ordinal();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(exclude)) continue;
            FriendlyByteBuf buf = PacketByteBufs.create();
            new Net.Visuals(tags, ESP.contains(p.getUUID()), HEALTH_BARS.contains(p.getUUID()), season).write(buf);
            ServerPlayNetworking.send(p, Net.S2C_VISUALS, buf);
        }
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
