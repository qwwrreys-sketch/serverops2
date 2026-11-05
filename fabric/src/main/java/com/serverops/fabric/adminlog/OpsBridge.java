package com.serverops.fabric.adminlog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.serverops.common.OpsData;
import com.serverops.common.Perm;
import com.serverops.fabric.server.OpsService;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * READ-ONLY window into the admin panel's server state.
 *
 * <p>OpsService keeps its state in private static fields and its permission check in a private method.
 * Instead of editing that class (forbidden) or copying the rules (they could drift apart), this class reads
 * them through reflection. It never writes anything and never changes visibility rules for anyone else.
 * If a name ever changes in OpsService, {@link #available()} turns false, a warning is printed once, and the
 * notifier simply stays silent - the admin panel itself is never affected.
 */
final class OpsBridge {
    private OpsBridge() {}

    /** The panel's own access result for one player (same numbers OpsService.access() computes). */
    record Acc(boolean master, boolean panel, int rank, long perms) {
        boolean has(Perm p) {
            return Perm.has(perms, p);
        }
    }

    private static final Map<String, Field> FIELDS = new HashMap<>();
    private static Method access;
    private static Method mMaster;
    private static Method mPanel;
    private static Method mRank;
    private static Method mPerms;
    private static boolean ok;

    static {
        try {
            Class<?> c = OpsService.class;
            for (String n : new String[] {"GOD", "INVIS", "FLY", "ESP", "HEALTH_BARS", "FROZEN", "data"}) {
                Field f = c.getDeclaredField(n);
                f.setAccessible(true);
                FIELDS.put(n, f);
            }
            access = c.getDeclaredMethod("access", ServerPlayer.class);
            access.setAccessible(true);
            Class<?> rec = access.getReturnType();
            mMaster = accessor(rec, "master");
            mPanel = accessor(rec, "panel");
            mRank = accessor(rec, "rank");
            mPerms = accessor(rec, "perms");
            ok = true;
        } catch (Throwable t) {
            System.err.println("[ServerOps][AdminLog] Cannot read the admin panel state, notifications disabled: " + t);
        }
    }

    private static Method accessor(Class<?> c, String name) throws NoSuchMethodException {
        Method m = c.getDeclaredMethod(name);
        m.setAccessible(true);
        return m;
    }

    static boolean available() {
        return ok;
    }

    static Acc access(ServerPlayer p) throws ReflectiveOperationException {
        Object o = access.invoke(null, p);
        return new Acc((Boolean) mMaster.invoke(o), (Boolean) mPanel.invoke(o), (Integer) mRank.invoke(o),
                (Long) mPerms.invoke(o));
    }

    /** Does the server's existing check let this player into the admin panel? (OP 4 / panel grant / panel role) */
    static boolean isAdmin(ServerPlayer p) {
        try {
            return ok && access(p).panel();
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    static Set<UUID> set(String name) throws ReflectiveOperationException {
        return (Set<UUID>) FIELDS.get(name).get(null);
    }

    @SuppressWarnings("unchecked")
    static Map<UUID, Vec3> frozen() throws ReflectiveOperationException {
        return (Map<UUID, Vec3>) FIELDS.get("FROZEN").get(null);
    }

    static OpsData data() throws ReflectiveOperationException {
        return (OpsData) FIELDS.get("data").get(null);
    }
}
