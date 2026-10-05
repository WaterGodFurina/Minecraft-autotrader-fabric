package dev.autotrader.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.autotrader.AutoTraderClient;
import dev.autotrader.gui.AutoTraderConfigScreen;

/**
 * Soft integration with Mod Menu: adds a "config" button to the mod's entry in the
 * mod list, opening the very same screen that the in-game hotkey opens.
 *
 * <p>This class is only ever reached through the {@code "modmenu"} entrypoint key, which
 * only Mod Menu reads. Without Mod Menu installed the class is never loaded, so the
 * missing classes are harmless. Mod Menu is therefore a <b>compile-only</b> dependency.</p>
 */
public final class AutoTraderModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new AutoTraderConfigScreen(parent, AutoTraderClient.config());
    }
}
