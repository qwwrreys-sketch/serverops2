package com.serverops.fabric.adminlog;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** The one packet of the notifier: server -> admin client, "show this card in the top-right corner". */
public final class AdminLogNet {
    private AdminLogNet() {}

    public static final ResourceLocation S2C_TOAST = new ResourceLocation("serverops", "admin_toast");

    /** Decides the accent colour and the small label of a card. Order is sent as a number - do not reorder. */
    public enum Kind {
        BAN("BAN"), FREEZE("FREEZE"), GOD("GOD MODE"), INVIS("INVISIBLE"), VIEW("VISION"), ROLE("ROLES"),
        WORLD("WORLD"), OTHER("ADMIN");

        public final String label;

        Kind(String label) {
            this.label = label;
        }

        static Kind byOrdinal(int o) {
            Kind[] all = values();
            return o >= 0 && o < all.length ? all[o] : OTHER;
        }
    }

    /** {@code text} parts separated by "  |  " become separate lines on the card. */
    public record Toast(Kind kind, String actor, String text) {
        public void write(FriendlyByteBuf b) {
            b.writeVarInt(kind.ordinal());
            b.writeUtf(actor, 64);
            b.writeUtf(text, 600);
        }

        public static Toast read(FriendlyByteBuf b) {
            return new Toast(Kind.byOrdinal(b.readVarInt()), b.readUtf(64), b.readUtf(600));
        }
    }
}
