package dev.couchcoop.session;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionCoordinatorTest {
    @Test
    void sameGameFolderSharesASession() {
        String a = SessionCoordinator.sessionName(Path.of("profiles", "Fabric 26.3"));
        String b = SessionCoordinator.sessionName(Path.of("profiles", "x", "..", "fabric 26.3"));
        assertEquals(a, b);
    }

    @Test
    void differentGameFoldersGetDifferentSessions() {
        assertNotEquals(
            SessionCoordinator.sessionName(Path.of("profiles", "Fabric 26.3")),
            SessionCoordinator.sessionName(Path.of("profiles", "Forge 26.3")));
    }

    @Test
    void nameIsAShortFolderName() {
        assertTrue(SessionCoordinator.sessionName(Path.of("run")).matches("session-[0-9a-f]{8}"));
    }
}
