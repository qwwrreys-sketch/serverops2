package com.serverops.fabric.client;

import java.util.List;
import java.util.UUID;

import com.serverops.fabric.net.Net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

/** Thin helpers for everything the client sends. */
public final class ClientNet {
    private ClientNet() {}

    private static final UUID NONE = new UUID(0L, 0L);

    public static void requestOpen() {
        ClientPlayNetworking.send(Net.C2S_OPEN, PacketByteBufs.create());
    }

    public static void requestRefresh() {
        ClientPlayNetworking.send(Net.C2S_REFRESH, PacketByteBufs.create());
    }

    public static void action(Net.Act act, UUID target, int value, String s1, String s2) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        new Net.Action(act, target, value, s1, s2).write(buf);
        ClientPlayNetworking.send(Net.C2S_ACTION, buf);
    }

    public static void action(Net.Act act, UUID target) {
        action(act, target, 0, "", "");
    }

    public static void toggle(Net.Act act, UUID target, boolean on) {
        action(act, target, on ? 1 : 0, "", "");
    }

    public static void setRole(UUID target, String roleId) {
        action(Net.Act.SET_ROLE, target, 0, roleId, "");
    }

    public static void ban(UUID target, int minutes, String publicReason, String privateReason) {
        action(Net.Act.BAN, target, minutes, publicReason, privateReason);
    }

    public static void unban(String usernameOrId) {
        action(Net.Act.UNBAN, NONE, 0, usernameOrId, "");
    }

    public static void role(Net.RoleOp op, String id, String name, int color, long perms, List<String> order) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        new Net.RoleEdit(op, id, name, color, perms, order).write(buf);
        ClientPlayNetworking.send(Net.C2S_ROLE, buf);
    }

    public static void world(int kind, int value) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(kind);
        buf.writeVarInt(value);
        ClientPlayNetworking.send(Net.C2S_WORLD, buf);
    }
}
