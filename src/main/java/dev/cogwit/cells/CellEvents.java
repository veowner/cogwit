package dev.cogwit.cells;

import dev.cogwit.CogwitMod;
import dev.cogwit.Speech;
import dev.cogwit.horizon.HorizonContent;
import dev.cogwit.horizon.HorizonTips;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;

@EventBusSubscriber(modid = CogwitMod.MODID)
public final class CellEvents {
    /** How long Cogwit stays overcharged after a lightning strike. */
    static final int OVERCHARGE_TICKS = 1200;

    private CellEvents() {}

    /** Lightning fills every worn cell; with the full set the armor drinks the bolt instead of you. */
    @SubscribeEvent
    public static void onLightning(EntityStruckByLightningEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        int cells = CellBank.fillAll(player);
        if (cells == 0) {
            return;
        }
        if (HorizonContent.has(player, dev.cogwit.horizon.Horizon.Feature.CORE)) {
            event.setCanceled(true);
        }
        if (CogwitTalismanItem.worn(player).isPresent()) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, OVERCHARGE_TICKS, 1));
            Speech.say(player, Component.translatable("cogwit.cell.lightning_cogwit", cells));
        } else {
            HorizonTips.say(player, Component.translatable("cogwit.cell.lightning", cells));
        }
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(),
                    60, 0.6, 1.0, 0.6, 0.2);
        }
    }
}
