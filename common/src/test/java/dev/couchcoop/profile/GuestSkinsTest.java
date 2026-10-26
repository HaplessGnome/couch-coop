package dev.couchcoop.profile;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuestSkinsTest {
    private static final int OPAQUE = 0xFF336699;

    /** A 64x64 skin with the right arm painted 4 (wide) or 3 (slim) pixels across each face. */
    private static BufferedImage skin(boolean slim) {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        int armFaces = slim ? 14 : 16; // front, side, back and side faces laid out from x=40
        for (int x = 40; x < 40 + armFaces; x++) {
            for (int y = 20; y < 32; y++) {
                image.setRGB(x, y, OPAQUE);
            }
        }
        return image;
    }

    @Test
    void slimArmsLeaveTheOuterColumnEmpty() {
        assertTrue(GuestSkins.isSlim(skin(true)));
    }

    @Test
    void wideArmsAreDetected() {
        assertFalse(GuestSkins.isSlim(skin(false)));
    }

    @Test
    void legacySkinsAreWide() {
        assertFalse(GuestSkins.isSlim(new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB)));
        assertFalse(GuestSkins.isSlim(null));
    }
}
