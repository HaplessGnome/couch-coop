package dev.couchcoop.fabric;

import dev.couchcoop.Platform;
import dev.couchcoop.CouchCoop;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class CouchCoopFabric implements ClientModInitializer {
    private static final String KNOT_CLIENT = "net.fabricmc.loader.impl.launch.knot.KnotClient";

    @Override
    public void onInitializeClient() {
        CouchCoop.init(new Platform() {
            @Override
            public String loaderName() {
                return "Fabric";
            }

            @Override
            public Path configDir() {
                return FabricLoader.getInstance().getConfigDir();
            }

            @Override
            public Optional<List<String>> fallbackLaunchCommand() {
                List<String> command = new ArrayList<>();
                command.add(KNOT_CLIENT);
                command.addAll(Arrays.asList(FabricLoader.getInstance().getLaunchArguments(false)));
                return Optional.of(command);
            }
        });
    }
}
