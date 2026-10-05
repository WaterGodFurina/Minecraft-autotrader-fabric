package dev.autotrader;

import net.minecraft.client.Minecraft;

/**
 * The "glide to a fixed angle" camera move: when the villager gui opens the view is eased
 * from wherever the player was looking to the configured yaw/pitch, instead of jumping there.
 *
 * <p>Angles use the same numbers the F3 "Facing" line shows - yaw: 0 = south, 90 = west,
 * 180 = north, -90 = east; pitch: 90 = straight down, -90 = straight up.</p>
 */
public final class ViewDrift {

    private static boolean active;
    private static float startYaw;
    private static float startPitch;
    private static float targetYaw;
    private static float targetPitch;
    private static int totalTicks;
    private static int elapsedTicks;

    private ViewDrift() {
    }

    public static boolean isActive() {
        return active;
    }

    /** Begins a glide from the current rotation to {@code yaw}/{@code pitch}. */
    public static void start(double yaw, double pitch, int ticks) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        startYaw = client.player.getYRot();
        startPitch = client.player.getXRot();
        targetYaw = (float) yaw;
        targetPitch = (float) pitch;
        totalTicks = Math.max(1, ticks);
        elapsedTicks = 0;
        active = true;
        if (totalTicks == 1) {
            // no animation requested -> land on it right away
            apply(client, targetYaw, targetPitch);
            active = false;
        }
    }

    public static void cancel() {
        active = false;
    }

    /** Advance the glide; call once per client tick. */
    public static void tick(Minecraft client) {
        if (!active) {
            return;
        }
        if (client.player == null) {
            active = false;
            return;
        }
        elapsedTicks++;
        float progress = Math.min(1.0f, (float) elapsedTicks / totalTicks);
        float eased = smoothStep(progress);
        // yaw takes the short way round, so 170 -> -170 only travels 20 degrees
        float yaw = startYaw + shortestYawDelta(startYaw, targetYaw) * eased;
        float pitch = startPitch + (targetPitch - startPitch) * eased;
        if (progress >= 1.0f) {
            yaw = targetYaw;
            pitch = targetPitch;
            active = false;
        }
        apply(client, yaw, pitch);
    }

    private static void apply(Minecraft client, float yaw, float pitch) {
        if (client.player == null) {
            return;
        }
        client.player.setYRot(yaw);
        client.player.yRotO = yaw;          // also the interpolation field, so no wobble
        client.player.setYHeadRot(yaw);
        client.player.setXRot(pitch);
        client.player.xRotO = pitch;
    }

    private static float smoothStep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    private static float shortestYawDelta(float from, float to) {
        float delta = (to - from) % 360.0f;
        if (delta > 180.0f) {
            delta -= 360.0f;
        }
        if (delta < -180.0f) {
            delta += 360.0f;
        }
        return delta;
    }
}
