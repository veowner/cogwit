package dev.cogwit.exoframe;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * One half of the copper exoframe, worn in its accessory slot over any armor. The upper half lends the arms speed
 * and reach, the lower half lends the legs speed, a taller step and a softer landing; both add a point of armor.
 */
public class ExoframePartItem extends Item implements ICurioItem, GeoItem {
    public enum Half { UPPER, LOWER }

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.exoframe.idle");

    public final Half half;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ExoframePartItem(Half half, Properties properties) {
        super(properties);
        this.half = half;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip,
            net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable(half == Half.UPPER ? "cogwit.exo.tooltip.upper"
                : "cogwit.exo.tooltip.lower").withStyle(net.minecraft.ChatFormatting.GOLD));
        tooltip.add(net.minecraft.network.chat.Component.translatable("cogwit.exo.tooltip.set")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return slotContext.identifier().equals(half == Half.UPPER ? ExoframeContent.UPPER_SLOT : ExoframeContent.LOWER_SLOT);
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(SlotContext slotContext, ResourceLocation id,
            ItemStack stack) {
        ImmutableMultimap.Builder<Holder<Attribute>, AttributeModifier> map = ImmutableMultimap.builder();
        map.put(Attributes.ARMOR, modifier(id, "armor", 1, AttributeModifier.Operation.ADD_VALUE));
        if (half == Half.UPPER) {
            map.put(Attributes.ATTACK_SPEED, modifier(id, "attack_speed", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            map.put(Attributes.BLOCK_BREAK_SPEED, modifier(id, "break_speed", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            map.put(Attributes.BLOCK_INTERACTION_RANGE, modifier(id, "block_reach", 0.5, AttributeModifier.Operation.ADD_VALUE));
            map.put(Attributes.ENTITY_INTERACTION_RANGE, modifier(id, "entity_reach", 0.5, AttributeModifier.Operation.ADD_VALUE));
        } else {
            map.put(Attributes.MOVEMENT_SPEED, modifier(id, "speed", 0.08, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            map.put(Attributes.STEP_HEIGHT, modifier(id, "step", 0.4, AttributeModifier.Operation.ADD_VALUE));
            map.put(Attributes.SAFE_FALL_DISTANCE, modifier(id, "safe_fall", 2, AttributeModifier.Operation.ADD_VALUE));
        }
        return map.build();
    }

    private static AttributeModifier modifier(ResourceLocation id, String what, double amount,
            AttributeModifier.Operation operation) {
        return new AttributeModifier(id.withSuffix("/" + what), amount, operation);
    }

    /** Each worn frame gets its own animation id, so two players' frames never share one. */
    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        if (slotContext.entity().level() instanceof ServerLevel level) {
            GeoItem.getOrAssignId(stack, level);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
