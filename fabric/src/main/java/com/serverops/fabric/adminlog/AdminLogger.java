package com.serverops.fabric.adminlog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.mojang.authlib.GameProfile;
import com.serverops.common.AccessRules;
import com.serverops.common.Perm;
import com.serverops.fabric.net.Net;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Tells every online admin what the other admins just did, and keeps a log file.
 *
 * <h3>How it hooks in without touching OpsService</h3>
 * The panel registers ONE handler per packet channel (action / role / world). At server start this class takes
 * each handler out and registers a thin wrapper in its place. For every packet the wrapper
 * <ol>
 *   <li>reads a private copy of the packet,</li>
 *   <li>queues "snapshot BEFORE" on the server thread,</li>
 *   <li>hands the untouched packet to the panel's original handler (queues its own work),</li>
 *   <li>queues "snapshot AFTER" and announces whatever changed.</li>
 * </ol>
 * The server thread runs the three tasks in that order. The original handler gets the same packet and does
 * exactly what it always did; if anything in this class fails, the exception is caught and printed and the
 * panel keeps working.
 *
 * <p>Announcements go to every online player the panel's own access check lets in (except the admin who
 * acted), as a card in the top-right corner of their screen (optionally also a gold "[Staff]" chat line), and are appended to {@code <world>/serverops/admin-log.txt}.
 */
public final class AdminLogger {
    private AdminLogger() {}

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static boolean installed;
    private static boolean warned;

    private record WorldReq(int kind, int value) {}

    public static void install() {
        // SERVER_STARTING runs after ALL mod initialisers, so OpsService has registered its handlers by then.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            if (installed) return; // the receivers are global: wrap only once, even for several worlds
            installed = true;
            if (!OpsBridge.available()) return;
            wrap(Net.C2S_ACTION, Net.Action::read, Net.Action::target, AdminLogger::reportAction);
            wrap(Net.C2S_ROLE, Net.RoleEdit::read, e -> null, AdminLogger::reportRoles);
            wrap(Net.C2S_WORLD, b -> new WorldReq(b.readVarInt(), b.readVarInt()), w -> null, AdminLogger::reportWorld);
        });
    }

    // ================================================================== wrapping

    private interface Reporter<T> {
        void report(MinecraftServer s, ServerPlayer actor, T req, Snap before, Snap after);
    }

    private static <T> void wrap(ResourceLocation channel, Function<FriendlyByteBuf, T> parser,
                                 Function<T, UUID> targeter, Reporter<T> reporter) {
        var original = ServerPlayNetworking.unregisterGlobalReceiver(channel);
        if (original == null) {
            System.err.println("[ServerOps][AdminLog] No handler found for " + channel + "; not wrapping it.");
            return;
        }
        ServerPlayNetworking.registerGlobalReceiver(channel, (srv, player, handler, buf, sender) -> {
            T req = null;
            try {
                req = parser.apply(new FriendlyByteBuf(buf.copy())); // private copy: the original reads the real buffer
            } catch (Throwable t) {
                warn(t);
            }
            if (req == null) {
                original.receive(srv, player, handler, buf, sender);
                return;
            }
            final T request = req;
            final Snap[] before = new Snap[1];
            srv.execute(() -> {
                try {
                    before[0] = Snap.take(srv, player, targeter.apply(request));
                } catch (Throwable t) {
                    warn(t);
                }
            });
            original.receive(srv, player, handler, buf, sender); // the panel does its normal work, unchanged
            srv.execute(() -> {
                try {
                    if (before[0] == null) return;
                    Snap after = Snap.take(srv, player, targeter.apply(request));
                    reporter.report(srv, player, request, before[0], after);
                } catch (Throwable t) {
                    warn(t);
                }
            });
        });
    }

    private static void warn(Throwable t) {
        if (warned) return;
        warned = true;
        System.err.println("[ServerOps][AdminLog] Notification failed (the admin panel is not affected): " + t);
    }

    // ================================================================== what happened?

    private static void reportAction(MinecraftServer s, ServerPlayer actor, Net.Action in, Snap b, Snap a) {
        if (in.act() == null) return;
        String who = b.targetName.isEmpty() ? "someone" : b.targetName;
        boolean self = in.target().equals(actor.getUUID());
        String on = self ? "" : " for " + who;
        String key = in.target().toString();
        AdminLogNet.Kind kind = kindOf(in.act());

        switch (in.act()) {
            case BAN -> {
                if (a.banKeys.contains(key) && !b.banKeys.contains(key)) {
                    String reason = in.s1().isBlank() ? "Banned by staff." : clip(in.s1(), 256);
                    announce(s, actor, kind, "BANNED " + who + "  |  Reason: " + reason + "  |  Duration: " + duration(in.value()));
                }
            }
            case UNBAN -> {
                for (String k : b.banKeys) {
                    if (!a.banKeys.contains(k)) announce(s, actor, kind, "UNBANNED " + banName(s, b, k));
                }
            }
            case KICK -> {
                if (may(b, Perm.KICK, true) && !self) announce(s, actor, kind, "KICKED " + who);
            }
            case FREEZE -> {
                if (b.frozen != a.frozen) announce(s, actor, kind, (a.frozen ? "FROZE " : "UNFROZE ") + who);
            }
            case GOD -> {
                if (b.god != a.god) announce(s, actor, kind, "turned God Mode " + onOff(a.god) + on);
            }
            case INVIS -> {
                if (b.invis != a.invis) announce(s, actor, kind, "turned Invisibility " + onOff(a.invis) + on);
            }
            case FLY -> {
                if (b.fly != a.fly) announce(s, actor, kind, "turned Flight " + onOff(a.fly) + on);
            }
            case ESP -> {
                if (b.esp != a.esp) announce(s, actor, kind, "turned ESP " + onOff(a.esp) + on);
            }
            case HEALTHBARS -> {
                if (b.health != a.health) announce(s, actor, kind, "turned HP Bars " + onOff(a.health) + on);
            }
            case SET_ROLE -> {
                if (!b.roleId.equals(a.roleId)) {
                    announce(s, actor, kind, "changed the role of " + who + ": " + roleLabel(b, b.roleId) + " -> " + roleLabel(a, a.roleId));
                }
            }
            case PANEL -> {
                if (b.panelGrant != a.panelGrant) {
                    announce(s, actor, kind, (a.panelGrant ? "GRANTED admin panel access to " : "REVOKED admin panel access from ") + who);
                }
            }
            case HEAL -> {
                if (may(b, Perm.HEAL, true)) announce(s, actor, kind, "healed " + who);
            }
            case BRING -> {
                if (may(b, Perm.TELEPORT, true) && !self) announce(s, actor, kind, "teleported " + who + " to themselves");
            }
            case GOTO -> {
                if (may(b, Perm.TELEPORT, false) && !self) announce(s, actor, kind, "teleported to " + who);
            }
            case RESPAWN -> {
                if (may(b, Perm.RESPAWN, true)) announce(s, actor, kind, "forced " + who + " to respawn");
            }
            default -> { }
        }
    }

    private static void reportRoles(MinecraftServer s, ServerPlayer actor, Net.RoleEdit e, Snap b, Snap a) {
        if (b.rolesSig.equals(a.rolesSig) || e.op() == null) return;
        switch (e.op()) {
            case CREATE -> announce(s, actor, AdminLogNet.Kind.ROLE, "CREATED the role \"" + clip(e.name().trim(), 24) + "\"");
            case UPDATE -> announce(s, actor, AdminLogNet.Kind.ROLE, "EDITED the role \"" + a.roleNames.getOrDefault(e.id(), e.id()) + "\"");
            case DELETE -> announce(s, actor, AdminLogNet.Kind.ROLE, "DELETED the role \"" + b.roleNames.getOrDefault(e.id(), e.id()) + "\"");
            case REORDER -> announce(s, actor, AdminLogNet.Kind.ROLE, "REORDERED the role hierarchy");
            default -> { }
        }
    }

    private static void reportWorld(MinecraftServer s, ServerPlayer actor, WorldReq w, Snap b, Snap a) {
        switch (w.kind()) {
            case Net.WORLD_WEATHER -> {
                if (b.rain != a.rain || b.thunder != a.thunder) {
                    announce(s, actor, AdminLogNet.Kind.WORLD, "set the weather to " + (a.thunder ? "THUNDER" : a.rain ? "RAIN" : "CLEAR"));
                }
            }
            case Net.WORLD_TIME -> {
                if (b.dayTime != a.dayTime) announce(s, actor, AdminLogNet.Kind.WORLD, "set the time of day to " + (a.dayTime % 24000L));
            }
            case Net.WORLD_SEASON -> {
                if (!b.season.equals(a.season)) announce(s, actor, AdminLogNet.Kind.WORLD, "set the season to " + a.season);
            }
            default -> { }
        }
    }

    /**
     * For one-shot actions that leave no state behind (kick, heal, teleport, respawn): was the actor allowed?
     * Uses the panel's own access numbers plus the shared hierarchy rule from the 'common' module.
     */
    private static boolean may(Snap b, Perm perm, boolean hierarchy) {
        if (!b.targetOnline || !b.actor.panel() || !b.actor.has(perm)) return false;
        return !hierarchy || AccessRules.canTouch(b.actor.master(), b.actor.rank(), b.targetAcc.master(), b.targetAcc.rank());
    }

    private static AdminLogNet.Kind kindOf(Net.Act act) {
        return switch (act) {
            case BAN, UNBAN -> AdminLogNet.Kind.BAN;
            case FREEZE -> AdminLogNet.Kind.FREEZE;
            case GOD -> AdminLogNet.Kind.GOD;
            case INVIS -> AdminLogNet.Kind.INVIS;
            case ESP, HEALTHBARS -> AdminLogNet.Kind.VIEW;
            default -> AdminLogNet.Kind.OTHER;
        };
    }

    // ================================================================== wording

    private static String onOff(boolean on) {
        return on ? "ON" : "OFF";
    }

    private static String roleLabel(Snap s, String id) {
        return id.isEmpty() ? "no role" : s.roleNames.getOrDefault(id, id);
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Minutes -> "permanent" or "1d 2h 30m". */
    static String duration(int minutes) {
        if (minutes <= 0) return "permanent";
        int d = minutes / 1440;
        int h = (minutes % 1440) / 60;
        int m = minutes % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0 || sb.length() == 0) sb.append(m).append("m");
        return sb.toString().trim();
    }

    private static String banName(MinecraftServer s, Snap before, String key) {
        String n = before.banNames.get(key);
        if (n != null) return n;
        try {
            var cache = s.getProfileCache();
            if (cache != null) {
                Optional<GameProfile> p = cache.get(UUID.fromString(key));
                if (p.isPresent() && p.get().getName() != null) return p.get().getName();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return key;
    }

    // ================================================================== delivery

    /** Set to true to ALSO print every notice in the chat of the admins (the cards are the main display). */
    private static final boolean ALSO_SEND_CHAT = false;

    private static void announce(MinecraftServer s, ServerPlayer actor, AdminLogNet.Kind kind, String text) {
        String actorName = actor.getGameProfile().getName();
        String line = actorName + " " + text;
        System.out.println("[ServerOps][AdminLog] " + line);
        appendToFile(s, line);

        Component chat = Component.literal("[Staff] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(line).withStyle(ChatFormatting.YELLOW));
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (p == actor) continue; // the acting admin already sees the result in their own panel
            if (!OpsBridge.isAdmin(p)) continue;
            FriendlyByteBuf buf = PacketByteBufs.create();
            new AdminLogNet.Toast(kind, actorName, text).write(buf);
            ServerPlayNetworking.send(p, AdminLogNet.S2C_TOAST, buf);
            if (ALSO_SEND_CHAT) p.sendSystemMessage(chat);
        }
    }

    private static void appendToFile(MinecraftServer s, String line) {
        try {
            Path f = s.getWorldPath(LevelResource.ROOT).resolve("serverops").resolve("admin-log.txt");
            Files.createDirectories(f.getParent());
            Files.writeString(f, "[" + LocalDateTime.now().format(STAMP) + "] " + line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[ServerOps][AdminLog] Could not write admin-log.txt: " + e);
        }
    }
}
