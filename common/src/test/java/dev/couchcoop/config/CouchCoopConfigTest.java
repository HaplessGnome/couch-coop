package dev.couchcoop.config;

import dev.couchcoop.config.CouchCoopConfig.Action;
import dev.couchcoop.input.GamepadInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CouchCoopConfigTest {
    @Test
    void crouchDefaultsToR3() {
        assertEquals(GamepadInput.R3, CouchCoopConfig.defaults().binding(Action.SNEAK));
    }

    @Test
    void oldConfigStillOnCircleMovesToR3() {
        CouchCoopConfig config = CouchCoopConfig.defaults();
        config.bindings.put(Action.SNEAK, GamepadInput.CIRCLE);
        CouchCoopConfig.migrate(config, 1);
        assertEquals(GamepadInput.R3, config.binding(Action.SNEAK));
    }

    @Test
    void triangleMovesStacksAndSquareSplitsThem() {
        CouchCoopConfig config = CouchCoopConfig.defaults();
        assertEquals(GamepadInput.TRIANGLE, config.binding(Action.MENU_QUICK_MOVE));
        assertEquals(GamepadInput.SQUARE, config.binding(Action.MENU_RIGHT_CLICK));
    }

    @Test
    void oldMenuButtonsAreSwapped() {
        CouchCoopConfig config = CouchCoopConfig.defaults();
        config.bindings.put(Action.MENU_QUICK_MOVE, GamepadInput.SQUARE);
        config.bindings.put(Action.MENU_RIGHT_CLICK, GamepadInput.TRIANGLE);
        CouchCoopConfig.migrate(config, 2);
        assertEquals(GamepadInput.TRIANGLE, config.binding(Action.MENU_QUICK_MOVE));
        assertEquals(GamepadInput.SQUARE, config.binding(Action.MENU_RIGHT_CLICK));
    }

    @Test
    void customMenuButtonsAreKept() {
        CouchCoopConfig config = CouchCoopConfig.defaults();
        config.bindings.put(Action.MENU_QUICK_MOVE, GamepadInput.SQUARE);
        config.bindings.put(Action.MENU_RIGHT_CLICK, GamepadInput.R1);
        CouchCoopConfig.migrate(config, 2);
        assertEquals(GamepadInput.SQUARE, config.binding(Action.MENU_QUICK_MOVE));
    }

    @Test
    void deliberateChoicesSurviveMigration() {
        CouchCoopConfig config = CouchCoopConfig.defaults();
        config.bindings.put(Action.SNEAK, GamepadInput.L3);
        CouchCoopConfig.migrate(config, 1);
        assertEquals(GamepadInput.L3, config.binding(Action.SNEAK));
    }

    @Test
    void currentConfigsAreNotMigratedAgain() {
        // Someone who picks Circle on purpose after upgrading keeps it.
        CouchCoopConfig config = CouchCoopConfig.defaults();
        config.bindings.put(Action.SNEAK, GamepadInput.CIRCLE);
        CouchCoopConfig.migrate(config, CouchCoopConfig.VERSION);
        assertEquals(GamepadInput.CIRCLE, config.binding(Action.SNEAK));
    }
}
