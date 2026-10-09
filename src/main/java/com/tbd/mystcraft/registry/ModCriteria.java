package com.tbd.mystcraft.registry;

import com.tbd.mystcraft.Mystcraft;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.criterion.PlayerTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Advancement triggers ({@code data/mystcraft/advancement}). All are plain player triggers fired by the action
 * itself, never by an item turning up in the inventory, so dev commands and creative never award them.
 */
public final class ModCriteria {
    private ModCriteria() {}

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, Mystcraft.MOD_ID);

    /** A symbol was learned ({@code knowledge/SymbolKnowledge}). */
    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> SYMBOL_LEARNED = TRIGGERS.register("symbol_learned", PlayerTrigger::new);
    /** A page was written or a modifier attached at a Writing Desk. */
    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> WRITING_DESK_WRITE = TRIGGERS.register("writing_desk_write", PlayerTrigger::new);
    /** A Descriptive Book was taken from the Book Binder. */
    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> BOOK_BOUND = TRIGGERS.register("book_bound", PlayerTrigger::new);
    /** An Unlinked Book was linked to the place it was used. */
    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> BOOK_LINKED = TRIGGERS.register("book_linked", PlayerTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> ENTER_AGE_SAFE = TRIGGERS.register("enter_myst_dimension_safe", PlayerTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> ENTER_AGE_QUINN = TRIGGERS.register("enter_myst_dimension_quinn", PlayerTrigger::new);
    /** Standing in an Age whose instability score reached {@code BalanceConfig.UNSTABLE_ADVANCEMENT_SCORE}. */
    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> AGE_UNSTABLE = TRIGGERS.register("age_unstable", PlayerTrigger::new);
    /** Killed by one of the mod's instability damage sources ({@code instability/InstabilityDeaths}). */
    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> KILLED_BY_INSTABILITY = TRIGGERS.register("killed_by_instability", PlayerTrigger::new);
}
