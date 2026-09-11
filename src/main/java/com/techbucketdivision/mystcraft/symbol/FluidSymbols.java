package com.techbucketdivision.mystcraft.symbol;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.api.symbol.BlockCategory;
import com.techbucketdivision.mystcraft.api.symbol.WordData;
import com.techbucketdivision.mystcraft.registry.ModFluids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Fluid symbols (REQUIREMENTS §4.3.14): one block symbol per registered source fluid other than vanilla water/lava
 * (which are covered by the built-in block table). Word "Sea"; card rank 4, grammar rank 4 (config/balance table of
 * the original reduced to the built-in defaults, plus black ink = card 1 / grammar 0). Categories: FLUID always, SEA
 * unless sea-banned (GAS instead of SEA for fluids lighter than air).
 */
public final class FluidSymbols {
    private FluidSymbols() {}

    public static final int DEFAULT_CARD_RANK = 4;
    public static final int DEFAULT_GRAMMAR_RANK = 4;

    private static final Set<Identifier> BLACKLIST = new LinkedHashSet<>();
    private static final Set<Identifier> SEA_BANNED = new LinkedHashSet<>();
    private static boolean registered;

    /** IMC-equivalent: never create a symbol for this fluid. */
    public static synchronized void blacklist(Identifier fluidId) {
        BLACKLIST.add(fluidId);
    }

    /** Fluid may be used as FLUID (lakes) but not as the sea. */
    public static synchronized void banAsSea(Identifier fluidId) {
        SEA_BANNED.add(fluidId);
    }

    /** Registers the fluid symbols once (idempotent). Called from {@link BiomeSymbols#registerAll}. */
    public static synchronized void registerAll() {
        if (registered) return;
        registered = true;
        int count = 0;
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            Identifier fluidId = BuiltInRegistries.FLUID.getKey(fluid);
            if (fluidId == null || BLACKLIST.contains(fluidId)) continue;
            if (fluid == Fluids.EMPTY || fluid == Fluids.WATER || fluid == Fluids.LAVA
                    || fluid == Fluids.FLOWING_WATER || fluid == Fluids.FLOWING_LAVA) continue;
            FluidState state = fluid.defaultFluidState();
            if (!state.isSource()) continue; // skip flowing variants
            BlockState block;
            try {
                block = state.createLegacyBlock();
            } catch (RuntimeException e) {
                continue;
            }
            if (block.isAir()) continue;
            boolean gaseous = false;
            try {
                gaseous = fluid.getFluidType().isLighterThanAir(); // FluidType#isLighterThanAir() is final: density <= 0
            } catch (RuntimeException ignored) {
            }
            boolean seaBanned = SEA_BANNED.contains(fluidId);
            int card = DEFAULT_CARD_RANK;
            int grammar = DEFAULT_GRAMMAR_RANK;
            if (fluid == ModFluids.BLACK_INK.get()) {
                card = 1;
                grammar = 0;
                seaBanned = true;
            }
            Map<BlockCategory, Integer> ranks = new LinkedHashMap<>();
            ranks.put(BlockCategory.FLUID, grammar);
            if (gaseous) {
                ranks.put(BlockCategory.GAS, grammar);
            } else if (!seaBanned) {
                ranks.put(BlockCategory.SEA, grammar);
            }
            AgeSymbol symbol = BlockSymbols.createBlockSymbol(block, WordData.SEA, card, ranks);
            if (SymbolRegistry.contains(symbol.id())) continue; // already a built-in block symbol
            if (BlockSymbols.register(symbol)) count++;
        }
        Mystcraft.LOGGER.info("Registered {} fluid symbols", count);
    }
}
