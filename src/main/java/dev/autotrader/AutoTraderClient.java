package dev.autotrader;

import dev.autotrader.config.AutoTraderConfig;
import dev.autotrader.gui.AutoTraderConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AutoTraderClient implements ClientModInitializer {

    public static final String MOD_ID = "autotrader";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static AutoTraderConfig config = new AutoTraderConfig();
    private static boolean hotkeysApplied;

    public static AutoTraderConfig config() {
        return config;
    }

    @Override
    public void onInitializeClient() {
        // Key mappings have to be registered before the game options are built, so touch the
        // KeyBindings class right here - its static initialiser does the registration.
        LOGGER.info("[autotrader] registering {} key mappings", KeyBindings.ALL.size());

        config = AutoTraderConfig.load();
        LOGGER.info("[autotrader] loaded, {} auto trade entries, enabled={}, interval={}ms, rotate={} ({} deg), drift={} ({} / {} in {} ticks)",
                config.trades.size(), config.enabled, config.tradeIntervalMs,
                config.rotateViewOnOpen, config.viewYawDegrees,
                config.driftViewOnOpen, config.driftViewYaw, config.driftViewPitch, config.driftViewTicks);
        verifyMixin();

        ClientTickEvents.END_CLIENT_TICK.register(AutoTraderClient::onEndTick);

        // "auto open villager gui": the ten second window starts the moment a world is joined
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> VillagerAutoOpen.onJoin());

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof MerchantScreen merchantScreen)) {
                return;
            }
            // rule 4: optionally make the view jump when the trade screen opens
            if (config.rotateViewOnOpen && config.viewYawDegrees != 0.0 && client.player != null) {
                client.player.setYRot(client.player.getYRot() + (float) config.viewYawDegrees);
                client.player.setYHeadRot(client.player.getYRot());
            }
            // and/or glide the camera to a fixed angle, over a few ticks, so it looks smooth
            if (config.driftViewOnOpen) {
                ViewDrift.start(config.driftViewYaw, config.driftViewPitch, config.driftViewTicks);
            }
            // rule 2: the "add this trade" key must work while the screen has the keyboard
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
                if (KeyBindings.ADD_TRADE.matches(event)) {
                    AutoTradeController.addSelectedTrade(merchantScreen, client);
                    return false;
                }
                return true;
            });
        });
    }

    private static void onEndTick(Minecraft client) {
        if (!hotkeysApplied) {
            hotkeysApplied = true;
            try {
                config.applyHotkeysToKeyMappings();
            } catch (Throwable t) {
                LOGGER.warn("[autotrader] could not apply hotkeys from the config file", t);
            }
            if (AutoTraderConfig.lastLoadWasBroken()) {
                notifyPlayer(Component.translatable("autotrader.msg.config_repaired"));
                notifyPlayer(Component.literal("[autotrader] " + AutoTraderConfig.path()));
            }
        }
        if (client.player == null) {
            return;
        }
        VillagerAutoOpen.tick(client);
        while (KeyBindings.OPEN_CONFIG.consumeClick()) {
            client.gui.setScreen(new AutoTraderConfigScreen(client.gui.screen(), config));
        }
        while (KeyBindings.TOGGLE.consumeClick()) {
            config.enabled = !config.enabled;
            config.save();
            notifyPlayer(Component.translatable(config.enabled
                    ? "autotrader.msg.enabled"
                    : "autotrader.msg.disabled"));
        }
        while (KeyBindings.DRIFT_VIEW.consumeClick()) {
            config.driftViewOnOpen = !config.driftViewOnOpen;
            config.save();
            notifyPlayer(Component.translatable(config.driftViewOnOpen
                    ? "autotrader.msg.drift_on"
                    : "autotrader.msg.drift_off"));
        }
        // bulk trading is not a key bind any more: it is simply how AutoTradeController
        // trades (see the class comment there), so there is nothing to toggle here
        // the glide has to run while a screen has the keyboard, so it lives here
        ViewDrift.tick(client);
        AutoTradeController.tick(client);
    }

    public static void notifyPlayer(Component message) {        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(message);
        }
    }

    public static void notifyPlayerOverlay(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendOverlayMessage(message);
        }
    }

    /**
     * Loads the two mixin targets early (without running their static initialisers) so that a
     * broken injector shows up in the log at startup instead of crashing the first time someone
     * opens a villager.
     *
     * <p>Mixin rewrites a class while it is being loaded, so simply forcing the class to load
     * through the transforming loader is enough to surface a bad {@code @Inject} target as a
     * {@code Throwable} we can log here.</p>
     */
    private static void verifyMixin() {
        try {
            Class<?> screenClass = Class.forName(
                    "net.minecraft.client.gui.screens.inventory.MerchantScreen",
                    false,
                    AutoTraderClient.class.getClassLoader());
            boolean ok = dev.autotrader.mixin.MerchantScreenAccessor.class.isAssignableFrom(screenClass);
            if (ok) {
                LOGGER.info("[autotrader] MerchantScreen mixin applied");
            } else {
                LOGGER.error("[autotrader] MerchantScreen mixin NOT applied - auto trading will not work");
            }
        } catch (Throwable t) {
            LOGGER.error("[autotrader] could not verify MerchantScreen mixin", t);
        }
        try {
            // also drives the MultiPlayerGameMode mixin (villager tracking for the price refresh)
            // through the loader, so a bad injection target is logged now rather than later
            Class.forName("net.minecraft.client.multiplayer.MultiPlayerGameMode",
                    false, AutoTraderClient.class.getClassLoader());
            LOGGER.info("[autotrader] MultiPlayerGameMode mixin loaded");
        } catch (Throwable t) {
            LOGGER.error("[autotrader] could not verify MultiPlayerGameMode mixin", t);
        }
    }
}
