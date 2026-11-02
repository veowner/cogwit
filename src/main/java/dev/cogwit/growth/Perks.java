package dev.cogwit.growth;

import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.Growth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/** What Cogwit's levels do every second while he is awake. */
public final class Perks {
    /** How far Cogwit's eye spots mobs that are after you. */
    public static final double EYE_RANGE = 24;
    public static final double HIGHLIGHT_RANGE = 16;

    private Perks() {}

    public static void tick(Player player, BrainState s) {
        if (Growth.has(s.xp(), Growth.Perk.EYE)) {
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(EYE_RANGE),
                    mob -> mob.getTarget() == player && mob.isAlive())) {
                mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false));
            }
        }
        if (Growth.has(s.xp(), Growth.Perk.HIGHLIGHT)) {
            for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class,
                    player.getBoundingBox().inflate(HIGHLIGHT_RANGE),
                    item -> !item.hasGlowingTag() && isValuable(item.getItem()))) {
                item.setGlowingTag(true);
            }
        }
    }

    public static boolean isValuable(ItemStack stack) {
        return stack.is(GrowthContent.VALUABLE) || stack.isEnchanted()
                || stack.getRarity() == Rarity.RARE || stack.getRarity() == Rarity.EPIC;
    }
}
