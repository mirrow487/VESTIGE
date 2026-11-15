package dev.vestige.datagen;

import dev.vestige.core.VestigeModule;
import net.minecraft.data.DataGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Registers data generators. Run with {@code ./gradlew runData}; output is committed in src/generated. */
public final class DataGenModule implements VestigeModule {
    @Override
    public String id() {
        return "datagen";
    }

    @Override
    public void init(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(DataGenModule::gatherData);
    }

    private static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        generator.addProvider(event.includeClient(), new EnglishLanguageProvider(generator.getPackOutput()));
    }
}
