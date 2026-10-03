package com.techbucketdivision.mystcraft.gametest;

import com.techbucketdivision.mystcraft.instability.effects.PotionEffectProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;

/** Instability effects. */
@ForEachTest(groups = "instability")
public class InstabilityTests {

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Potion instability effects are not re-applied every tick (playtest bug: HUD timers flickering)")
    static void potionEffectNotReappliedEveryTick(ExtendedGameTestHelper helper) {
        var pig = helper.spawn(EntityType.PIG, 1, 1, 1);
        helper.assertTrue(PotionEffectProvider.shouldApply(pig, MobEffects.POISON, 200, 0), "absent effect is applied");
        pig.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
        helper.assertFalse(PotionEffectProvider.shouldApply(pig, MobEffects.POISON, 200, 0), "fresh effect is not re-applied");
        helper.assertTrue(PotionEffectProvider.shouldApply(pig, MobEffects.POISON, 200, 1), "stronger effect replaces a weaker one");
        pig.removeEffect(MobEffects.POISON);
        pig.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
        helper.assertTrue(PotionEffectProvider.shouldApply(pig, MobEffects.POISON, 200, 0), "effect past half its duration is refreshed");
        helper.succeed();
    }
}
