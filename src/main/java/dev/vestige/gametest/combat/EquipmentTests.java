package dev.vestige.gametest.combat;

import dev.vestige.VestigeIds;
import dev.vestige.combat.damage.CombatDamage;
import dev.vestige.combat.machine.CombatInput;
import dev.vestige.combat.machine.RunningAction;
import dev.vestige.combat.player.PlayerCombat;
import dev.vestige.combat.status.StatusService;
import dev.vestige.entity.EntityModule;
import dev.vestige.entity.mob.Hollowed;
import dev.vestige.equipment.Attunement;
import dev.vestige.equipment.EquipSlot;
import dev.vestige.equipment.EquipmentArmor;
import dev.vestige.equipment.EquipmentItems;
import dev.vestige.equipment.EquipmentMenu;
import dev.vestige.equipment.EquipmentModule;
import dev.vestige.equipment.EquipmentRules;
import dev.vestige.equipment.EquipmentStats;
import dev.vestige.equipment.Stance;
import dev.vestige.player.GlimService;
import dev.vestige.stats.Attribute;
import dev.vestige.stats.AttributeRules;
import dev.vestige.stats.Burden;
import dev.vestige.stats.DamageSpread;
import dev.vestige.stats.DamageType;
import dev.vestige.stats.Perks;
import dev.vestige.stats.Stats;
import dev.vestige.stats.StatusType;
import dev.vestige.weapon.WeaponModule;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Milestone 5 on a real server: attributes and Attunement, equipment rules, armor sets, burden,
 * damage-type identities, statuses, player poise, combat stance and the equipment menu.
 */
@GameTestHolder(VestigeIds.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EquipmentTests {
    private static final String ARENA = "arena";

    private EquipmentTests() {}

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = TestPlayers.survival(h, new Vec3(4.5, 1, 2.5), 0f);
        h.onEachTick(() -> {
            if (p.isAlive()) {
                PlayerCombat.serverTick(p);
                EquipmentModule.applyDerived(p);
            }
        });
        return p;
    }

    private static void equip(ServerPlayer p, EquipSlot slot, ItemStack stack) {
        var menu = new EquipmentMenu(0, p.getInventory());
        menu.getSlot(slot.ordinal()).set(stack);
    }

    private static void wear(ServerPlayer p, net.minecraft.world.item.Item... pieces) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < pieces.length; i++) {
            p.setItemSlot(slots[i], new ItemStack(pieces[i]));
        }
    }

    private static Hollowed dummy(GameTestHelper h, net.minecraft.world.entity.EntityType<Hollowed> type) {
        Hollowed mob = h.spawn(type, new Vec3(4.5, 1, 4.5));
        mob.setNoAi(true);
        return mob;
    }

    // ------------------------------------------------------------------ attributes and Attunement

    /** Vitality 20 raises max health from 100 to 140; Endurance 20 raises stamina to 120. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void attributesRaiseHealthAndStamina(GameTestHelper h) {
        ServerPlayer p = player(h);
        Attunement.set(p, Attribute.VITALITY, 20);
        Attunement.set(p, Attribute.ENDURANCE, 20);
        h.runAfterDelay(2, () -> {
            h.assertTrue(Math.abs(p.getMaxHealth() - 140f) < 0.01f, "max health " + p.getMaxHealth());
            h.assertTrue(Math.abs(dev.vestige.vitals.StaminaService.get(p).max() - 120f) < 0.01f,
                    "stamina " + dev.vestige.vitals.StaminaService.get(p).max());
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** Attuning spends the Glim for each level bought; an unaffordable purchase changes nothing. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void attunementSpendsGlim(GameTestHelper h) {
        ServerPlayer p = player(h);
        long cost = Attunement.cost(1, 3);
        GlimService.set(p, cost + 5);
        long spent = Attunement.attune(p, java.util.Map.of(Attribute.STRENGTH, 3));
        h.assertTrue(spent == cost && GlimService.get(p) == 5, "spent " + spent + ", left " + GlimService.get(p));
        h.assertTrue(p.getData(EquipmentModule.ATTRIBUTES).get(Attribute.STRENGTH) == 13, "strength 13");
        h.assertTrue(Attunement.attune(p, java.util.Map.of(Attribute.DEXTERITY, 1)) == -1, "unaffordable must be refused");
        h.assertTrue(p.getData(EquipmentModule.ATTRIBUTES).get(Attribute.DEXTERITY) == AttributeRules.BASE, "dexterity unchanged");
        TestPlayers.remove(p);
        h.succeed();
    }

    /** Strength scales a maul: STR 30 hits measurably harder than STR 14 (its requirement). */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void strengthScalesMaul(GameTestHelper h) {
        ServerPlayer p = player(h);
        var maul = dev.vestige.weapon.WeaponProfiles.of(new ItemStack(WeaponModule.HAND_MAUL.get()), false).orElseThrow();
        Attunement.set(p, Attribute.STRENGTH, 14);
        float low = dev.vestige.combat.player.PlayerAttack.spread(p, maul, null).get(DamageType.STRIKE);
        Attunement.set(p, Attribute.STRENGTH, 30);
        float high = dev.vestige.combat.player.PlayerAttack.spread(p, maul, null).get(DamageType.STRIKE);
        Attunement.set(p, Attribute.STRENGTH, 10);
        float under = dev.vestige.combat.player.PlayerAttack.spread(p, maul, null).get(DamageType.STRIKE);
        h.assertTrue(high > low * 1.3f, "STR 30 " + high + " vs STR 14 " + low);
        h.assertTrue(under < 72f * 0.65f, "below the requirement the maul is weak: " + under);
        TestPlayers.remove(p);
        h.succeed();
    }

    // ------------------------------------------------------------------ equipment rules and sets

    /** Kinds, duplicates, families and locked slots (GDD §9.1). */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void equipmentRules(GameTestHelper h) {
        ServerPlayer p = player(h);
        var inv = p.getData(EquipmentModule.RPG_INVENTORY);
        ItemStack ring = new ItemStack(EquipmentItems.HARTS_RING.get());
        h.assertTrue(EquipmentRules.check(p, inv, EquipSlot.RING_1, ring) == EquipmentRules.Refusal.NONE, "ring fits a ring slot");
        h.assertTrue(EquipmentRules.check(p, inv, EquipSlot.TALISMAN_1, ring) == EquipmentRules.Refusal.WRONG_KIND, "not a talisman");
        h.assertTrue(EquipmentRules.check(p, inv, EquipSlot.MAIN, ring) == EquipmentRules.Refusal.WRONG_KIND, "not a weapon");
        equip(p, EquipSlot.RING_1, ring);
        h.assertTrue(EquipmentRules.check(p, inv, EquipSlot.RING_2, new ItemStack(EquipmentItems.HARTS_RING.get()))
                == EquipmentRules.Refusal.DUPLICATE, "no duplicate rings");
        h.assertTrue(EquipmentRules.check(p, inv, EquipSlot.RELIC_3, new ItemStack(EquipmentItems.DIVERS_BELL.get()))
                == EquipmentRules.Refusal.LOCKED, "the 3rd relic slot is locked until Mother Tallow");
        h.assertTrue(EquipmentRules.check(p, inv, EquipSlot.MAIN, new ItemStack(WeaponModule.KEEPSAKE_BLADE.get()))
                == EquipmentRules.Refusal.NONE, "a weapon fits Main");
        h.assertTrue(EquipmentRules.check(p, inv, EquipSlot.SECONDARY, new ItemStack(WeaponModule.PLANK_BUCKLER.get()))
                == EquipmentRules.Refusal.NONE, "a shield fits Secondary");
        TestPlayers.remove(p);
        h.succeed();
    }

    /** A full Wickiron set: heavy absorption, the 2- and 4-piece bonuses, and a heavy burden with a maul. */
    @GameTest(template = ARENA, timeoutTicks = 30)
    public static void wickironSetBonusesAndBurden(GameTestHelper h) {
        ServerPlayer p = player(h);
        wear(p, EquipmentArmor.WICKIRON_HELM.get(), EquipmentArmor.WICKIRON_PLATE.get(), EquipmentArmor.WICKIRON_CUISSES.get(),
                EquipmentArmor.WICKIRON_GREAVES.get());
        equip(p, EquipSlot.MAIN, new ItemStack(WeaponModule.WICKIRON_GREATBLADE.get()));
        var s = Stats.of(p);
        h.assertTrue(s.sets.getOrDefault("vestige:wickiron", 0) == 4, "4 pieces counted: " + s.sets);
        h.assertTrue(s.has(Perks.WICKIRON_SLOW_BURN), "4-piece perk active");
        h.assertTrue(s.absorption.get(DamageType.SLASH) > 0.45f, "heavy physical absorption: " + s.absorption.get(DamageType.SLASH));
        h.assertTrue(s.burden == Burden.HEAVY, "heavy burden, load " + s.equipLoad + "/" + s.equipCapacity + " -> " + s.burden);
        h.assertTrue(s.poise >= 37f, "poise " + s.poise);
        TestPlayers.remove(p);
        h.succeed();
    }

    /** The burden decides the roll: heavy gear rolls short (roll_heavy); the Hollow Ring turns it into a blink. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void burdenChoosesTheRoll(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WeaponModule.KEEPSAKE_BLADE.get()));
        wear(p, EquipmentArmor.WICKIRON_HELM.get(), EquipmentArmor.WICKIRON_PLATE.get(), EquipmentArmor.WICKIRON_CUISSES.get(),
                EquipmentArmor.WICKIRON_GREAVES.get());
        equip(p, EquipSlot.SECONDARY, new ItemStack(WeaponModule.TIMBER_BULWARK.get()));
        equip(p, EquipSlot.MAIN, new ItemStack(WeaponModule.WICKIRON_GREATBLADE.get())); // 52.7 / 60 = 88%: heavy
        h.runAfterDelay(2, () -> PlayerCombat.handleInput(p, 3, CombatInput.ROLL.ordinal(), 0f));
        h.runAfterDelay(4, () -> {
            RunningAction a = PlayerCombat.state(p).machine().current();
            h.assertTrue(a != null && a.ref.action().equals("roll_heavy"), "expected roll_heavy, was " + (a == null ? "none" : a.ref.action()));
        });
        h.runAfterDelay(30, () -> {
            equip(p, EquipSlot.RING_1, new ItemStack(EquipmentItems.HOLLOW_RING.get()));
            PlayerCombat.handleInput(p, 4, CombatInput.ROLL.ordinal(), 0f);
        });
        h.runAfterDelay(32, () -> {
            RunningAction a = PlayerCombat.state(p).machine().current();
            h.assertTrue(a != null && a.ref.action().equals("roll_blink"), "expected roll_blink, was " + (a == null ? "none" : a.ref.action()));
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ damage-type identities

    /** Against the iron-wrapped Brute, Strike beats Slash of the same rating. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void strikeBeatsArmoredBrute(GameTestHelper h) {
        Hollowed brute = dummy(h, EntityModule.HOLLOWED_BRUTE.get());
        h.runAfterDelay(2, () -> {
            float slash = CombatDamage.perType(brute, DamageSpread.single(DamageType.SLASH, 60), 1, 0, DamageSpread.EMPTY).total();
            float strike = CombatDamage.perType(brute, DamageSpread.single(DamageType.STRIKE, 60), 1, 0, DamageSpread.EMPTY).total();
            h.assertTrue(strike > slash * 1.15f, "strike " + strike + " vs slash " + slash);
            h.succeed();
        });
    }

    /** Pierce counter-hits a target in the middle of its attack (x1.25). */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void pierceCounterHits(GameTestHelper h) {
        Hollowed mob = dummy(h, EntityModule.HOLLOWED.get());
        float[] idle = {0f};
        h.runAfterDelay(2, () -> {
            idle[0] = CombatDamage.perType(mob, DamageSpread.single(DamageType.PIERCE, 60), 1, 0, DamageSpread.EMPTY).total();
            mob.startAction("swipe");
        });
        h.runAfterDelay(6, () -> {
            float counter = CombatDamage.perType(mob, DamageSpread.single(DamageType.PIERCE, 60), 1, 0, DamageSpread.EMPTY).total();
            h.assertTrue(Math.abs(counter - idle[0] * 1.25f) < 0.01f, "counter " + counter + " vs idle " + idle[0]);
            h.succeed();
        });
    }

    /** Radiance smites the Pale-touched (x1.3); Void ignores absorption; Hush slips past half the defence. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void elementalIdentities(GameTestHelper h) {
        Hollowed hollowed = dummy(h, EntityModule.HOLLOWED.get());
        h.spawn(EntityModule.TOLLING_RAM.get(), new Vec3(2.5, 1, 6.5)).setNoAi(true);
        h.runAfterDelay(2, () -> {
            var ramEntity = h.getLevel().getEntitiesOfClass(dev.vestige.entity.mob.TollingRam.class,
                    new net.minecraft.world.phys.AABB(h.absolutePos(net.minecraft.core.BlockPos.ZERO)).inflate(12)).get(0);
            float radPale = CombatDamage.perType(hollowed, DamageSpread.single(DamageType.RADIANCE, 50), 1, 0, DamageSpread.EMPTY).total();
            float radBeast = CombatDamage.perType(ramEntity, DamageSpread.single(DamageType.RADIANCE, 50), 1, 0, DamageSpread.EMPTY).total();
            // The ram's radiance defence is 9 vs the hollowed's 6, so compare against the formula without the smite.
            double plainPale = dev.vestige.combat.damage.DamageCalculator.afterDefense(50, 6);
            h.assertTrue(Math.abs(radPale - plainPale * 1.3) < 0.05, "radiance on pale " + radPale + " vs " + plainPale * 1.3);
            h.assertTrue(radBeast < plainPale, "no smite on a beast: " + radBeast);
            float hush = CombatDamage.perType(ramEntity, DamageSpread.single(DamageType.HUSH, 50), 1, 0, DamageSpread.EMPTY).total();
            double hushExpected = dev.vestige.combat.damage.DamageCalculator.afterDefense(50, 9 * 0.5);
            h.assertTrue(Math.abs(hush - hushExpected) < 0.05, "hush halves defence: " + hush + " vs " + hushExpected);
            h.succeed();
        });
    }

    /** Void ignores a player's absorption entirely; Slash is absorbed. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void voidIgnoresAbsorption(GameTestHelper h) {
        ServerPlayer p = player(h);
        wear(p, EquipmentArmor.WICKIRON_HELM.get(), EquipmentArmor.WICKIRON_PLATE.get(), EquipmentArmor.WICKIRON_CUISSES.get(),
                EquipmentArmor.WICKIRON_GREAVES.get());
        float slash = CombatDamage.perType(p, DamageSpread.single(DamageType.SLASH, 40), 1, 0, DamageSpread.EMPTY).total();
        float voidDmg = CombatDamage.perType(p, DamageSpread.single(DamageType.VOID, 40), 1, 0, DamageSpread.EMPTY).total();
        h.assertTrue(Math.abs(voidDmg - 40f) < 0.01f, "void through plate: " + voidDmg);
        h.assertTrue(slash < 25f, "slash absorbed by plate: " + slash);
        TestPlayers.remove(p);
        h.succeed();
    }

    // ------------------------------------------------------------------ statuses

    /** Rend bursts for 12% of max health plus 5% of current when its meter fills. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void rendBursts(GameTestHelper h) {
        Hollowed mob = dummy(h, EntityModule.HOLLOWED.get());
        h.runAfterDelay(2, () -> {
            float before = mob.getHealth();
            float expected = mob.getMaxHealth() * 0.12f + before * 0.05f;
            h.assertTrue(!StatusService.build(mob, StatusType.REND, 60, null), "60 does not trigger");
            h.assertTrue(StatusService.build(mob, StatusType.REND, 60, null), "120 triggers");
            h.assertTrue(Math.abs((before - mob.getHealth()) - expected) < 0.5f, "rend burst " + (before - mob.getHealth()) + " vs " + expected);
            h.succeed();
        });
    }

    /** Scorch cuts healing by 30%; Voidbite takes 20% max health until the player rests (cure). */
    @GameTest(template = ARENA, timeoutTicks = 30)
    public static void scorchAndVoidbite(GameTestHelper h) {
        ServerPlayer p = player(h);
        float max = p.getMaxHealth();
        StatusService.build(p, StatusType.SCORCH, 150, null);
        h.assertTrue(StatusService.healFactor(p) == 0.7f, "scorch cuts healing");
        StatusService.build(p, StatusType.VOIDBITE, 150, null);
        h.assertTrue(Math.abs(p.getMaxHealth() - max * 0.8f) < 0.01f, "voidbite -20% max HP: " + p.getMaxHealth());
        StatusService.cure(p);
        h.assertTrue(Math.abs(p.getMaxHealth() - max) < 0.01f && StatusService.healFactor(p) == 1f, "rest cures both");
        TestPlayers.remove(p);
        h.succeed();
    }

    /** Hushed players gain no Resonance and cannot use arts. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void hushedBlocksResonance(GameTestHelper h) {
        ServerPlayer p = player(h);
        StatusService.build(p, StatusType.HUSHED, 150, null);
        dev.vestige.combat.resonance.ResonanceService.add(p, 30f);
        h.assertTrue(dev.vestige.combat.resonance.ResonanceService.get(p) == 0f, "no Resonance while Hushed");
        TestPlayers.remove(p);
        h.succeed();
    }

    // ------------------------------------------------------------------ poise, perks

    /** Enough poise (Bellbronze set + band) shrugs off a Hollowed swipe without a flinch. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void poiseAbsorbsFlinch(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WeaponModule.KEEPSAKE_BLADE.get()));
        wear(p, EquipmentArmor.BELLBRONZE_HELM.get(), EquipmentArmor.BELLBRONZE_MAIL.get(), EquipmentArmor.BELLBRONZE_TASSETS.get(),
                EquipmentArmor.BELLBRONZE_SABATONS.get());
        equip(p, EquipSlot.RING_1, new ItemStack(EquipmentItems.BELLBRONZE_BAND.get()));
        Hollowed mob = h.spawn(EntityModule.HOLLOWED.get(), new Vec3(4.5, 1, 4.1));
        mob.setNoAi(true);
        mob.setYRot(180f);
        h.runAfterDelay(2, () -> {
            mob.setTarget(p);
            mob.startAction("swipe");
            PlayerCombat.handleInput(p, 9, CombatInput.HEAVY_PRESS.ordinal(), Float.NaN);
        });
        h.runAfterDelay(2 + 14 + 3, () -> {
            h.assertTrue(p.getHealth() < p.getMaxHealth(), "the swipe should land");
            RunningAction a = PlayerCombat.state(p).machine().current();
            h.assertTrue(a != null && a.ref.action().equals("heavy"), "poise should keep the heavy going, was " + (a == null ? "none" : a.ref.action()));
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** The Talisman of Second Breath turns one killing blow into 20% health, then needs 10 minutes. */
    @GameTest(template = ARENA, timeoutTicks = 30)
    public static void secondBreathRevives(GameTestHelper h) {
        ServerPlayer p = player(h);
        equip(p, EquipSlot.TALISMAN_1, new ItemStack(EquipmentItems.TALISMAN_OF_SECOND_BREATH.get()));
        h.runAfterDelay(2, () -> p.hurt(p.damageSources().generic(), 10_000f));
        h.runAfterDelay(4, () -> {
            h.assertTrue(p.isAlive() && Math.abs(p.getHealth() - p.getMaxHealth() * 0.2f) < 0.5f, "revived at 20%: " + p.getHealth());
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ stance and menu

    /** Combat stance swaps real stacks and swaps them back: nothing duplicated, nothing lost. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void stanceSwapsWithoutDuplication(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, new ItemStack(Items.BREAD, 5));
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH, 3));
        equip(p, EquipSlot.MAIN, new ItemStack(WeaponModule.KEEPSAKE_BLADE.get()));
        equip(p, EquipSlot.SECONDARY, new ItemStack(WeaponModule.PLANK_BUCKLER.get()));
        h.assertTrue(Stance.enter(p), "enter stance");
        h.assertTrue(p.getMainHandItem().is(WeaponModule.KEEPSAKE_BLADE.get()) && p.getOffhandItem().is(WeaponModule.PLANK_BUCKLER.get()),
                "wielding the RPG weapons");
        var inv = p.getData(EquipmentModule.RPG_INVENTORY);
        h.assertTrue(inv.get(EquipSlot.MAIN).isEmpty() && inv.get(EquipSlot.SECONDARY).isEmpty(), "slots moved, not copied");
        h.assertTrue(p.getInventory().countItem(Items.BREAD) == 0, "bread stashed, not in the inventory");
        Stance.exit(p);
        h.assertTrue(p.getMainHandItem().is(Items.BREAD) && p.getMainHandItem().getCount() == 5, "bread back");
        h.assertTrue(p.getOffhandItem().is(Items.TORCH) && p.getOffhandItem().getCount() == 3, "torches back");
        h.assertTrue(inv.get(EquipSlot.MAIN).is(WeaponModule.KEEPSAKE_BLADE.get()) && inv.get(EquipSlot.SECONDARY).is(WeaponModule.PLANK_BUCKLER.get()),
                "weapons back in their slots");
        h.assertTrue(p.getInventory().countItem(WeaponModule.KEEPSAKE_BLADE.get()) == 0, "no duplicate blade");
        TestPlayers.remove(p);
        h.succeed();
    }

    /** Opening any container leaves combat stance first (the stash comes home). */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void containerLeavesStance(GameTestHelper h) {
        ServerPlayer p = player(h);
        equip(p, EquipSlot.MAIN, new ItemStack(WeaponModule.KEEPSAKE_BLADE.get()));
        Stance.enter(p);
        dev.vestige.equipment.EquipmentMenus.open(p);
        h.assertTrue(!Stance.in(p), "stance left on opening the equipment screen");
        h.assertTrue(p.getData(EquipmentModule.RPG_INVENTORY).get(EquipSlot.MAIN).is(WeaponModule.KEEPSAKE_BLADE.get()), "blade back in Main");
        p.closeContainer();
        TestPlayers.remove(p);
        h.succeed();
    }

    /** Shift-clicking in the equipment menu sends a ring to a ring slot and a helm to the head slot. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void menuShiftClickRoutesItems(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.getInventory().setItem(9, new ItemStack(EquipmentItems.RING_OF_QUICK_BREATH.get()));
        p.getInventory().setItem(10, new ItemStack(EquipmentArmor.BELLBRONZE_HELM.get()));
        var menu = new EquipmentMenu(1, p.getInventory());
        menu.quickMoveStack(p, EquipmentMenu.INV_START);
        menu.quickMoveStack(p, EquipmentMenu.INV_START + 1);
        var inv = p.getData(EquipmentModule.RPG_INVENTORY);
        h.assertTrue(inv.get(EquipSlot.RING_1).is(EquipmentItems.RING_OF_QUICK_BREATH.get()), "ring equipped: " + inv.get(EquipSlot.RING_1));
        h.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).is(EquipmentArmor.BELLBRONZE_HELM.get()), "helm worn");
        EquipmentStats.invalidate(p);
        h.assertTrue(Math.abs(Stats.of(p).staminaRegen - 1.15f) < 0.001f, "the ring's stamina regen applies at once: " + Stats.of(p).staminaRegen);
        TestPlayers.remove(p);
        h.succeed();
    }
}
