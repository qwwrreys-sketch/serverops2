package com.serverops.fabric.sidemenu;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.serverops.common.menu.CharacterRules;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Channel ids + (de)serialisation of the side menu's packets. Completely separate from the admin panel's
 * {@code Net} class: different channel names, nothing shared.
 */
public final class MenuNet {
    private MenuNet() {}

    private static final String NS = "serverops";

    // client -> server
    public static final ResourceLocation C2S_REQUEST = new ResourceLocation(NS, "menu_request");     // "send me my data"
    public static final ResourceLocation C2S_CHARACTER = new ResourceLocation(NS, "menu_character"); // create / change
    public static final ResourceLocation C2S_CODE = new ResourceLocation(NS, "menu_code");           // redeem promo code
    // server -> client
    public static final ResourceLocation S2C_PROFILE = new ResourceLocation(NS, "menu_profile");
    public static final ResourceLocation S2C_DIRECTORY = new ResourceLocation(NS, "menu_directory");

    public enum CharOp { CREATE, CHANGE_NAME, CHANGE_RACE, REROLL_SURNAME }

    /** What the player asks for. {@code race} is a Race ordinal. */
    public record CharacterRequest(CharOp op, String name, int year, int race) {
        public void write(FriendlyByteBuf b) {
            b.writeVarInt(op.ordinal());
            b.writeUtf(name, 64);
            b.writeVarInt(year);
            b.writeVarInt(race);
        }

        public static CharacterRequest read(FriendlyByteBuf b) {
            int o = b.readVarInt();
            CharOp[] all = CharOp.values();
            CharOp op = o >= 0 && o < all.length ? all[o] : null;
            return new CharacterRequest(op, b.readUtf(64), b.readVarInt(), b.readVarInt());
        }
    }

    /** The local player's own character + balance (+ an optional message to show). */
    public record MyProfile(boolean created, String firstName, String surname, int race, int birthYear,
                            int rerolls, String notice) {
        public static MyProfile empty() {
            return new MyProfile(false, "", "", 0, CharacterRules.BIRTH_YEAR_DEFAULT, 0, "");
        }

        public void write(FriendlyByteBuf b) {
            b.writeBoolean(created);
            b.writeUtf(firstName);
            b.writeUtf(surname);
            b.writeVarInt(race);
            b.writeVarInt(birthYear);
            b.writeVarInt(rerolls);
            b.writeUtf(notice);
        }

        public static MyProfile read(FriendlyByteBuf b) {
            return new MyProfile(b.readBoolean(), b.readUtf(), b.readUtf(), b.readVarInt(), b.readVarInt(),
                    b.readVarInt(), b.readUtf());
        }
    }

    /** One connected player as the server sees them. {@code rpName} is "" until they made a character. */
    public record DirEntry(UUID id, String username, String rpName, long playSeconds) {}

    public static void writeDirectory(FriendlyByteBuf b, List<DirEntry> list) {
        b.writeVarInt(list.size());
        for (DirEntry e : list) {
            b.writeUUID(e.id);
            b.writeUtf(e.username);
            b.writeUtf(e.rpName);
            b.writeVarLong(e.playSeconds);
        }
    }

    public static List<DirEntry> readDirectory(FriendlyByteBuf b) {
        int n = Math.min(b.readVarInt(), 4096);
        List<DirEntry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(new DirEntry(b.readUUID(), b.readUtf(), b.readUtf(), b.readVarLong()));
        return out;
    }
}
