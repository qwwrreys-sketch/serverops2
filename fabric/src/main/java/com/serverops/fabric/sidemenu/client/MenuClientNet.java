package com.serverops.fabric.sidemenu.client;

import com.serverops.fabric.sidemenu.MenuNet;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

/** Everything the side menu sends to the server. */
public final class MenuClientNet {
    private MenuClientNet() {}

    /** "Send me my profile and the player directory." */
    public static void request() {
        ClientPlayNetworking.send(MenuNet.C2S_REQUEST, PacketByteBufs.create());
    }

    public static void character(MenuNet.CharOp op, String name, int year, int raceOrdinal) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        new MenuNet.CharacterRequest(op, name, year, raceOrdinal).write(buf);
        ClientPlayNetworking.send(MenuNet.C2S_CHARACTER, buf);
    }

    public static void code(String code) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(code, 64);
        ClientPlayNetworking.send(MenuNet.C2S_CODE, buf);
    }
}
