package dev.cogwit.growth;

import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Food for you, and the only thing that wakes Cogwit early. Eating it shares the apple with him. */
public class CopperAppleItem extends Item {
    public CopperAppleItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack left = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide && entity instanceof Player player) {
            CogwitTalismanItem.worn(player).ifPresent(cogwit ->
                    CogwitTalismanItem.feed(player, cogwit, new ItemStack(this), 1));
        }
        return left;
    }
}
