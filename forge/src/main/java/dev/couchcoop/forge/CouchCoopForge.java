package dev.couchcoop.forge;

import dev.couchcoop.Platform;
import dev.couchcoop.CouchCoop;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Mod(CouchCoop.MOD_ID)
public final class CouchCoopForge {
    public CouchCoopForge(FMLJavaModLoadingContext context) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        CouchCoop.init(new Platform() {
            @Override
            public String loaderName() {
                return "Forge";
            }

            @Override
            public Path configDir() {
                return FMLPaths.CONFIGDIR.get();
            }

            @Override
            public Optional<List<String>> fallbackLaunchCommand() {
                // Forge's bootstrap doesn't expose its original arguments.
                return Optional.empty();
            }
        });
    }
}
