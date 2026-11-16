package dev.vestige.equipment;

import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A VESTIGE armor piece: a GeckoLib 3D model per set (geo/item/armor/&lt;set&gt;.geo.json, from
 * tools/gen_models.py) with an idle loop for cloth and ornaments. Its protection comes from
 * VESTIGE data ({@code vestige/armor}), not vanilla armor points (GDD §9.1).
 */
public class VestigeArmorItem extends ArmorItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final String set;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.armor.idle");

    public VestigeArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties, String set) {
        super(material, type, properties);
        this.set = set;
    }

    public String set() {
        return set;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(EquipmentArmor.renderProvider(set));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
