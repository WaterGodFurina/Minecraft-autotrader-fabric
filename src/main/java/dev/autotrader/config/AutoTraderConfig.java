package dev.autotrader.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.platform.InputConstants;
import dev.autotrader.KeyBindings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the mod can be configured with lives here and is persisted to
 * {@code config/autotrader.json}.
 */
public final class AutoTraderConfig {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            // tolerate the little mistakes people make when hand editing json
            // (trailing commas, comments, single quotes, ...)
            .setLenient()
            .create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("autotrader.json");

    /** set when the file on disk existed but could not be read, so the screen can say so */
    private static boolean lastLoadWasBroken;

    public static boolean lastLoadWasBroken() {
        return lastLoadWasBroken;
    }

    public static Path path() {
        return PATH;
    }

    // ---- general ----
    /** master switch; the toggle hotkey flips this at runtime */
    public boolean enabled = true;
    /**
     * What happens to the goods the villager hands over.
     *
     * <p><b>On</b> (default): they are thrown on the ground - the result slot is
     * {@code THROW}n out whole-stack, and every bag stack of a traded product is swept out
     * the same way, so products never pile up. <b>Off</b>: they are kept - the result slot
     * is shift-clicked ({@code QUICK_MOVE}), which moves the product straight into the bag.</p>
     *
     * <p>Neither mode ever picks something up onto the cursor. That is the whole point: on
     * a phone or tablet the cursor belongs to the player, and the mod must never grab it.
     * The old "keep" mode used to take the product with {@code PICKUP} and put it down in a
     * slot, which is exactly what made dropping things impossible on mobile - that path is
     * gone for good.</p>
     */
    public boolean discardProducts = true;
    /** delay between two trades, in milliseconds */
    public int tradeIntervalMs = 500;
    /** rotate the player view the moment the villager trade screen opens */
    public boolean rotateViewOnOpen = false;
    /** how many degrees to add to the yaw, may be negative, -180..180 */
    public double viewYawDegrees = 0.0;

    /**
     * When a wanted trade can no longer be run because the villager has raised its price above
     * base (or the price has ballooned to {@code 2x} base), close the trade screen and right-click
     * the villager again so the server resends its authoritative price list. Works on servers that
     * drop the special price when the screen is closed (Leaf-like); on others it is a no-op.
     * @see dev.autotrader.AutoTradeController
     */
    public boolean refreshPriceOnRaise = true;

    // ---- camera drift when the trade screen opens ----
    /** glide the camera to the absolute angles below as soon as the villager gui opens */
    public boolean driftViewOnOpen = false;
    /** absolute yaw, the first number of the F3 "Facing" line: 0 = south, 90 = west, 180 = north, -90 = east */
    public double driftViewYaw = 0.0;
    /** absolute pitch, the second number: 0 = level, 90 = straight down, -90 = straight up */
    public double driftViewPitch = 0.0;
    /** how many ticks the glide takes (20 ticks = 1 second) */
    public int driftViewTicks = 10;

    // ---- auto-open the nearest villager's trade screen right after joining ----
    /**
     * For ten seconds after joining a world, right-click the closest reachable villager so its
     * trade screen opens on its own. If no villager is around in that window, nothing happens.
     * See {@link dev.autotrader.VillagerAutoOpen}.
     */
    public boolean autoOpenVillagerGui = false;

    // ---- hotkeys (mirrored into options.txt by vanilla) ----
    public Map<String, String> hotkeys = new LinkedHashMap<>();

    // ---- auto trade list ----
    public List<TradeEntry> trades = new ArrayList<>();

    public static AutoTraderConfig load() {
        lastLoadWasBroken = false;
        boolean existed = Files.exists(PATH);
        AutoTraderConfig config = null;
        if (existed) {
            try {
                String json = Files.readString(PATH, StandardCharsets.UTF_8);
                config = GSON.fromJson(json, AutoTraderConfig.class);
                if (config == null) {
                    // empty file, or the literal "null"
                    throw new JsonSyntaxException("file is empty");
                }
            } catch (IOException | RuntimeException e) {
                lastLoadWasBroken = true;
                backUpUnreadableFile();
                dev.autotrader.AutoTraderClient.LOGGER.error(
                        "[autotrader] {} is not valid json - a copy was kept next to it and defaults are used",
                        PATH, e);
            }
        }
        if (config == null) {
            config = new AutoTraderConfig();
        }
        config.sanitize();
        // Only (re)write the file when there was nothing readable on disk. Rewriting it on
        // every single launch is what used to make hand edits look impossible: the file was
        // silently replaced at startup, and one typo reset everything to defaults for good.
        if (!existed || lastLoadWasBroken) {
            config.save();
        }
        dev.autotrader.AutoTraderClient.LOGGER.info(
                "[autotrader] config {} -> enabled={}, discard={}, interval={}ms, rotate={} ({} deg), autoOpenGui={}, refreshPriceOnRaise={}, {} hotkeys, {} trades",
                PATH, config.enabled, config.discardProducts, config.tradeIntervalMs,
                config.rotateViewOnOpen, config.viewYawDegrees, config.autoOpenVillagerGui,
                config.refreshPriceOnRaise, config.hotkeys.size(), config.trades.size());
        for (TradeEntry entry : config.trades) {
            dev.autotrader.AutoTraderClient.LOGGER.info(
                    "[autotrader]   trade entry: pay={} x{}, pay2={} x{}, get={} x{}",
                    entry.costA, entry.countA, entry.costB, entry.countB, entry.result, entry.resultCount);
        }
        return config;
    }

    /** Moves an unreadable file aside instead of deleting the player's work. */
    private static void backUpUnreadableFile() {
        try {
            Path backup = PATH.resolveSibling(PATH.getFileName() + ".broken");
            Files.move(PATH, backup, StandardCopyOption.REPLACE_EXISTING);
            dev.autotrader.AutoTraderClient.LOGGER.error("[autotrader] unreadable config kept at {}", backup);
        } catch (IOException e) {
            dev.autotrader.AutoTraderClient.LOGGER.error("[autotrader] could not back up the unreadable config", e);
        }
    }

    private void sanitize() {
        if (tradeIntervalMs < 0) {
            tradeIntervalMs = 0;
        }
        if (tradeIntervalMs > 60_000) {
            tradeIntervalMs = 60_000;
        }
        if (viewYawDegrees < -180) {
            viewYawDegrees = -180;
        }
        if (viewYawDegrees > 180) {
            viewYawDegrees = 180;
        }
        if (driftViewYaw < -180) {
            driftViewYaw = -180;
        }
        if (driftViewYaw > 180) {
            driftViewYaw = 180;
        }
        if (driftViewPitch < -90) {
            driftViewPitch = -90;
        }
        if (driftViewPitch > 90) {
            driftViewPitch = 90;
        }
        if (driftViewTicks < 0) {
            driftViewTicks = 0;
        }
        if (driftViewTicks > 200) {
            driftViewTicks = 200;
        }
        if (hotkeys == null) {
            hotkeys = new LinkedHashMap<>();
        }
        if (trades == null) {
            trades = new ArrayList<>();
        }
        trades.removeIf(java.util.Objects::isNull);
    }

    public void save() {
        try {
            Path parent = PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // write next to the target and swap it in, so a crash mid-write can never
            // leave a truncated (unreadable) json behind
            Path tmp = PATH.resolveSibling(PATH.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            dev.autotrader.AutoTraderClient.LOGGER.debug("[autotrader] saved {}", PATH);
        } catch (IOException e) {
            dev.autotrader.AutoTraderClient.LOGGER.warn("[autotrader] could not write {}", PATH, e);
        }
    }

    public int intervalTicks() {
        return Math.max(1, Math.round(tradeIntervalMs / 50.0f));
    }

    /** Called once shortly after startup: bind whatever the json says. */
    public void applyHotkeysToKeyMappings() {
        boolean changed = false;
        for (Map.Entry<String, KeyMapping> entry : KeyBindings.ALL.entrySet()) {
            String wanted = hotkeys.get(entry.getKey());
            if (wanted == null || wanted.isBlank()) {
                continue;
            }
            KeyMapping mapping = entry.getValue();
            // options.txt (where vanilla also keeps key binds) already persisted whatever the
            // player picked last time. Only let the json win while the live mapping is still
            // sitting on its vanilla default - that is the case where nobody expressed an
            // opinion yet, e.g. the player edited autotrader.json by hand. Without this the
            // json kept resetting binds that were changed in 选项 -> 控制.
            if (!mapping.isDefault()) {
                continue;
            }
            InputConstants.Key key;
            try {
                key = InputConstants.getKey(wanted);
            } catch (RuntimeException e) {
                continue;
            }
            if (key == null || key == InputConstants.UNKNOWN) {
                continue;
            }
            if (!key.equals(mapping.getDefaultKey())) {
                mapping.setKey(key);
                changed = true;
            }
        }
        if (changed) {
            if (Minecraft.getInstance().options != null) {
                Minecraft.getInstance().options.save();
            }
            captureHotkeysFromKeyMappings();
            save();
        }
    }

    /** Mirror the currently bound keys back into the json. */
    public void captureHotkeysFromKeyMappings() {
        for (Map.Entry<String, KeyMapping> entry : KeyBindings.ALL.entrySet()) {
            hotkeys.put(entry.getKey(), entry.getValue().saveString());
        }
    }
}
