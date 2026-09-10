package com.techbucketdivision.mystcraft.blockentity;

import com.techbucketdivision.mystcraft.registry.ModBlockEntities;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Registers item / fluid handler capabilities for the block entities (mod bus {@link RegisterCapabilitiesEvent}). */
public final class BlockEntityCapabilities {
    private BlockEntityCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.INK_MIXER.get(), (be, side) -> be.inventory);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.BOOK_BINDER.get(), (be, side) -> be.inventory);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.BOOK_RECEPTACLE.get(), (be, side) -> be.inventory);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.BOOK_DISPLAY.get(), (be, side) -> be.inventory);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.LINK_MODIFIER.get(), (be, side) -> be.inventory);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.WRITING_DESK.get(), (be, side) -> be.main);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.WRITING_DESK.get(), (be, side) -> be.inkwell);
    }
}
