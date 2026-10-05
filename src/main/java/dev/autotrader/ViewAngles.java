package dev.autotrader;

/**
 * Turns the two angles of the F3 "Facing" line into words, so the config screen can show
 * what a number actually means.
 *
 * <p>yaw (first number): 0 = 正南/south, 90 = 正西/west, 180 = 正北/north, -90 = 正东/east<br>
 * pitch (second number): 0 = level, 90 = straight down, -90 = straight up</p>
 */
public final class ViewAngles {

    private ViewAngles() {
    }

    /** F3 prints yaw in -180..180; wrap anything else into that range. */
    public static double normaliseYaw(double yaw) {
        double y = yaw % 360.0;
        if (y >= 180.0) {
            y -= 360.0;
        }
        if (y < -180.0) {
            y += 360.0;
        }
        return y;
    }

    public static String yawKey(double yaw) {
        double y = normaliseYaw(yaw);
        if (y >= -45.0 && y < 45.0) {
            return "autotrader.dir.south";
        }
        if (y >= 45.0 && y < 135.0) {
            return "autotrader.dir.west";
        }
        if (y >= -135.0 && y < -45.0) {
            return "autotrader.dir.east";
        }
        return "autotrader.dir.north";
    }

    public static String pitchKey(double pitch) {
        if (pitch >= 45.0) {
            return "autotrader.pitch.down";
        }
        if (pitch <= -45.0) {
            return "autotrader.pitch.up";
        }
        return "autotrader.pitch.level";
    }

    /** e.g. {@code 正南 平视 (0.0 / 0.0)} */
    public static net.minecraft.network.chat.Component describe(double yaw, double pitch) {
        return net.minecraft.network.chat.Component.empty()
                .append(net.minecraft.network.chat.Component.translatable(yawKey(yaw)))
                .append(" ")
                .append(net.minecraft.network.chat.Component.translatable(pitchKey(pitch)))
                .append(" (").append(trim(yaw)).append(" / ").append(trim(pitch)).append(")");
    }

    public static String trim(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
