package dev.vestige.combat.projectile;

import dev.vestige.combat.CombatModule;
import dev.vestige.combat.damage.CombatDamage;
import dev.vestige.combat.damage.CombatDamageTypes;
import dev.vestige.combat.machine.RunningAction;
import dev.vestige.combat.resonance.ResonanceService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * An arrow loosed by a VESTIGE bow action. On an entity it deals the shot's attack rating x motion
 * value through {@link CombatDamage} (defence, poise, stagger, criticals, feedback), like a melee
 * hit, so arts such as Pinning Shot work the same way. Arrows from a save (no action) fall back to
 * vanilla arrow damage.
 */
public class CombatArrow extends AbstractArrow {
    private RunningAction action;
    private double attackRating;
    private float poiseScale = 1f;
    private float damageBonus;
    private float resonanceOnHit;

    public CombatArrow(EntityType<? extends CombatArrow> type, Level level) {
        super(type, level);
    }

    public CombatArrow(Level level, LivingEntity owner, ItemStack ammo) {
        super(CombatModule.COMBAT_ARROW.get(), owner, level, ammo, null);
    }

    /** Binds the shot that fired this arrow. */
    public void arm(RunningAction action, double attackRating, float poiseScale, float damageBonus, float resonanceOnHit) {
        this.action = action;
        this.attackRating = attackRating;
        this.poiseScale = poiseScale;
        this.damageBonus = damageBonus;
        this.resonanceOnHit = resonanceOnHit;
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.ARROW);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (action == null || level().isClientSide || !(getOwner() instanceof LivingEntity owner)
                || !(result.getEntity() instanceof LivingEntity target)) {
            super.onHitEntity(result);
            return;
        }
        if (target == owner) {
            return;
        }
        CombatDamage.Outcome out = CombatDamage.apply(owner, target, action, attackRating, poiseScale, damageBonus,
                CombatDamageTypes.WEAPON, CombatDamage.Options.RANGED);
        if (out.landed() && owner instanceof ServerPlayer player) {
            ResonanceService.add(player, resonanceOnHit);
        }
        playSound(SoundEvents.ARROW_HIT, 1.0f, 1.2f / (random.nextFloat() * 0.2f + 0.9f));
        discard();
    }
}
