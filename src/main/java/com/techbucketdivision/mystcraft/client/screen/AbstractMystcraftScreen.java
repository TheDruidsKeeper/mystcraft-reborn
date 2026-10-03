package com.techbucketdivision.mystcraft.client.screen;

import com.mojang.serialization.DynamicOps;
import com.techbucketdivision.mystcraft.client.screen.gui.GuiElement;
import com.techbucketdivision.mystcraft.menu.AbstractMystcraftMenu;
import com.techbucketdivision.mystcraft.network.MenuMessagePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Base container screen: owns a list of {@link GuiElement}s (rendered in the background pass, fed input before the
 * vanilla slot handling) and the message helpers used by every Mystcraft GUI: apply locally then send
 * ({@link #send(String, CompoundTag)}).
 */
public abstract class AbstractMystcraftScreen<T extends AbstractMystcraftMenu> extends AbstractContainerScreen<T> {
    protected final List<GuiElement> elements = new ArrayList<>();
    protected final List<EditBox> editBoxes = new ArrayList<>();

    protected AbstractMystcraftScreen(T menu, Inventory inventory, Component title, int imageWidth, int imageHeight) {
        super(menu, inventory, title, imageWidth, imageHeight);
    }

    @Override
    protected void init() {
        super.init();
        elements.clear();
        editBoxes.clear();
        buildElements();
    }

    /** Create elements and widgets (called on init and resize). */
    protected abstract void buildElements();

    protected <E extends GuiElement> E addElement(E element) {
        elements.add(element);
        return element;
    }

    /** Adds an edit box widget and tracks it for key routing. */
    protected EditBox addEditBox(EditBox box) {
        editBoxes.add(box);
        addRenderableWidget(box);
        return box;
    }

    // --- messaging ------------------------------------------------------------------------------------------------

    protected LocalPlayer player() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) throw new IllegalStateException("No client player");
        return p;
    }

    protected DynamicOps<Tag> ops() {
        return player().level().registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }

    /** Applies the message locally (prediction) and sends it to the server. */
    protected void send(String message, CompoundTag data) {
        CompoundTag local = data.copy();
        local.putString(MenuMessagePayload.KEY_MESSAGE, message);
        menu.processMessage(player(), local);
        menu.sendToServer(message, data);
    }

    protected void send(String message) {
        send(message, new CompoundTag());
    }

    /** Sends without local prediction (server-only actions such as Link / RecycleDim). */
    protected void sendOnly(String message, CompoundTag data) {
        menu.sendToServer(message, data);
    }

    protected void sendOnly(String message) {
        sendOnly(message, new CompoundTag());
    }

    protected ItemStack carried() {
        return menu.getCarried();
    }

    // --- slot hints -------------------------------------------------------------------------------------------------

    /**
     * What an empty slot is for: a faded example item drawn in the slot, a short name and a description shown as a
     * tooltip while the empty slot is hovered. Makes the workstations self-explanatory without a manual.
     */
    protected record SlotHint(int slot, ItemStack ghost, Component name, List<Component> description) {}

    protected final List<SlotHint> slotHints = new ArrayList<>();

    /** Caption colour for in-GUI labels (vanilla's dark grey container text). */
    protected static final int CAPTION = 0xFF404040;
    protected static final int CAPTION_LIGHT = 0xFFE0E0E0;

    /**
     * Registers a hint for a menu slot. {@code key} resolves {@code <key>} (name) and {@code <key>.desc}
     * (description; an optional {@code <key>.desc2} adds a second line).
     */
    protected void hintSlot(int slot, ItemStack ghost, String key) {
        List<Component> desc = new ArrayList<>();
        desc.add(Component.translatable(key + ".desc"));
        if (net.minecraft.client.resources.language.I18n.exists(key + ".desc2")) desc.add(Component.translatable(key + ".desc2"));
        slotHints.add(new SlotHint(slot, ghost, Component.translatable(key), desc));
    }

    protected void hintSlot(int slot, net.minecraft.world.level.ItemLike ghost, String key) {
        hintSlot(slot, new ItemStack(ghost), key);
    }

    private void drawSlotHints(GuiGraphicsExtractor g) {
        for (SlotHint hint : slotHints) {
            if (hint.slot() >= menu.slots.size()) continue;
            net.minecraft.world.inventory.Slot slot = menu.slots.get(hint.slot());
            if (!slot.isActive() || slot.hasItem() || hint.ghost().isEmpty()) continue;
            int sx = leftPos + slot.x, sy = topPos + slot.y;
            g.item(hint.ghost(), sx, sy);
            g.fill(sx, sy, sx + 16, sy + 16, 0xA08B8B8B); // fade the example into the slot background
        }
    }

    private @Nullable List<Component> slotHintTooltip(int mouseX, int mouseY) {
        for (SlotHint hint : slotHints) {
            if (hint.slot() >= menu.slots.size()) continue;
            net.minecraft.world.inventory.Slot slot = menu.slots.get(hint.slot());
            if (!slot.isActive() || slot.hasItem()) continue;
            int sx = leftPos + slot.x, sy = topPos + slot.y;
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                List<Component> lines = new ArrayList<>();
                lines.add(hint.name());
                for (Component c : hint.description()) lines.add(c.copy().withStyle(net.minecraft.ChatFormatting.GRAY));
                return lines;
            }
        }
        return null;
    }

    /** Draws a small label (no shadow) in the container's caption colour. */
    protected void caption(GuiGraphicsExtractor g, Component text, int x, int y) {
        g.text(font, text, x, y, CAPTION, false);
    }

    protected void caption(GuiGraphicsExtractor g, String key, int x, int y) {
        caption(g, Component.translatable(key), x, y);
    }

    /** Draws a label right-aligned so that it ends at {@code right}. */
    protected void captionRight(GuiGraphicsExtractor g, Component text, int right, int y) {
        g.text(font, text, right - font.width(text), y, CAPTION, false);
    }

    // --- rendering ------------------------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        drawBackgroundTexture(graphics, mouseX, mouseY, partialTick);
        drawSlotHints(graphics);
        for (GuiElement e : elements) {
            if (e.isVisible()) e.render(graphics, mouseX, mouseY, partialTick);
        }
        for (GuiElement e : elements) {
            List<Component> tooltip = e.isVisible() ? e.tooltip() : null;
            if (tooltip != null && !tooltip.isEmpty()) {
                graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
                return;
            }
        }
        List<Component> hint = slotHintTooltip(mouseX, mouseY);
        if (hint != null) graphics.setComponentTooltipForNextFrame(font, hint, mouseX, mouseY);
    }

    /** Blit the GUI texture(s); called before the elements. */
    protected abstract void drawBackgroundTexture(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick);

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Screens draw their own labels; suppress the default title / inventory labels.
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        for (GuiElement e : elements) e.tick();
    }

    // --- input ----------------------------------------------------------------------------------------------------

    /**
     * Shift state of the most recent input event. 26.1 removed the static {@code Screen.hasShiftDown()}; modifiers now
     * travel with the input event, so we latch them here for callbacks that run outside the event (page pickup etc.).
     */
    private boolean shiftDown;

    /** Whether shift was held during the most recent mouse/key event. */
    protected boolean shiftDown() {
        return shiftDown;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        shiftDown = event.hasShiftDown();
        double mx = event.x(), my = event.y();
        for (GuiElement e : elements) {
            if (e.isEnabled() && e.mouseClicked(mx, my, event.button())) return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mx = event.x(), my = event.y();
        boolean consumed = false;
        for (GuiElement e : elements) {
            if (e.isVisible() && e.mouseReleased(mx, my, event.button())) consumed = true;
        }
        return consumed || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        for (GuiElement e : elements) {
            if (e.isEnabled() && e.mouseScrolled(mx, my, scrollY)) return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        for (EditBox box : editBoxes) {
            // Anvil-screen pattern: a focused text box swallows keys so the inventory key does not close the screen.
            if (box.isFocused() && (box.keyPressed(event) || box.canConsumeInput())) return true;
        }
        for (GuiElement e : elements) {
            if (e.isEnabled() && e.keyPressed(event)) return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        for (EditBox box : editBoxes) {
            if (box.isFocused() && box.charTyped(event)) return true;
        }
        return super.charTyped(event);
    }

    /** Convenience: the element under the mouse, if any. */
    protected @Nullable GuiElement elementAt(double mx, double my) {
        for (GuiElement e : elements) {
            if (e.isVisible() && e.contains(mx, my)) return e;
        }
        return null;
    }
}
