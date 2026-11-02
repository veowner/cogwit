package dev.cogwit.agent;

import dev.cogwit.CogwitMod;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = CogwitMod.MODID)
public final class AgentEvents {
    private AgentEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide && player.isAlive() && CogwitTalismanItem.worn(player).isPresent()) {
            AgentService.tick(player);
        }
    }

    /** Hits either way keep the "in a fight" clock running. */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof Player victim) {
            AgentService.noteFight(victim);
        }
        if (event.getSource().getEntity() instanceof Player attacker) {
            AgentService.noteFight(attacker);
        }
    }

    /** Every hit the player lands goes into their fighting-style profile. */
    @SubscribeEvent
    public static void onDamageDealt(LivingDamageEvent.Post event) {
        if (event.getEntity().level().isClientSide || !(event.getSource().getEntity() instanceof Player attacker)
                || event.getEntity() == attacker || event.getEntity() instanceof Player) {
            return;
        }
        StyleProfile.Kind kind = AgentService.kindOf(event.getSource(), attacker);
        if (kind != null) {
            AgentService.recordStyle(attacker, kind, event.getNewDamage());
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        BlockState state = event.getState();
        Player player = event.getPlayer();
        if (player.level().isClientSide || CogwitTalismanItem.worn(player).isEmpty()) {
            return;
        }
        boolean grown = state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)
                || state.getBlock() instanceof NetherWartBlock && state.getValue(NetherWartBlock.AGE) >= 3;
        if (grown) {
            AgentService.harvested(player, event.getPos(), state);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        AgentService.forget(event.getEntity());
        dev.cogwit.mind.DeathCompass.forget(event.getEntity().getUUID());
        dev.cogwit.speech.CogwitVoice.forget(event.getEntity().getUUID());
    }
}
