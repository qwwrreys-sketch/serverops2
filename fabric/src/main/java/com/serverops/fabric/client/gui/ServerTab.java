package com.serverops.fabric.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.serverops.common.Perm;
import com.serverops.common.Season;
import com.serverops.fabric.client.ClientNet;
import com.serverops.fabric.client.ClientState;
import com.serverops.fabric.client.gui.Rows.Btn;
import com.serverops.fabric.net.Net;

/** Weather, time of day and seasons. */
public final class ServerTab extends Tab {
    public ServerTab(OpsScreen scr) {
        super(scr);
    }

    @Override
    public String title() {
        return "Server";
    }

    @Override
    public List<Row> build() {
        List<Row> r = new ArrayList<>();
        BooleanSupplier can = () -> ClientState.has(Perm.SERVER_WORLD);

        r.add(Rows.heading("Server", "Weather, time of day and seasons for the whole world."));

        r.add(Rows.section("Weather"));
        r.add(Rows.buttons(List.of(
                Btn.of("Clear", () -> ClientNet.world(Net.WORLD_WEATHER, 0)).when(can),
                Btn.of("Rain", () -> ClientNet.world(Net.WORLD_WEATHER, 1)).when(can),
                Btn.of("Thunder", () -> ClientNet.world(Net.WORLD_WEATHER, 2)).when(can)), true));

        r.add(Rows.section("Time of day"));
        r.add(Rows.buttons(List.of(
                Btn.of("Day", () -> ClientNet.world(Net.WORLD_TIME, 1000)).when(can),
                Btn.of("Noon", () -> ClientNet.world(Net.WORLD_TIME, 6000)).when(can),
                Btn.of("Sunset", () -> ClientNet.world(Net.WORLD_TIME, 12000)).when(can),
                Btn.of("Night", () -> ClientNet.world(Net.WORLD_TIME, 13000)).when(can),
                Btn.of("Midnight", () -> ClientNet.world(Net.WORLD_TIME, 18000)).when(can)), true));

        r.add(Rows.section("Season"));
        List<Btn> seasons = new ArrayList<>();
        for (Season s : Season.values()) {
            seasons.add(Btn.of(s.label, () -> ClientNet.world(Net.WORLD_SEASON, s.ordinal()))
                    .when(can).selected(() -> ClientState.season == s));
        }
        r.add(Rows.buttons(seasons, true));
        r.add(Rows.note("Seasons tint grass and leaves on every player's screen. They do not change gameplay."));

        if (!can.getAsBoolean()) {
            r.add(Rows.note("Your role does not have the \"World control\" permission, so these buttons are locked."));
        }
        return r;
    }
}
