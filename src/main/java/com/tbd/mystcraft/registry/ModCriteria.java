package com.tbd.mystcraft.registry;

import com.tbd.mystcraft.Mystcraft;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.criterion.PlayerTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Advancement triggers (original spec §19.5). All are simple player triggers. */
public final class ModCriteria {
    private ModCriteria() {}

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, Mystcraft.MOD_ID);

    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> WRITING_DESK_WRITE = TRIGGERS.register("writing_desk_write", PlayerTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> ENTER_AGE_SAFE = TRIGGERS.register("enter_myst_dimension_safe", PlayerTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> ENTER_AGE_QUINN = TRIGGERS.register("enter_myst_dimension_quinn", PlayerTrigger::new);
}
