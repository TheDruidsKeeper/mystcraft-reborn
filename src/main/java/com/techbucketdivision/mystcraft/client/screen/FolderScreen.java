package com.techbucketdivision.mystcraft.client.screen;

import com.techbucketdivision.mystcraft.client.screen.gui.PageSurface;
import com.techbucketdivision.mystcraft.client.screen.gui.ToggleButton;
import com.techbucketdivision.mystcraft.menu.FolderMenu;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Collation Folder / Symbol Portfolio screen (REQUIREMENTS §8.6): search + AZ/ALL row, 132 px page surface, then the
 * player inventory (desk texture region y=82, 80 high).
 */
public class FolderScreen extends AbstractMystcraftScreen<FolderMenu> {
    private static final Identifier DESK = MystIds.id("textures/gui/writingdesk.png");
    private static final int SURFACE_H = 132;
    private static final int BUTTON = 18;
    private static final int INV_W = 176;
    private static final int INV_H = 80;

    private @Nullable PageSurface surface;
    private @Nullable ToggleButton sortButton, allButton;

    public FolderScreen(FolderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, INV_W, SURFACE_H + BUTTON + INV_H + 1);
    }

    @Override
    protected void buildElements() {
        int gx = leftPos, gy = topPos;
        surface = new PageSurface(gx, gy + BUTTON + 1, INV_W, SURFACE_H - BUTTON, menu::getPageCollection, this::player, new SurfaceHandler());
        sortButton = addElement(new ToggleButton(gx, gy, BUTTON, "AZ", () -> surface != null && surface.isSortAlphabetical(), () -> surface.toggleSort())
                .tooltip(Component.translatable("gui.mystcraft.surface.sort")));
        allButton = addElement(new ToggleButton(gx + BUTTON, gy, BUTTON, "ALL", () -> surface != null && surface.isShowAll(), () -> surface.toggleShowAll())
                .tooltip(Component.translatable("gui.mystcraft.surface.show_all")));
        addElement(surface);

        EditBox search = addEditBox(new EditBox(font, gx + (BUTTON + 2) * 2, gy, INV_W - (BUTTON + 2) * 2, BUTTON,
                Component.translatable("gui.mystcraft.surface.search")));
        search.setMaxLength(32);
        search.setHint(Component.translatable("gui.mystcraft.surface.search"));
        search.setResponder(text -> {
            if (surface != null) surface.setSearch(text);
        });
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean collection = surface != null && surface.isCollection();
        if (sortButton != null) sortButton.setEnabled(collection);
        if (allButton != null) allButton.setEnabled(collection);
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.blit(RenderPipelines.GUI_TEXTURED, DESK, leftPos, topPos + SURFACE_H + 1, 0, 82, INV_W, INV_H, 256, 256);
        String name = menu.getTabItemName();
        if (name != null) {
            g.text(font, name, leftPos + 4, topPos + SURFACE_H + 1 - 10, 0xFFFFFFFF, true);
        }
    }

    private final class SurfaceHandler implements PageSurface.Handler {
        @Override
        public void place(int index, boolean single) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Index", index);
            tag.putBoolean("Single", single);
            send(FolderMenu.MSG_ADD_TO_SURFACE, tag);
        }

        @Override
        public void pickup(PageSurface.Entry entry) {
            if (entry.count <= 0) return;
            if (surface != null && surface.isCollection()) {
                ItemStack page = entry.stack.copy();
                page.setCount(shiftDown() ? Math.min(64, entry.count) : 1);
                CompoundTag tag = new CompoundTag();
                tag.store("Page", ItemStack.OPTIONAL_CODEC, ops(), page);
                send(FolderMenu.MSG_REMOVE_FROM_COLLECTION, tag);
            } else {
                CompoundTag tag = new CompoundTag();
                tag.putInt("Index", entry.slotId);
                send(FolderMenu.MSG_REMOVE_FROM_ORDERED_COLLECTION, tag);
            }
        }
    }
}
