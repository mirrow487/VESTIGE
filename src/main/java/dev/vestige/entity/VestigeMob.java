package dev.vestige.entity;

import dev.vestige.core.Net;
import dev.vestige.combat.CombatData;
import dev.vestige.combat.CombatTarget;
import dev.vestige.combat.action.ActionDef;
import dev.vestige.combat.action.ActionPhase;
import dev.vestige.combat.action.ActionRef;
import dev.vestige.combat.damage.CombatDamage;
import dev.vestige.combat.damage.CombatDamageTypes;
import dev.vestige.combat.hit.HitResolver;
import dev.vestige.combat.machine.RunningAction;
import dev.vestige.combat.net.CombatPayloads;
import dev.vestige.combat.player.PlayerCombat;
import dev.vestige.entity.ai.ActionCombatGoal;
import dev.vestige.entity.ai.AttackTokens;
import dev.vestige.vitals.HealthScaling;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Base class for VESTIGE enemies. Runs actions on the shared combat runtime (ADR-006): the same
 * timing, hitbox sweep, telegraph and animation data as player attacks. Health, defence and
 * poise come from the enemy's {@link EnemyProfile} in VESTIGE units.
 */
public abstract class VestigeMob extends Monster implements CombatTarget, HealthScaling.NativeHealth {
    private static final String TAG_PROFILE_APPLIED = "vestige_profile_applied";

    private RunningAction current;
    private UUID actionTarget;
    private String pendingFollowUp;
    private float poise;
    private long lastPoiseHit = Long.MIN_VALUE / 2;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private boolean profileApplied;

    protected VestigeMob(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ActionCombatGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public ResourceLocation profileId() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(getType());
    }

    public Optional<EnemyProfile> profile() {
        return EntityModule.ENEMIES.get(level().isClientSide(), profileId());
    }

    public boolean busy() {
        return current != null;
    }

    public RunningAction currentAction() {
        return current;
    }

    public boolean cooldownReady(String action, long now) {
        return now >= cooldowns.getOrDefault(action, Long.MIN_VALUE);
    }

    /** Starts an attack from the profile table (called by the AI goal). */
    public void startAttack(EnemyProfile.Attack attack, LivingEntity target) {
        cooldowns.put(attack.action(), level().getGameTime() + attack.cooldown());
        pendingFollowUp = attack.followUp().filter(f -> random.nextFloat() < attack.followChance()).orElse(null);
        actionTarget = target.getUUID();
        startAction(attack.action());
    }

    /** Starts a named action from this enemy's moveset and tells nearby clients. */
    public boolean startAction(String name) {
        Optional<EnemyProfile> profile = profile();
        if (profile.isEmpty()) {
            return false;
        }
        ResourceLocation moveset = profile.get().moveset();
        Optional<ActionDef> def = CombatData.resolve(false, new ActionRef(moveset, name));
        if (def.isEmpty()) {
            return false;
        }
        current = new RunningAction(new ActionRef(moveset, name), def.get(), -1, Float.NaN, level().getGameTime(), RunningAction.Kind.OTHER);
        getNavigation().stop();
        Net.sendAround(this, new CombatPayloads.ActionStart(getId(), current.ref.toString(), -1, Float.NaN, false), true);
        return true;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            return;
        }
        if (!profileApplied) {
            applyProfile();
        }
        profile().ifPresent(p -> {
            if (level().getGameTime() - lastPoiseHit > p.poiseRegenDelay()) {
                poise = p.poise();
            }
        });
        tickAction();
    }

    private void applyProfile() {
        profile().ifPresent(p -> {
            var maxHealth = getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null && maxHealth.getBaseValue() != p.health()) {
                float fraction = getHealth() / getMaxHealth();
                maxHealth.setBaseValue(p.health());
                setHealth(getPersistentData().getBoolean(TAG_PROFILE_APPLIED) ? fraction * getMaxHealth() : getMaxHealth());
            }
            getPersistentData().putBoolean(TAG_PROFILE_APPLIED, true);
            poise = p.poise();
            profileApplied = true;
        });
    }

    private void tickAction() {
        if (current == null) {
            return;
        }
        LivingEntity target = actionTarget == null ? getTarget() : resolveTarget();
        ActionPhase before = current.phase();
        if (before == ActionPhase.WINDUP && target != null && current.hitstop() == 0) {
            float wanted = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90f;
            float turned = Mth.approachDegrees(getYRot(), wanted, current.def.tracking());
            setYRot(turned);
        }
        // Body and head follow the action's facing; the look control must not fight it.
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        applyRootMotion();
        if (current.phase() == ActionPhase.ACTIVE) {
            for (Entity e : HitResolver.sweep(this, current, getYRot(), this::canHitWithAction)) {
                if (e instanceof LivingEntity living) {
                    CombatDamage.Outcome out = CombatDamage.apply(this, living, current, current.def.power().damage(), 1f, CombatDamageTypes.ENEMY_ATTACK);
                    if (out.landed() && living instanceof ServerPlayer player) {
                        PlayerCombat.onHitByAction(player, current);
                    }
                }
            }
        }
        if (current.tick()) {
            finishAction();
        }
    }

    private LivingEntity resolveTarget() {
        LivingEntity t = getTarget();
        return t != null && t.getUUID().equals(actionTarget) ? t : t;
    }

    private void applyRootMotion() {
        if (current.hitstop() > 0) {
            setDeltaMovement(0, getDeltaMovement().y, 0);
            return;
        }
        ActionDef.Motion m = current.def.motion();
        float speed = switch (current.phase()) {
            case WINDUP -> m.windup();
            case ACTIVE -> m.active();
            case RECOVERY -> m.recovery();
            case DONE -> 0f;
        };
        Vec3 forward = Vec3.directionFromRotation(0f, getYRot());
        setDeltaMovement(forward.x * speed, getDeltaMovement().y, forward.z * speed);
    }

    private boolean canHitWithAction(Entity e) {
        return e instanceof Player p && !p.isCreative() && !p.isSpectator();
    }

    private void finishAction() {
        String followUp = pendingFollowUp;
        boolean wasReaction = current.ref.action().equals("flinch") || current.ref.action().equals("stagger");
        current = null;
        pendingFollowUp = null;
        LivingEntity target = getTarget();
        if (followUp != null && !wasReaction && target != null && target.isAlive() && distanceTo(target) < 4.5f && startAction(followUp)) {
            return;
        }
        releaseToken();
    }

    private void releaseToken() {
        if (actionTarget != null) {
            AttackTokens.release(actionTarget, getId());
            actionTarget = null;
        }
    }

    /** Stops the current action (flinch, stagger) and tells clients. */
    private void interruptWith(String reaction) {
        RunningAction was = current;
        current = null;
        pendingFollowUp = null;
        if (was != null) {
            Net.sendAround(this, new CombatPayloads.ActionEnd(getId(), CombatPayloads.EndReason.INTERRUPTED.ordinal(), -1), true);
        }
        releaseToken();
        startAction(reaction);
    }

    @Override
    public float combatDefense() {
        return profile().map(EnemyProfile::defense).orElse(0f);
    }

    @Override
    public void receivePoiseDamage(float amount, LivingEntity attacker) {
        if (isStaggered()) {
            return;
        }
        lastPoiseHit = level().getGameTime();
        poise -= amount;
        boolean armored = current != null && current.def.hyperarmor() && current.phase() != ActionPhase.RECOVERY;
        if (poise <= 0f) {
            poise = profile().map(EnemyProfile::poise).orElse(20f);
            interruptWith("stagger");
            Net.sendAround(this, new CombatPayloads.Event(CombatPayloads.EventKind.STAGGER_BREAK.ordinal(), getId()), true);
        } else if (!armored) {
            interruptWith("flinch");
        }
    }

    @Override
    public boolean isStaggered() {
        return current != null && current.ref.action().equals("stagger");
    }

    @Override
    public void applyHitstop(int ticks) {
        if (current != null) {
            current.addHitstop(ticks);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return false; // all damage goes through actions
    }

    @Override
    public void die(DamageSource source) {
        releaseToken();
        super.die(source);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }
}
