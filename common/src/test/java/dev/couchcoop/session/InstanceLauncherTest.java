package dev.couchcoop.session;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
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

    @Test
    void guestHeapIsCappedAndTheLogConfigReplaced() {
        Path config = Path.of("session", "guest-log4j2.xml");
        List<String> guest = InstanceLauncher.guestJvmArguments(
            List.of("-Xms8G", "-Xmx6144M", "-Dlog4j.configurationFile=client.xml", "-XX:+UseG1GC"), 3072, config);
        assertEquals(List.of(
            "-Xms3072M", "-XX:+UseG1GC", "-Xmx3072M", "-Dlog4j.configurationFile=" + config.toAbsolutePath()
        ), guest);
    }

    @Test
    void guestNeverGetsMoreThanTheHost() {
        assertEquals(List.of("-Xmx2048M"), InstanceLauncher.guestJvmArguments(List.of("-Xmx2G"), 3072, null));
    }

    @Test
    void zeroKeepsTheHostsHeap() {
        assertEquals(List.of("-Xmx6144M"), InstanceLauncher.guestJvmArguments(List.of("-Xmx6G"), 0, null));
    }

    @Test
    void hostWithoutAHeapFlagStillGetsACap() {
        assertEquals(List.of("-Xmx3072M"), InstanceLauncher.guestJvmArguments(List.of(), 3072, null));
    }

    @Test
    void memorySizesParse() {
        assertEquals(6144, InstanceLauncher.parseMegabytes("6G"));
        assertEquals(6144, InstanceLauncher.parseMegabytes("6144m"));
        assertEquals(512, InstanceLauncher.parseMegabytes("524288k"));
        assertEquals(1, InstanceLauncher.parseMegabytes("1048576"));
        assertEquals(-1, InstanceLauncher.parseMegabytes("lots"));
    }
}
