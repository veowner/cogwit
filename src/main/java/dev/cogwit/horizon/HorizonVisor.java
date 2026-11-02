package dev.cogwit.horizon;

import dev.cogwit.CogwitComponents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/**
 * The helmet's faceplate: it swings up over the brow on request and on its own while the wearer eats or drinks.
 * Whether it was raised is kept on the helmet, so everyone who sees the wearer sees it the same way.
 */
public final class HorizonVisor {
    private HorizonVisor() {}

    public static boolean raised(ItemStack helmet) {
        return helmet.getOrDefault(CogwitComponents.VISOR_OPEN.get(), false);
    }

    /** Whether the faceplate should be up right now: raised by hand, or lifted for a meal. */
    public static boolean shouldBeOpen(LivingEntity wearer, ItemStack helmet) {
        if (raised(helmet)) {
            return true;
        }
        if (!wearer.isUsingItem()) {
            return false;
        }
        UseAnim use = wearer.getUseItem().getUseAnimation();
        return use == UseAnim.EAT || use == UseAnim.DRINK;
    }

    /** Raises or lowers the faceplate of the Horizon helmet the player wears; nothing without one. */
    public static void toggle(Player player) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(helmet.getItem() instanceof HorizonArmorItem)) {
            return;
        }
        boolean open = !raised(helmet);
        helmet.set(CogwitComponents.VISOR_OPEN.get(), open);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                open ? HorizonSounds.VISOR_OPEN.get() : HorizonSounds.VISOR_CLOSE.get(), SoundSource.PLAYERS, 0.8f, 1f);
    }
}
