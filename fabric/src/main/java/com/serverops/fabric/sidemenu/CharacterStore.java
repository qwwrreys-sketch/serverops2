package com.serverops.fabric.sidemenu;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.serverops.common.menu.CharacterData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Loads / saves {@link CharacterData} as JSON: {@code <world>/serverops/characters.json} (next to data.json). */
final class CharacterStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CharacterStore() {}

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("serverops").resolve("characters.json");
    }

    static CharacterData load(MinecraftServer server) {
        Path f = file(server);
        try {
            if (Files.isRegularFile(f)) {
                CharacterData d = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), CharacterData.class);
                if (d != null) return d.sanitize();
            }
        } catch (Exception e) {
            System.err.println("[ServerOps] Could not read " + f + ": " + e);
        }
        return new CharacterData();
    }

    static void save(MinecraftServer server, CharacterData data) {
        Path f = file(server);
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[ServerOps] Could not save " + f + ": " + e);
        }
    }
}
