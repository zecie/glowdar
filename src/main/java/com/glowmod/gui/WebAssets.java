package com.glowmod.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class WebAssets {

    private static final List<String> ASSETS = List.of(
            "index.html",
            "styles.css",
            "app.js"
    );

    private static Path extractedDir;

    private WebAssets() {}

    public static synchronized Path ensureExtracted() {
        if (extractedDir != null) return extractedDir;

        Path target = FabricLoader.getInstance().getGameDir().resolve("glowdar").resolve("web");
        try {
            Files.createDirectories(target);
        } catch (IOException e) {
            throw new RuntimeException("glowdar: failed to create web asset dir " + target, e);
        }

        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer("glowdar");
        if (mod.isEmpty()) throw new RuntimeException("glowdar: mod container missing");

        for (String name : ASSETS) {
            Optional<Path> src = mod.get().findPath("assets/glowdar/web/" + name);
            if (src.isEmpty()) {
                throw new RuntimeException("glowdar: missing bundled asset assets/glowdar/web/" + name);
            }
            try {
                Files.copy(src.get(), target.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("glowdar: failed copying asset " + name, e);
            }
        }

        extractedDir = target;
        return target;
    }

    public static String indexUrl() {
        Path index = ensureExtracted().resolve("index.html").toAbsolutePath();
        return index.toUri().toString();
    }
}
