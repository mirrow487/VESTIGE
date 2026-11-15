package dev.vestige.datagen;

import dev.vestige.VestigeIds;
import dev.vestige.config.ServerConfig;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/** en_us translations. Each module adds its keys here as it gains user-facing text. */
public final class EnglishLanguageProvider extends LanguageProvider {
    public EnglishLanguageProvider(PackOutput output) {
        super(output, VestigeIds.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("vestige.configuration.title", "VESTIGE Configuration");
        add("vestige.configuration.section.vestige.server.toml", "World Settings");
        add("vestige.configuration.section.vestige.server.toml.title", "VESTIGE World Settings");
        for (ServerConfig.Entry entry : ServerConfig.ENTRIES) {
            add(entry.key(), entry.name());
            add(entry.key() + ".tooltip", entry.tooltip());
        }
    }
}
