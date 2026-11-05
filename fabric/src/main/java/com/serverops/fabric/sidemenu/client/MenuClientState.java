package com.serverops.fabric.sidemenu.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.serverops.fabric.sidemenu.MenuNet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

/** Everything the side menu knows on the client. Only touched from the client thread. */
public final class MenuClientState {
    private MenuClientState() {}

    /** My own character / balance. */
    public static MenuNet.MyProfile profile = MenuNet.MyProfile.empty();
    public static boolean profileLoaded;
    /** Incremented on every packet so the screen knows to rebuild. */
    public static int version;

    public static String notice = "";
    public static long noticeUntil;

    private record Dir(String rpName, long baseSeconds, long receivedAt) {}

    private static final Map<UUID, Dir> DIR = new HashMap<>();

    public static void applyProfile(MenuNet.MyProfile p) {
        profile = p;
        profileLoaded = true;
        version++;
        if (!p.notice().isEmpty()) {
            notice = p.notice();
            noticeUntil = System.currentTimeMillis() + 4500;
        }
    }

    public static void applyDirectory(List<MenuNet.DirEntry> list) {
        DIR.clear();
        long now = System.currentTimeMillis();
        for (MenuNet.DirEntry e : list) DIR.put(e.id(), new Dir(e.rpName(), e.playSeconds(), now));
        version++;
    }

    public static void reset() {
        profile = MenuNet.MyProfile.empty();
        profileLoaded = false;
        DIR.clear();
        notice = "";
        noticeUntil = 0;
        version++;
    }

    /** Live playtime: the server's total at the time of the packet plus the time that passed since. -1 = unknown. */
    public static long playSeconds(UUID id) {
        Dir d = DIR.get(id);
        if (d == null) return -1;
        return d.baseSeconds() + Math.max(0, (System.currentTimeMillis() - d.receivedAt()) / 1000);
    }

    public static String rpName(UUID id) {
        Dir d = DIR.get(id);
        return d == null ? "" : d.rpName();
    }

    /** One line of the Player List. */
    public record Online(UUID id, String username, String display, boolean me) {}

    /**
     * Everybody currently connected. The set of players comes straight from the client's own tab list
     * (so it is real-time, no packet needed); roleplay names / playtime are added from the server directory.
     */
    public static List<Online> online(String lowerQuery) {
        List<Online> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener conn = mc.getConnection();
        if (conn == null) return out;
        UUID myId = mc.player == null ? null : mc.player.getUUID();
        for (PlayerInfo info : conn.getOnlinePlayers()) {
            UUID id = info.getProfile().getId();
            String user = info.getProfile().getName();
            String rp = rpName(id);
            String display = rp.isEmpty() ? user : rp;
            if (!lowerQuery.isEmpty()
                    && !display.toLowerCase(Locale.ROOT).contains(lowerQuery)
                    && !user.toLowerCase(Locale.ROOT).contains(lowerQuery)) continue;
            out.add(new Online(id, user, display, id.equals(myId)));
        }
        // me first (like the reference design), then alphabetical
        out.sort((a, b) -> {
            if (a.me() != b.me()) return a.me() ? -1 : 1;
            return a.display().compareToIgnoreCase(b.display());
        });
        return out;
    }

    /** Cheap fingerprint of "who is online" so the screen can rebuild only when the set changes. */
    public static int onlineSignature() {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null) return 0;
        int sig = 0;
        for (PlayerInfo info : conn.getOnlinePlayers()) sig += info.getProfile().getId().hashCode();
        return sig * 31 + conn.getOnlinePlayers().size();
    }

    public static String formatPlaytime(long seconds) {
        if (seconds < 0) return "-";
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        if (d > 0) return d + "d " + h + "h " + m + "m";
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }
}
