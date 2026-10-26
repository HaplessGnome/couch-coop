package dev.couchcoop.session;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceLauncherTest {
    @Test
    void modrinthWrapperIsReplacedByTheRealMainClass() {
        // As the Modrinth App launches a Forge instance (paths shortened).
        String raw = "com.modrinth.theseus.MinecraftLaunch net.minecraftforge.bootstrap.ForgeBootstrap"
            + " --version 26.3 --gameDir C:/Users/Oleho/AppData/Roaming/ModrinthApp/profiles/Forge 26.3"
            + " --launchTarget forge_client";
        assertEquals(List.of(
            "net.minecraftforge.bootstrap.ForgeBootstrap",
            "--version", "26.3",
            "--gameDir", "C:/Users/Oleho/AppData/Roaming/ModrinthApp/profiles/Forge 26.3",
            "--launchTarget", "forge_client"
        ), InstanceLauncher.parseCommand(raw));
    }

    @Test
    void directLaunchKeepsItsMainClass() {
        String raw = "net.fabricmc.loader.impl.launch.knot.KnotClient --username Dev --gameDir C:/games/mc";
        assertEquals(List.of(
            "net.fabricmc.loader.impl.launch.knot.KnotClient",
            "--username", "Dev",
            "--gameDir", "C:/games/mc"
        ), InstanceLauncher.parseCommand(raw));
    }

    @Test
    void flagsWithoutValuesSurvive() {
        assertEquals(List.of("Main", "--demo", "--width", "854"), InstanceLauncher.parseCommand("Main --demo --width 854"));
    }

    @Test
    void wrapperOnlyJvmFlagsAreRecognised() {
        assertTrue(InstanceLauncher.isWrapperJvmArg("-Dmodrinth.internal.ipc.port=53835"));
        assertTrue(InstanceLauncher.isWrapperJvmArg("-javaagent:C:/Temp/.tmpdUlcje/theseus.jar"));
        assertFalse(InstanceLauncher.isWrapperJvmArg("-Xmx6144M"));
        assertFalse(InstanceLauncher.isWrapperJvmArg("-Dminecraft.launcher.brand=theseus"));
    }
}
