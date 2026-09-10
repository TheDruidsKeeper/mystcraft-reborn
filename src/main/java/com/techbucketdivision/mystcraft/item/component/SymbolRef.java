package com.techbucketdivision.mystcraft.item.component;

import com.mojang.serialization.Codec;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.symbol.SymbolRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Symbol reference stored on a page. */
public record SymbolRef(Identifier id) {
    public static final Codec<SymbolRef> CODEC = Identifier.CODEC.xmap(SymbolRef::new, SymbolRef::id);
    public static final StreamCodec<ByteBuf, SymbolRef> STREAM_CODEC = Identifier.STREAM_CODEC.map(SymbolRef::new, SymbolRef::id);

    public static SymbolRef of(AgeSymbol symbol) {
        return new SymbolRef(symbol.id());
    }

    public @Nullable AgeSymbol symbol() {
        return SymbolRegistry.get(id);
    }
}
