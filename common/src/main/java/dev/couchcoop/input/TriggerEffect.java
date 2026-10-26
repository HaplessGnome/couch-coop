package dev.couchcoop.input;

/**
 * DualSense adaptive trigger effects, as the raw effect report SDL's PS5 driver accepts through
 * {@code SDL_SendGamepadEffect}. The layout is SDL's {@code DS5EffectsState_t}; only the trigger
 * fields are flagged as changed, so rumble, lights and audio are left alone.
 */
public record TriggerEffect(byte[] bytes) {
    /** Size of SDL's DS5EffectsState_t. */
    public static final int REPORT_SIZE = 47;
    private static final int ENABLE_BITS_1 = 0;
    private static final int RIGHT_TRIGGER = 10;
    private static final int LEFT_TRIGGER = 21;
    private static final int TRIGGER_BYTES = 11;
    private static final byte ENABLE_RIGHT_TRIGGER = 0x04;
    private static final byte ENABLE_LEFT_TRIGGER = 0x08;

    private static final byte MODE_OFF = 0x05;
    private static final byte MODE_RESISTANCE = 0x01;

    /** No effect: the trigger moves freely. */
    public static TriggerEffect off() {
        return new TriggerEffect(new byte[] {MODE_OFF});
    }

    /**
     * Resistance from {@code start} (0 = top of the pull, 1 = fully pressed) onwards, with
     * {@code strength} from 0 to 1.
     */
    public static TriggerEffect resistance(double start, double strength) {
        return new TriggerEffect(new byte[] {MODE_RESISTANCE, toByte(start), toByte(strength)});
    }

    private static byte toByte(double fraction) {
        return (byte) Math.round(Math.clamp(fraction, 0, 1) * 255);
    }

    /** The full effect report setting both triggers. */
    public static byte[] report(TriggerEffect left, TriggerEffect right) {
        byte[] report = new byte[REPORT_SIZE];
        report[ENABLE_BITS_1] = ENABLE_RIGHT_TRIGGER | ENABLE_LEFT_TRIGGER;
        System.arraycopy(right.bytes, 0, report, RIGHT_TRIGGER, Math.min(right.bytes.length, TRIGGER_BYTES));
        System.arraycopy(left.bytes, 0, report, LEFT_TRIGGER, Math.min(left.bytes.length, TRIGGER_BYTES));
        return report;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TriggerEffect effect && java.util.Arrays.equals(bytes, effect.bytes);
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(bytes);
    }
}
