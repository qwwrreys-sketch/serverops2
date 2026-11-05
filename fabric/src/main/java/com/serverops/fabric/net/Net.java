package com.serverops.fabric.net;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Channel ids and the (de)serialisation of every packet. Uses Fabric API custom payload channels. */
public final class Net {
    private Net() {}

    public static final String MOD_ID = "serverops";

    // client -> server
    public static final ResourceLocation C2S_OPEN = id("open");
    public static final ResourceLocation C2S_REFRESH = id("refresh");
    public static final ResourceLocation C2S_ACTION = id("action");
    public static final ResourceLocation C2S_ROLE = id("role");
    public static final ResourceLocation C2S_WORLD = id("world");
    // server -> client
    public static final ResourceLocation S2C_SYNC = id("sync");
    public static final ResourceLocation S2C_VISUALS = id("visuals");

    private static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    /** Things staff can do to a player. */
    public enum Act { BRING, GOTO, RESPAWN, HEAL, KICK, SET_ROLE, PANEL, GOD, FREEZE, INVIS, FLY, ESP, HEALTHBARS, BAN, UNBAN }

    public enum RoleOp { CREATE, UPDATE, DELETE, REORDER }

    /** World-tab settings. */
    public static final int WORLD_WEATHER = 0, WORLD_TIME = 1, WORLD_SEASON = 2;

    // ---------------------------------------------------------------- records

    public record RoleInfo(String id, String name, int color, long perms) {}

    public record PlayerEntry(UUID id, String name, String roleId, boolean panel, boolean god, boolean freeze,
                              boolean invis, boolean fly, boolean esp, boolean healthBars,
                              boolean canAct, boolean master) {}

    public record BanEntry(String uuid, String name, String publicReason, String privateReason,
                           long expires, String by) {}

    /** Full panel state for one staff member. */
    public record Sync(boolean allowed, boolean open, boolean master, int rank, long perms, int season,
                       String notice, List<RoleInfo> roles, List<PlayerEntry> players, List<BanEntry> bans) {

        public static Sync denied() {
            return new Sync(false, false, false, 0, 0, 0, "", List.of(), List.of(), List.of());
        }

        public void write(FriendlyByteBuf b) {
            b.writeBoolean(allowed);
            b.writeBoolean(open);
            b.writeBoolean(master);
            b.writeInt(rank);
            b.writeLong(perms);
            b.writeVarInt(season);
            b.writeUtf(notice);
            b.writeVarInt(roles.size());
            for (RoleInfo r : roles) {
                b.writeUtf(r.id);
                b.writeUtf(r.name);
                b.writeInt(r.color);
                b.writeLong(r.perms);
            }
            b.writeVarInt(players.size());
            for (PlayerEntry p : players) {
                b.writeUUID(p.id);
                b.writeUtf(p.name);
                b.writeUtf(p.roleId);
                b.writeBoolean(p.panel);
                b.writeBoolean(p.god);
                b.writeBoolean(p.freeze);
                b.writeBoolean(p.invis);
                b.writeBoolean(p.fly);
                b.writeBoolean(p.esp);
                b.writeBoolean(p.healthBars);
                b.writeBoolean(p.canAct);
                b.writeBoolean(p.master);
            }
            b.writeVarInt(bans.size());
            for (BanEntry e : bans) {
                b.writeUtf(e.uuid);
                b.writeUtf(e.name);
                b.writeUtf(e.publicReason);
                b.writeUtf(e.privateReason);
                b.writeLong(e.expires);
                b.writeUtf(e.by);
            }
        }

        public static Sync read(FriendlyByteBuf b) {
            boolean allowed = b.readBoolean();
            boolean open = b.readBoolean();
            boolean master = b.readBoolean();
            int rank = b.readInt();
            long perms = b.readLong();
            int season = b.readVarInt();
            String notice = b.readUtf();
            int rn = b.readVarInt();
            List<RoleInfo> roles = new ArrayList<>(rn);
            for (int i = 0; i < rn; i++) roles.add(new RoleInfo(b.readUtf(), b.readUtf(), b.readInt(), b.readLong()));
            int pn = b.readVarInt();
            List<PlayerEntry> players = new ArrayList<>(pn);
            for (int i = 0; i < pn; i++) {
                players.add(new PlayerEntry(b.readUUID(), b.readUtf(), b.readUtf(), b.readBoolean(), b.readBoolean(),
                        b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(),
                        b.readBoolean(), b.readBoolean()));
            }
            int bn = b.readVarInt();
            List<BanEntry> bans = new ArrayList<>(bn);
            for (int i = 0; i < bn; i++) {
                bans.add(new BanEntry(b.readUtf(), b.readUtf(), b.readUtf(), b.readUtf(), b.readLong(), b.readUtf()));
            }
            return new Sync(allowed, open, master, rank, perms, season, notice, roles, players, bans);
        }
    }

    public record Tag(UUID id, String name, int color) {}

    /** What every client needs to render: role tags for everybody + this client's own sight flags + season. */
    public record Visuals(List<Tag> tags, boolean esp, boolean healthBars, int season) {
        public void write(FriendlyByteBuf b) {
            b.writeVarInt(tags.size());
            for (Tag t : tags) {
                b.writeUUID(t.id);
                b.writeUtf(t.name);
                b.writeInt(t.color);
            }
            b.writeBoolean(esp);
            b.writeBoolean(healthBars);
            b.writeVarInt(season);
        }

        public static Visuals read(FriendlyByteBuf b) {
            int n = b.readVarInt();
            List<Tag> tags = new ArrayList<>(n);
            for (int i = 0; i < n; i++) tags.add(new Tag(b.readUUID(), b.readUtf(), b.readInt()));
            return new Visuals(tags, b.readBoolean(), b.readBoolean(), b.readVarInt());
        }
    }

    // ---------------------------------------------------------------- C2S payloads

    public record Action(Act act, UUID target, int value, String s1, String s2) {
        public void write(FriendlyByteBuf b) {
            b.writeVarInt(act.ordinal());
            b.writeUUID(target);
            b.writeVarInt(value);
            b.writeUtf(s1, 512);
            b.writeUtf(s2, 512);
        }

        public static Action read(FriendlyByteBuf b) {
            int o = b.readVarInt();
            Act[] all = Act.values();
            Act act = o >= 0 && o < all.length ? all[o] : null;
            return new Action(act, b.readUUID(), b.readVarInt(), b.readUtf(512), b.readUtf(512));
        }
    }

    public record RoleEdit(RoleOp op, String id, String name, int color, long perms, List<String> order) {
        public void write(FriendlyByteBuf b) {
            b.writeVarInt(op.ordinal());
            b.writeUtf(id);
            b.writeUtf(name, 64);
            b.writeInt(color);
            b.writeLong(perms);
            b.writeVarInt(order.size());
            for (String s : order) b.writeUtf(s);
        }

        public static RoleEdit read(FriendlyByteBuf b) {
            int o = b.readVarInt();
            RoleOp[] all = RoleOp.values();
            RoleOp op = o >= 0 && o < all.length ? all[o] : null;
            String id = b.readUtf();
            String name = b.readUtf(64);
            int color = b.readInt();
            long perms = b.readLong();
            int n = Math.min(b.readVarInt(), 256);
            List<String> order = new ArrayList<>(n);
            for (int i = 0; i < n; i++) order.add(b.readUtf());
            return new RoleEdit(op, id, name, color, perms, order);
        }
    }
}
