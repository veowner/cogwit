package dev.cogwit;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The mod used to be called "clawd". Worlds saved back then keep working: every old registry name points at the new
 * one, and the old client config and key bindings are carried over once.
 */
public final class OldNameMigration {
    public static final String OLD_ID = "clawd";

    private OldNameMigration() {}

    /** The old name of a registry entry: same path with "cogwit" spelled the old way ("cogwit_talisman"). */
    public static ResourceLocation oldName(ResourceLocation id) {
        return ResourceLocation.fromNamespaceAndPath(OLD_ID, id.getPath().replace(CogwitMod.MODID, OLD_ID));
    }

    /** Runs last for every registry, after the mod's own entries are in. */
    public static void addAliases(RegisterEvent event) {
        var registry = event.getRegistry();
        for (ResourceLocation id : registry.keySet()) {
            if (id.getNamespace().equals(CogwitMod.MODID)) {
                registry.addAlias(oldName(id), id);
            }
        }
    }

    /** Copies the old client config and renames the old key bindings in options.txt, if there's nothing new yet. */
    public static void carryOverClientFiles() {
        try {
            Path config = FMLPaths.CONFIGDIR.get();
            Path oldClient = config.resolve(OLD_ID + "-client.toml");
            Path newClient = config.resolve(CogwitMod.MODID + "-client.toml");
            if (Files.exists(oldClient) && !Files.exists(newClient)) {
                Files.writeString(newClient, Files.readString(oldClient, StandardCharsets.UTF_8)
                        .replace(OLD_ID, CogwitMod.MODID), StandardCharsets.UTF_8);
            }
            Path options = FMLPaths.GAMEDIR.get().resolve("options.txt");
            if (Files.exists(options)) {
                String text = Files.readString(options, StandardCharsets.UTF_8);
                if (text.contains("key_key." + OLD_ID + ".") && !text.contains("key_key." + CogwitMod.MODID + ".")) {
                    Files.writeString(options, text.replace("key_key." + OLD_ID + ".", "key_key." + CogwitMod.MODID + "."),
                            StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            CogwitMod.LOGGER.warn("Couldn't carry over the old clawd settings", e);
        }
    }
}
