package dev.cogwit;

import dev.cogwit.brain.Brain;
import dev.cogwit.brain.ThinkResult;
import dev.cogwit.cells.CellBank;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Things the player asks Cogwit to do on purpose. */
public final class CogwitActions {
    public static final double THINK_RADIUS = 8.0;

    private CogwitActions() {}

    /** Tactical focus: nearby enemies slow down while the player gets stronger for a few seconds. */
    public static void think(Player player) {
        Optional<ItemStack> worn = CogwitTalismanItem.worn(player);
        if (worn.isEmpty()) {
            Speech.hint(player, Component.translatable("cogwit.command.not_worn"));
            return;
        }
        long now = player.level().getGameTime();
        CellBank.topUp(player, worn.get());
        ThinkResult result = Brain.think(CogwitTalismanItem.brain(worn.get()), now);
        switch (result.status()) {
            case OK -> {
                int before = CogwitTalismanItem.brain(worn.get()).tokens();
                CogwitTalismanItem.setBrain(worn.get(), result.state());
                CogwitTalismanItem.warnIfHungry(player, before, result.state().tokens());
                int duration = (int) Brain.THINK_DURATION_TICKS;
                for (Mob mob : player.level().getEntitiesOfClass(Mob.class,
                        player.getBoundingBox().inflate(THINK_RADIUS), mob -> mob instanceof Enemy)) {
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 2), player);
                }
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, 0));
                player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                        SoundSource.PLAYERS, 1f, 1.2f);
                if (player.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY() + 1, player.getZ(),
                            40, THINK_RADIUS / 2, 0.5, THINK_RADIUS / 2, 0.1);
                }
                Speech.hint(player, Component.translatable("cogwit.hint.thinking"));
            }
            case COOLDOWN -> Speech.hint(player, Component.translatable("cogwit.hint.think_cooldown",
                    Brain.clock(CogwitTalismanItem.brain(worn.get()).thinkReadyAt() - now)));
            case NO_TOKENS -> Speech.hint(player, Component.translatable("cogwit.hint.no_tokens"));
            case ASLEEP -> Speech.hint(player, Component.translatable("cogwit.hint.asleep"));
        }
    }
}
