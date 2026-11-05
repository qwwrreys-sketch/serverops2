package com.serverops.fabric.sidemenu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.serverops.common.menu.CharacterData;
import com.serverops.common.menu.CharacterProfile;
import com.serverops.common.menu.CharacterRules;
import com.serverops.common.menu.PromoCodes;
import com.serverops.common.menu.Race;
import com.serverops.common.menu.SurnamePool;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * All server-side logic of the side menu: character creation / paid changes, surname rolling, playtime,
 * the live player directory and promo codes. Everything here runs on the server thread and the server
 * re-validates EVERYTHING the client sends (the client UI is only a convenience).
 *
 * <p>It never touches {@code OpsService}; the admin-panel permission check stays 100% in that class.
 */
public final class MenuService {
    private MenuService() {}

    private static final long CODE_COOLDOWN_MS = 1500;
    private static final int AUTOSAVE_TICKS = 6000; // 5 minutes

    private static MinecraftServer server;
    private static CharacterData data = new CharacterData();

    private static final Map<UUID, Long> SESSION_START = new HashMap<>();
    private static final Map<UUID, Long> LAST_CODE_TRY = new HashMap<>();
    private static final Random RNG = new Random();
    private static int autosave;

    /** Result of a request: did it change anything, and what should the player read. */
    private record Result(boolean ok, String message) {
        static Result fail(String m) {
            return new Result(false, m);
        }

        static Result ok(String m) {
            return new Result(true, m);
        }
    }

    // ================================================================== setup

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            data = CharacterStore.load(s);
            PromoCodes.reload(FabricLoader.getInstance().getConfigDir().resolve("serverops-codes.txt"));
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            if (server != null) {
                flushAllPlaytime();
                save();
            }
            SESSION_START.clear();
            LAST_CODE_TRY.clear();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> onJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> onLeave(handler.player));

        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (++autosave >= AUTOSAVE_TICKS) {
                autosave = 0;
                flushAllPlaytime();
                save();
            }
        });

        // decode on the network thread, act on the server thread (same pattern as OpsService)
        ServerPlayNetworking.registerGlobalReceiver(MenuNet.C2S_REQUEST,
                (srv, player, handler, buf, sender) -> srv.execute(() -> handleRequest(player)));
        ServerPlayNetworking.registerGlobalReceiver(MenuNet.C2S_CHARACTER, (srv, player, handler, buf, sender) -> {
            MenuNet.CharacterRequest req = MenuNet.CharacterRequest.read(buf);
            srv.execute(() -> handleCharacter(player, req));
        });
        ServerPlayNetworking.registerGlobalReceiver(MenuNet.C2S_CODE, (srv, player, handler, buf, sender) -> {
            String code = buf.readUtf(64);
            srv.execute(() -> handleCode(player, code));
        });
    }

    private static void save() {
        CharacterStore.save(server, data);
    }

    private static CharacterProfile profile(UUID id) {
        return data.profile(id.toString());
    }

    // ================================================================== join / leave / playtime

    private static void onJoin(ServerPlayer p) {
        if (server == null) return;
        CharacterProfile c = profile(p.getUUID());
        c.lastKnownName = p.getGameProfile().getName();
        SESSION_START.put(p.getUUID(), System.currentTimeMillis());
        sendProfile(p, "");
        broadcastDirectory(null);
    }

    private static void onLeave(ServerPlayer p) {
        if (server == null) return;
        flushPlaytime(p.getUUID());
        SESSION_START.remove(p.getUUID());
        LAST_CODE_TRY.remove(p.getUUID());
        save();
        broadcastDirectory(p.getUUID()); // the leaving player is still in the list at this point
    }

    /** Moves the running session time into the stored total and restarts the session clock. */
    private static void flushPlaytime(UUID id) {
        Long start = SESSION_START.get(id);
        if (start == null) return;
        long now = System.currentTimeMillis();
        profile(id).playSeconds += Math.max(0, (now - start) / 1000);
        SESSION_START.put(id, now);
    }

    private static void flushAllPlaytime() {
        for (UUID id : new ArrayList<>(SESSION_START.keySet())) flushPlaytime(id);
    }

    private static long totalSeconds(UUID id) {
        long total = profile(id).playSeconds;
        Long start = SESSION_START.get(id);
        if (start != null) total += Math.max(0, (System.currentTimeMillis() - start) / 1000);
        return total;
    }

    // ================================================================== requests

    private static void handleRequest(ServerPlayer p) {
        sendProfile(p, "");
        sendDirectory(p, buildDirectory(null));
    }

    private static void handleCharacter(ServerPlayer p, MenuNet.CharacterRequest req) {
        if (req.op() == null) return;
        CharacterProfile c = profile(p.getUUID());
        Result r = applyCharacter(p.getUUID(), c, req);
        if (r.ok()) {
            save();
            sendProfile(p, r.message());
            broadcastDirectory(null);
        } else {
            sendProfile(p, r.message());
        }
    }

    private static Result applyCharacter(UUID id, CharacterProfile c, MenuNet.CharacterRequest req) {
        switch (req.op()) {
            case CREATE -> {
                if (c.created) return Result.fail("Your character already exists.");
                String name = CharacterRules.cleanName(req.name());
                if (name == null) return Result.fail("Enter a valid first name (letters only, "
                        + CharacterRules.NAME_MIN + "-" + CharacterRules.NAME_MAX + " characters).");
                if (!CharacterRules.validBirthYear(req.year())) return Result.fail("Pick a birth year between "
                        + CharacterRules.BIRTH_YEAR_MIN + " and " + CharacterRules.BIRTH_YEAR_MAX + ".");
                Race race = Race.byOrdinal(req.race());
                if (race == null) return Result.fail("Pick a race.");
                String surname = rollUniqueSurname(id, race, name, null);
                if (surname == null) return Result.fail("No surname could be rolled. Tell an admin.");
                c.created = true;
                c.firstName = name;
                c.surname = surname;
                c.race = race.name();
                c.birthYear = req.year();
                return Result.ok("Welcome, " + c.fullName() + ".");
            }
            case CHANGE_NAME -> {
                if (!c.created) return Result.fail("Create your character first.");
                String name = CharacterRules.cleanName(req.name());
                if (name == null) return Result.fail("Enter a valid first name.");
                if (name.equalsIgnoreCase(c.firstName)) return Result.fail("That is already your first name.");
                if (nameTaken(id, name, c.surname)) return Result.fail("Someone already has the name " + name + " " + c.surname + ".");
                Result paid = pay(c, CharacterRules.COST_NAME_CHANGE);
                if (paid != null) return paid;
                c.firstName = name;
                return Result.ok("Your name is now " + c.fullName() + ".");
            }
            case CHANGE_RACE -> {
                if (!c.created) return Result.fail("Create your character first.");
                Race race = Race.byOrdinal(req.race());
                if (race == null) return Result.fail("Pick a race.");
                if (race == c.raceEnum()) return Result.fail("You already are " + race.label + ".");
                String surname = rollUniqueSurname(id, race, c.firstName, null);
                if (surname == null) return Result.fail("No surname could be rolled. Tell an admin.");
                Result paid = pay(c, CharacterRules.COST_RACE_CHANGE);
                if (paid != null) return paid;
                c.race = race.name();
                c.surname = surname;
                return Result.ok("You are now " + race.label + ": " + c.fullName() + ".");
            }
            case REROLL_SURNAME -> {
                if (!c.created) return Result.fail("Create your character first.");
                String surname = rollUniqueSurname(id, c.raceEnum(), c.firstName, c.surname);
                if (surname == null) return Result.fail("No surname could be rolled. Tell an admin.");
                Result paid = pay(c, CharacterRules.COST_SURNAME_REROLL);
                if (paid != null) return paid;
                c.surname = surname;
                return Result.ok("New surname: " + c.fullName() + ".");
            }
            default -> {
                return Result.fail("Unknown request.");
            }
        }
    }

    /** @return null when paid, otherwise the failure to report (nothing was deducted). */
    private static Result pay(CharacterProfile c, int cost) {
        if (cost <= 0) return null;
        if (c.rerolls < cost) {
            return Result.fail("You need " + cost + " ReRoll" + (cost == 1 ? "" : "s") + " (you have " + c.rerolls + ").");
        }
        c.rerolls -= cost;
        return null;
    }

    /** Rolls from the list of {@code race} only, trying to avoid a full name another player already has. */
    private static String rollUniqueSurname(UUID self, Race race, String firstName, String avoid) {
        String last = "";
        for (int i = 0; i < 40; i++) {
            last = SurnamePool.roll(race, RNG, avoid);
            if (last.isEmpty()) return null; // list missing / empty
            if (!nameTaken(self, firstName, last)) return last;
        }
        return last; // extremely unlikely: accept the last roll rather than failing
    }

    private static boolean nameTaken(UUID self, String first, String last) {
        String me = self.toString();
        for (Map.Entry<String, CharacterProfile> e : data.players.entrySet()) {
            CharacterProfile o = e.getValue();
            if (!o.created || e.getKey().equals(me)) continue;
            if (o.firstName.equalsIgnoreCase(first) && o.surname.equalsIgnoreCase(last)) return true;
        }
        return false;
    }

    // ================================================================== promo codes

    private static void handleCode(ServerPlayer p, String raw) {
        UUID id = p.getUUID();
        long now = System.currentTimeMillis();
        Long last = LAST_CODE_TRY.get(id);
        if (last != null && now - last < CODE_COOLDOWN_MS) {
            sendProfile(p, "Slow down a little.");
            return;
        }
        LAST_CODE_TRY.put(id, now);

        String key = PromoCodes.normalise(raw);
        if (key.isEmpty()) {
            sendProfile(p, "Enter a code.");
            return;
        }
        PromoCodes.Code code = PromoCodes.find(key);
        CharacterProfile c = profile(id);
        if (code == null) {
            sendProfile(p, "That code is not valid.");
            return;
        }
        if (c.redeemedCodes.contains(code.code())) {
            sendProfile(p, "You already used that code.");
            return;
        }
        int used = data.codeUses.getOrDefault(code.code(), 0);
        if (code.maxUses() >= 0 && used >= code.maxUses()) {
            sendProfile(p, "That code has run out.");
            return;
        }
        c.redeemedCodes.add(code.code());
        data.codeUses.put(code.code(), used + 1);
        c.rerolls += code.rerolls();
        save();
        sendProfile(p, "Code accepted: +" + code.rerolls() + " ReRoll" + (code.rerolls() == 1 ? "" : "s") + ".");
    }

    // ================================================================== outgoing

    private static void sendProfile(ServerPlayer p, String notice) {
        CharacterProfile c = profile(p.getUUID());
        FriendlyByteBuf buf = PacketByteBufs.create();
        new MenuNet.MyProfile(c.created, c.firstName, c.surname, c.raceEnum().ordinal(), c.birthYear, c.rerolls,
                notice == null ? "" : notice).write(buf);
        ServerPlayNetworking.send(p, MenuNet.S2C_PROFILE, buf);
    }

    private static List<MenuNet.DirEntry> buildDirectory(UUID exclude) {
        List<MenuNet.DirEntry> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            if (id.equals(exclude)) continue;
            out.add(new MenuNet.DirEntry(id, p.getGameProfile().getName(), profile(id).fullName(), totalSeconds(id)));
        }
        return out;
    }

    private static void sendDirectory(ServerPlayer p, List<MenuNet.DirEntry> list) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        MenuNet.writeDirectory(buf, list);
        ServerPlayNetworking.send(p, MenuNet.S2C_DIRECTORY, buf);
    }

    /** Pushes the current roster to everyone (on join / leave / name change). */
    private static void broadcastDirectory(UUID exclude) {
        if (server == null) return;
        List<MenuNet.DirEntry> list = buildDirectory(exclude);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(exclude)) continue;
            sendDirectory(p, list);
        }
    }
}
