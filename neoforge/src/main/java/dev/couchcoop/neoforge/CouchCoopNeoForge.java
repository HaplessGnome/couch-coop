package dev.couchcoop.neoforge;

import dev.couchcoop.CouchCoop;
import dev.couchcoop.Platform;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Client only: {@code dist = CLIENT} keeps NeoForge from loading this on a dedicated server. */
@Mod(value = CouchCoop.MOD_ID, dist = Dist.CLIENT)
public final class CouchCoopNeoForge {
    public CouchCoopNeoForge() {
        CouchCoop.init(new Platform() {
            @Override
            public String loaderName() {
                return "NeoForge";
            }

            @Override
            public Path configDir() {
                return FMLPaths.CONFIGDIR.get();
            }

            @Override
            public Optional<List<String>> fallbackLaunchCommand() {
                // Like Forge, NeoForge's bootstrap doesn't expose its original arguments.
                return Optional.empty();
            }
        });
    }
}
