package dev.couchcoop.input;

import dev.couchcoop.CouchCoop;
import dev.couchcoop.config.CouchCoopConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

/**
 * Controller feedback from gameplay: rumble when the player is hurt or breaks a block, and on a
 * DualSense, adaptive triggers. L2 (use) stiffens as a bow, crossbow or trident is drawn; R2
 * (attack) resists while the attack is recharging. Xbox pads get a light impulse-trigger buzz
 * while drawing instead.
 */
public final class Haptics {
    /** Ticks a trident needs before it can be thrown. */
    private static final int TRIDENT_CHARGE_TICKS = 10;

    private float lastHealth = -1;

    /** Called once per client tick for a window that has a controller. */
    public void tick(Minecraft minecraft, Gamepad pad) {
        CouchCoopConfig config = CouchCoop.config();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gui.screen() != null) {
            pad.setTriggers(TriggerEffect.off(), TriggerEffect.off());
            lastHealth = player == null ? -1 : player.getHealth();
            return;
        }

        float health = player.getHealth();
        if (config.rumble && lastHealth >= 0 && health < lastHealth) {
            float damage = lastHealth - health;
            pad.rumble((float) (Math.min(1f, 0.3f + damage / 10f) * config.rumbleStrength), Math.min(500, 150 + (int) (damage * 30)));
        }
        lastHealth = health;

        double draw = drawProgress(player);
        if (config.adaptiveTriggers && pad.hasAdaptiveTriggers()) {
            TriggerEffect left = draw >= 0 ? TriggerEffect.resistance(0, 0.15 + 0.75 * draw) : TriggerEffect.off();
            double attack = config.attackTriggerStrength;
            TriggerEffect right = attack > 0 && player.getAttackStrengthScale(0) < 0.95f ? TriggerEffect.resistance(0.1, attack) : TriggerEffect.off();
            pad.setTriggers(left, right);
        } else if (config.rumble && draw >= 0 && !pad.hasAdaptiveTriggers()) {
            pad.rumbleTriggers((float) (0.1 + 0.3 * draw) * (float) config.rumbleStrength, 0, 80);
        }
    }

    /** A short tick on breaking a block. */
    public void blockBroken(Gamepad pad) {
        if (CouchCoop.config().rumble) {
            pad.rumble((float) (0.2 * CouchCoop.config().rumbleStrength), 60);
        }
    }

    /** Frees the triggers, so they don't stay stiff after the game closes or the pad changes hands. */
    public void reset(Gamepad pad) {
        pad.setTriggers(TriggerEffect.off(), TriggerEffect.off());
        lastHealth = -1;
    }

    /** How far a ranged weapon is drawn, 0 to 1, or -1 when not drawing one. */
    private static double drawProgress(LocalPlayer player) {
        if (!player.isUsingItem()) {
            return -1;
        }
        ItemStack item = player.getUseItem();
        int ticks = player.getTicksUsingItem();
        if (item.getItem() instanceof BowItem) {
            return BowItem.getPowerForTime(ticks);
        }
        if (item.getItem() instanceof CrossbowItem) {
            return Math.min(1.0, ticks / (double) CrossbowItem.getChargeDuration(item, player));
        }
        if (item.getItem() instanceof TridentItem) {
            return Math.min(1.0, ticks / (double) TRIDENT_CHARGE_TICKS);
        }
        return -1;
    }
}
