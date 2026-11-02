package dev.cogwit.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cogwit.CogwitMod;
import dev.cogwit.ClientConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.KeyModifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One keyboard layout for the whole modpack, so no two in-world actions share a key. Applied once on first launch
 * (and again from Cogwit's settings); bindings of mods that aren't installed are skipped. GUI-only keys (JEI,
 * Ponder, Jade) and vanilla movement are left alone. Values: a key name, optionally {@code +SHIFT}, {@code +CONTROL}
 * or {@code +ALT}; an empty string unbinds.
 */
public final class PackKeyLayout {
    public static final Map<String, String> LAYOUT = new LinkedHashMap<>();

    static {
        // Cogwit: hold G for the wheel, K menu, Y answer, Z dash, X gravity well; the rest live in the wheel
        LAYOUT.put("key.cogwit.wheel", "key.keyboard.g");
        LAYOUT.put("key.cogwit.roadmap", "key.keyboard.k");
        LAYOUT.put("key.cogwit.yes", "key.keyboard.y");
        LAYOUT.put("key.cogwit.blink", "key.keyboard.z");
        LAYOUT.put("key.cogwit.gravity", "key.keyboard.x");
        LAYOUT.put("key.cogwit.visor", "key.keyboard.h");
        for (String unbound : new String[] {"think", "hud", "crew", "scan", "mark", "agent", "codex"}) {
            LAYOUT.put("key.cogwit." + unbound, "");
        }
        // Iron's Spells keeps its defaults: R wheel, V cast, Left Alt + scroll
        LAYOUT.put("key.irons_spellbooks.spell_wheel", "key.keyboard.r");
        LAYOUT.put("key.irons_spellbooks.spellbook_cast", "key.keyboard.v");
        // Ars Nouveau moves to N; its Left Ctrl documentation key fought with sprint
        LAYOUT.put("key.ars_nouveau.selection_hud", "key.keyboard.n");
        LAYOUT.put("key.ars_nouveau.open_book", "key.keyboard.n+SHIFT");
        LAYOUT.put("key.ars_nouveau.next_slot", "");
        LAYOUT.put("key.ars_nouveau.previous_slot", "");
        LAYOUT.put("key.ars_nouveau.head_curio_hotkey", "");
        LAYOUT.put("key.ars_nouveau.open_documentation", "");
        // Cataclysm armor and weapon abilities share O
        LAYOUT.put("key.cataclysm.ability", "key.keyboard.o");
        LAYOUT.put("key.cataclysm.helmet_ability", "key.keyboard.o+SHIFT");
        LAYOUT.put("key.cataclysm.chestplate_ability", "key.keyboard.o+ALT");
        LAYOUT.put("key.cataclysm.boots_ability", "key.keyboard.o+CONTROL");
        // small toggles go to modifier combos of related keys
        LAYOUT.put("key.aether.invisibility_toggle.desc", "key.keyboard.v+ALT");
        LAYOUT.put("supplementaries.keybind.quiver", "key.keyboard.v+SHIFT");
        // Simple Voice Chat on J
        LAYOUT.put("key.voice_chat", "key.keyboard.j");
        LAYOUT.put("key.disable_voice_chat", "key.keyboard.j+SHIFT");
        LAYOUT.put("key.mute_microphone", "key.keyboard.m+SHIFT");
        LAYOUT.put("key.hide_icons", "key.keyboard.h+SHIFT");
        LAYOUT.put("key.voice_chat_group", "");
        // zoom stays on C; backpacks on B
        LAYOUT.put("key.ok_zoomer.zoom", "key.keyboard.c");
        LAYOUT.put("key.sophisticatedbackpacks.open_backpack", "key.keyboard.b");
        LAYOUT.put("key.sophisticatedbackpacks.inventory_interaction", "key.keyboard.b+CONTROL");
        LAYOUT.put("key.toms_storage.open_terminal", "key.keyboard.b+SHIFT");
        // Xaero's maps: M map, U waypoints
        LAYOUT.put("gui.xaero_open_map", "key.keyboard.m");
        LAYOUT.put("gui.xaero_waypoints_key", "key.keyboard.u");
        LAYOUT.put("gui.xaero_new_waypoint", "key.keyboard.u+SHIFT");
        LAYOUT.put("gui.xaero_enlarge_map", "key.keyboard.m+ALT");
        LAYOUT.put("gui.xaero_minimap_settings", "key.keyboard.right.bracket+SHIFT");
        LAYOUT.put("key.corpse.death_history", "key.keyboard.u+ALT");
        // shaders on K with modifiers (K alone is Cogwit's menu)
        LAYOUT.put("iris.keybind.toggleShaders", "key.keyboard.k+SHIFT");
        LAYOUT.put("iris.keybind.shaderPackSelection", "key.keyboard.k+ALT");
        LAYOUT.put("iris.keybind.reload", "key.keyboard.r+SHIFT");
        // the inventory has buttons for these; G belongs to Cogwit's wheel now
        LAYOUT.put("key.curios.open.desc", "");
        // Create's toolbox menu leaves Left Alt to Iron's spell scrolling; creative hotbar saving frees C and X
        LAYOUT.put("create.keyinfo.toolmenu", "key.keyboard.grave.accent");
        LAYOUT.put("create.keyinfo.toolbelt", "key.keyboard.grave.accent");
        LAYOUT.put("key.saveToolbarActivator", "");
        LAYOUT.put("key.loadToolbarActivator", "");
    }

    private PackKeyLayout() {}

    /** Applies the layout to whatever key mappings exist and saves options.txt. Returns how many were changed. */
    public static int apply() {
        Minecraft mc = Minecraft.getInstance();
        int changed = 0;
        for (KeyMapping key : mc.options.keyMappings) {
            String wanted = LAYOUT.get(key.getName());
            if (wanted == null) {
                continue;
            }
            KeyModifier modifier = KeyModifier.NONE;
            String name = wanted;
            int plus = wanted.indexOf('+');
            if (plus >= 0) {
                modifier = KeyModifier.valueFromString(wanted.substring(plus + 1));
                name = wanted.substring(0, plus);
            }
            InputConstants.Key code = name.isEmpty() ? InputConstants.UNKNOWN : InputConstants.getKey(name);
            if (key.getKey().equals(code) && key.getKeyModifier() == modifier) {
                continue;
            }
            key.setKeyModifierAndCode(modifier, code);
            changed++;
        }
        KeyMapping.resetMapping();
        mc.options.save();
        CogwitMod.LOGGER.info("Cogwit applied the modpack key layout ({} bindings changed)", changed);
        return changed;
    }

    /** First launch of this version: lay the keys out once, then never touch them again on our own. */
    public static void applyOnce() {
        if (ClientConfig.packKeysApplied()) {
            return;
        }
        apply();
        ClientConfig.PACK_KEYS_APPLIED.set(true);
        ClientConfig.SPEC.save();
    }
}
