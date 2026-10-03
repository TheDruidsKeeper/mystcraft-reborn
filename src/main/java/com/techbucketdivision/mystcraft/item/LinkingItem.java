package com.techbucketdivision.mystcraft.item;

import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.age.AgeManager;
import com.techbucketdivision.mystcraft.api.item.ItemBehaviours;
import com.techbucketdivision.mystcraft.api.linking.LinkInfo;
import com.techbucketdivision.mystcraft.api.linking.LinkProperty;
import com.techbucketdivision.mystcraft.entity.LinkbookEntity;
import com.techbucketdivision.mystcraft.item.component.BookHealth;
import com.techbucketdivision.mystcraft.linking.LinkController;
import com.techbucketdivision.mystcraft.linking.LinkListeners;
import com.techbucketdivision.mystcraft.menu.BookMenu;
import com.techbucketdivision.mystcraft.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Base of Descriptive and Linking Books (REQUIREMENTS §2.3). Link target lives in {@link ModDataComponents#LINK_INFO},
 * float health in {@link ModDataComponents#BOOK_HEALTH}.
 */
public abstract class LinkingItem extends Item implements ItemBehaviours.PortalActivator, ItemBehaviours.Renameable {

    public static final String SOUND_PORTAL_LINK = "mystcraft:linking.link_portal";

    protected LinkingItem(Item.Properties properties) {
        super(properties);
    }

    // --- link info ---------------------------------------------------------------------------------------------

    public static LinkInfo getLinkInfo(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.LINK_INFO.get(), LinkInfo.EMPTY);
    }

    public static void setLinkInfo(ItemStack stack, LinkInfo info) {
        stack.set(ModDataComponents.LINK_INFO.get(), info);
    }

    public static boolean hasLinkInfo(ItemStack stack) {
        return stack.has(ModDataComponents.LINK_INFO.get());
    }

    public static boolean isLinkingItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof LinkingItem;
    }

    // --- health (REQUIREMENTS §19.2) ----------------------------------------------------------------------------

    public static BookHealth getBookHealth(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.BOOK_HEALTH.get(), BookHealth.FULL);
    }

    public static float getHealth(ItemStack stack) {
        return getBookHealth(stack).health();
    }

    public static float getMaxHealth(ItemStack stack) {
        return getBookHealth(stack).maxHealth();
    }

    public static void setHealth(ItemStack stack, float health) {
        if (stack.isEmpty()) return;
        stack.set(ModDataComponents.BOOK_HEALTH.get(), getBookHealth(stack).withHealth(health));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getBookHealth(stack).isDamaged();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        BookHealth h = getBookHealth(stack);
        if (h.maxHealth() <= 0) return 0;
        return Math.round(13f * h.health() / h.maxHealth());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        BookHealth h = getBookHealth(stack);
        float f = h.maxHealth() <= 0 ? 0 : Math.max(0f, h.health() / h.maxHealth());
        return Mth.hsvToRgb(f / 3f, 1f, 1f);
    }

    /** Books are not vanilla-damageable: health is tracked by {@link BookHealth}. */
    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isCombineRepairable(ItemStack stack) {
        return false;
    }

    // --- lifecycle ---------------------------------------------------------------------------------------------

    /** Creates the missing components for a stack that has none (creative / command spawned). */
    protected abstract void initialize(@Nullable ServerLevel level, ItemStack stack, @Nullable Entity entity);

    /** Ensures every required component exists. */
    public void validate(@Nullable ServerLevel level, ItemStack stack, @Nullable Entity entity) {
        if (!hasLinkInfo(stack)) initialize(level, stack, entity);
        if (!stack.has(ModDataComponents.BOOK_HEALTH.get())) {
            stack.set(ModDataComponents.BOOK_HEALTH.get(), BookHealth.FULL);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (level.isClientSide()) return;
        validate(level, stack, owner);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        if (hasLinkInfo(stack)) {
            String name = getLinkInfo(stack).displayName();
            if (!name.isEmpty()) builder.accept(Component.literal(name));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getLinkInfo(stack).hasFlag(LinkProperty.FOLLOWING);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) {
            BookMenu.openForHeldBook(serverPlayer, hand);
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.PASS;
    }

    // --- linking -----------------------------------------------------------------------------------------------

    /**
     * Links {@code entity} using this book (book GUI "Link", lecterns, stands, book entities). Server only. Updates the
     * stored target UUID for old books, leaves the book behind unless {@link LinkProperty#FOLLOWING} is set.
     */
    public void activate(ItemStack stack, ServerLevel level, Entity entity) {
        if (level.isClientSide() || !hasLinkInfo(stack)) return;
        LinkInfo info = getLinkInfo(stack);
        if (!info.isBound()) return;
        if (!LinkListeners.isLinkPermitted(level, entity, info)) return;
        MinecraftServer server = level.getServer();
        if (server != null && info.dimension().isPresent()) {
            AgeData data = AgeManager.get(server, info.dimension().get());
            if (data != null && info.targetUuid().isEmpty()) {
                info = info.withTargetUuid(data.uuid());
                setLinkInfo(stack, info);
            }
        }
        onLink(stack, level, entity);
        LinkController.travelEntity(entity, info);
    }

    /** Drops the book as a {@link LinkbookEntity} at the player's position and clears the held slot. */
    protected void onLink(ItemStack stack, ServerLevel level, Entity entity) {
        if (!(entity instanceof Player player)) return;
        if (!dropItemOnLink(stack)) return;
        InteractionHand hand = null;
        if (player.getItemInHand(InteractionHand.MAIN_HAND) == stack) hand = InteractionHand.MAIN_HAND;
        else if (player.getItemInHand(InteractionHand.OFF_HAND) == stack) hand = InteractionHand.OFF_HAND;
        if (hand == null) return; // only books actually held are left behind
        LinkbookEntity.spawnFor(level, player, stack.copy());
        player.setItemInHand(hand, ItemStack.EMPTY);
    }

    public boolean dropItemOnLink(ItemStack stack) {
        return !getLinkInfo(stack).hasFlag(LinkProperty.FOLLOWING);
    }

    @Override
    public void onPortalCollision(ItemStack stack, Level level, Entity entity, BlockPos portalPos) {
        if (level.isClientSide()) return;
        prepareLink(stack, level);
        LinkInfo info = getLinkInfo(stack)
                .withFlag(LinkProperty.MAINTAIN_MOMENTUM, true)
                .withFlag(LinkProperty.GENERATE_PLATFORM, false)
                .withFlag(LinkProperty.EXTERNAL, true)
                .withProp(LinkProperty.PROP_SOUND, SOUND_PORTAL_LINK);
        LinkController.travelEntity(entity, info);
    }

    /** Hook run before any portal link; Descriptive Books bind to a new Age here (as {@code activate} does). */
    protected void prepareLink(ItemStack stack, Level level) {}

    @Override
    public int getPortalColor(ItemStack stack, Level level) {
        return getLinkColor(getLinkInfo(stack));
    }

    /** REQUIREMENTS §7.6: colour derived from the display-name hash, packed as coded in the original. */
    public static int getLinkColor(@Nullable LinkInfo info) {
        if (info == null) return 0x000000;
        Random rand = new Random(info.displayName().hashCode());
        int color = 0;
        color += rand.nextInt(256);
        color += rand.nextInt(256) << 8;
        color += rand.nextInt(256) << 16;
        return color;
    }

    // --- metadata ----------------------------------------------------------------------------------------------

    public String getTitle(ItemStack stack) {
        return hasLinkInfo(stack) ? getLinkInfo(stack).displayName() : "";
    }

    public List<String> getAuthors(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.AUTHORS.get(), List.of());
    }

    public @Nullable ResourceKey<Level> getTargetDimension(ItemStack stack) {
        return getLinkInfo(stack).dimension().orElse(null);
    }

    @Override
    public @Nullable String getDisplayName(ItemStack stack) {
        return hasLinkInfo(stack) ? getLinkInfo(stack).displayName() : null;
    }

    @Override
    public void setDisplayName(Player player, ItemStack stack, String name) {
        setLinkInfo(stack, getLinkInfo(stack).withDisplayName(name));
    }
}
