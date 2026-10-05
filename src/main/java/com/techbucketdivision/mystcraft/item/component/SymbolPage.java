package com.tbd.mystcraft.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tbd.mystcraft.api.symbol.AgeSymbol;
import com.tbd.mystcraft.symbol.SymbolRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The symbol component of a page (world-building plan §3): the primary symbol, the modifier symbols attached to it
 * (applied in list order before the symbol when the Age is built) and whether the page was <i>discovered</i> - added
 * by the game at the first link rather than written by a player.
 */
public record SymbolPage(Identifier symbol, List<Identifier> modifiers, boolean discovered) {
    public static final Codec<SymbolPage> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(SymbolPage::symbol),
            Identifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(SymbolPage::modifiers),
            Codec.BOOL.optionalFieldOf("discovered", false).forGetter(SymbolPage::discovered)
    ).apply(i, SymbolPage::new));
    public static final StreamCodec<ByteBuf, SymbolPage> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, SymbolPage::symbol,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), SymbolPage::modifiers,
            ByteBufCodecs.BOOL, SymbolPage::discovered,
            SymbolPage::new);

    public SymbolPage {
        modifiers = List.copyOf(modifiers);
    }

    public static SymbolPage of(Identifier symbol) {
        return new SymbolPage(symbol, List.of(), false);
    }

    public static SymbolPage of(AgeSymbol symbol) {
        return of(symbol.id());
    }

    public @Nullable AgeSymbol resolve() {
        return SymbolRegistry.get(symbol);
    }

    public SymbolPage withModifier(Identifier modifier) {
        List<Identifier> list = new ArrayList<>(modifiers);
        list.add(modifier);
        return new SymbolPage(symbol, list, discovered);
    }

    public SymbolPage withoutModifier(int index) {
        List<Identifier> list = new ArrayList<>(modifiers);
        if (index >= 0 && index < list.size()) list.remove(index);
        return new SymbolPage(symbol, list, discovered);
    }

    public SymbolPage withModifiers(List<Identifier> modifiers) {
        return new SymbolPage(symbol, modifiers, discovered);
    }

    public SymbolPage asDiscovered(boolean discovered) {
        return new SymbolPage(symbol, modifiers, discovered);
    }

    /** The symbol ids in build order: every modifier, then the symbol. */
    public List<Identifier> flatten() {
        List<Identifier> out = new ArrayList<>(modifiers.size() + 1);
        out.addAll(modifiers);
        out.add(symbol);
        return out;
    }
}
