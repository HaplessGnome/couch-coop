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
