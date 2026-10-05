package dev.autotrader;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The three keybinds of the mod. They are normal vanilla key mappings, so they also
 * show up in 选项 → 控制 and can be rebound there; on top of that they are editable
 * from the mod config screen and mirrored into config/autotrader.json.
 */
public final class KeyBindings {

    private static final KeyMapping.Category CATEGORY = createCategory();

    public static final KeyMapping ADD_TRADE = register("add_trade", InputConstants.KEY_R);
    public static final KeyMapping TOGGLE = register("toggle", InputConstants.KEY_G);
    public static final KeyMapping OPEN_CONFIG = register("open_config", InputConstants.KEY_P);
    /** turn the "glide the camera to a fixed angle when the trade screen opens" option on/off */
    public static final KeyMapping DRIFT_VIEW = register("drift_view", InputConstants.KEY_L);

    /** config-json key -> mapping, also drives the config screen rows */
    public static final Map<String, KeyMapping> ALL = new LinkedHashMap<>();

    static {
        ALL.put("addTrade", ADD_TRADE);
        ALL.put("toggle", TOGGLE);
        ALL.put("openConfig", OPEN_CONFIG);
        ALL.put("driftView", DRIFT_VIEW);
    }

    private KeyBindings() {
    }

    private static KeyMapping register(String id, int defaultKey) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key." + AutoTraderClient.MOD_ID + "." + id,
                InputConstants.Type.KEYSYM,
                defaultKey,
                CATEGORY));
    }

    private static KeyMapping.Category createCategory() {
        try {
            return KeyMapping.Category.register(Identifier.fromNamespaceAndPath(AutoTraderClient.MOD_ID, "main"));
        } catch (Throwable t) {
            // extremely defensive: never break the game because of a key category
            return KeyMapping.Category.MISC;
        }
    }
}
