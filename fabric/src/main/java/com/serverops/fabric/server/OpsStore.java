package com.serverops.fabric.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.serverops.common.OpsData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Loads / saves {@link OpsData} as JSON inside the world folder. */
final class OpsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private OpsStore() {}

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("serverops").resolve("data.json");
    }

    static OpsData load(MinecraftServer server) {
        Path f = file(server);
        try {
            if (Files.isRegularFile(f)) {
                OpsData d = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), OpsData.class);
                if (d != null) return d.sanitize();
            }
        } catch (Exception e) {
            System.err.println("[ServerOps] Could not read " + f + ": " + e);
        }
        return OpsData.defaults();
    }

    static void save(MinecraftServer server, OpsData data) {
        Path f = file(server);
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[ServerOps] Could not save " + f + ": " + e);
        }
    }
}
