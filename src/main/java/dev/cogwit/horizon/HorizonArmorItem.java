package dev.cogwit.horizon;

import dev.cogwit.CogwitMod;
import dev.cogwit.Speech;
import dev.cogwit.cells.CellBank;
import dev.cogwit.cells.CellContent;
import dev.cogwit.cells.Cells;
import dev.cogwit.cells.TokenCellItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

/** Event Horizon armor piece rendered as a 3D GeckoLib model: dark plate, a cape and a living black-hole core. */
public class HorizonArmorItem extends ArmorItem implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.horizon.idle");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.horizon.fly");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.horizon.run");
    private static final RawAnimation EQUIP = RawAnimation.begin().thenPlay("animation.horizon.equip");
    public static final String CONTROLLER = "main";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public HorizonArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
        GeoItem.registerSyncedAnimatable(this);
    }

    /** Plays the suit-up animation on this piece for everyone who can see the wearer. */
    public void playEquip(LivingEntity wearer, ItemStack stack, ServerLevel level) {
        triggerArmorAnim(wearer, GeoItem.getOrAssignId(stack, level), CONTROLLER, "equip");
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoArmorRenderer<?> renderer;

            @Override
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(T entity, ItemStack stack,
                    EquipmentSlot slot, HumanoidModel<T> original) {
                if (renderer == null) {
                    renderer = new HorizonArmorRenderer();
                }
                return renderer;
            }
        });
    }

    /** Click a token cell onto the armor to insert it; right click with an empty cursor takes the last one out. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack armor, ItemStack other, Slot slot, ClickAction action,
            Player player, SlotAccess carried) {
        if (!slot.allowModification(player)) {
            return false;
        }
        List<Integer> cells = CellBank.cells(armor);
        if (other.getItem() instanceof TokenCellItem) {
            Cells.insert(cells, CellBank.sockets(armor), TokenCellItem.charge(other)).ifPresentOrElse(updated -> {
                CellBank.setCells(armor, updated);
                other.shrink(1);
                player.playSound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.8f, 1.6f);
            }, () -> {
                if (!player.level().isClientSide) {
                    Speech.hint(player, Component.translatable("cogwit.cell.sockets_full"));
                }
            });
            return true;
        }
        if (other.isEmpty() && action == ClickAction.SECONDARY && !cells.isEmpty()) {
            Cells.Popped popped = Cells.pop(cells).orElseThrow();
            CellBank.setCells(armor, popped.remaining());
            carried.set(TokenCellItem.withCharge(new ItemStack(CellContent.TOKEN_CELL.get()), popped.charge()));
            player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.8f, 1.2f);
            return true;
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        List<Integer> cells = CellBank.cells(stack);
        tooltip.add(Component.translatable("cogwit.cell.sockets", cells.size(), CellBank.sockets(stack),
                Cells.total(cells)).withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, CONTROLLER, 5, state -> {
            Entity wearer = state.getData(DataTickets.ENTITY);
            RawAnimation animation = IDLE;
            if (wearer instanceof Player player) {
                if (player.getAbilities().flying || player.isFallFlying()) {
                    animation = FLY;
                } else if (player.isSprinting()) {
                    animation = RUN;
                }
                // the core's heart races as mass builds up; only the local player's mass is known here
                float mass = player.isLocalPlayer() ? HorizonClientState.latest().mass() : 0f;
                state.getController().setAnimationSpeed(1.0 + 0.8 * mass / Horizon.MASS_MAX);
            }
            return state.setAndContinue(animation);
        }).triggerableAnim("equip", EQUIP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
