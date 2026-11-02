package dev.cogwit.exoframe;

import dev.cogwit.Speech;
import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** What the copper exoframe does when both halves are worn: a short steam-driven shove, paid for with Cogwit's tokens. */
public final class ExoframeActions {
    public static final int SHOVE_COST = 20;
    public static final long SHOVE_COOLDOWN_TICKS = 80;
    public static final double SHOVE_SPEED = 1.15, SHOVE_LIFT = 0.22;
    private static final String READY_AT = "cogwit_exo_shove_ready_at";

    private ExoframeActions() {}

    /** Returns whether the shove fired. */
    public static boolean shove(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !ExoframeContent.wearsBoth(player)) {
            return false;
        }
        long now = level.getGameTime();
        long readyAt = player.getPersistentData().getLong(READY_AT);
        if (now < readyAt) {
            Speech.hint(player, Component.translatable("cogwit.horizon.cooldown", Brain.clock(readyAt - now)));
            return false;
        }
        ItemStack talisman = CogwitTalismanItem.worn(player).orElse(null);
        if (talisman == null) {
            Speech.hint(player, Component.translatable("cogwit.exo.no_cogwit"));
            return false;
        }
        BrainState brain = CogwitTalismanItem.brain(talisman);
        if (brain.tokens() < SHOVE_COST) {
            Speech.hint(player, Component.translatable("cogwit.exo.no_tokens"));
            return false;
        }
        CogwitTalismanItem.setBrain(talisman, brain.withTokens(brain.tokens() - SHOVE_COST));
        player.getPersistentData().putLong(READY_AT, now + SHOVE_COOLDOWN_TICKS);
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, player.getYRot()) : flat.normalize();
        player.setDeltaMovement(flat.x * SHOVE_SPEED, Math.max(player.getDeltaMovement().y, 0) + SHOVE_LIFT, flat.z * SHOVE_SPEED);
        player.hurtMarked = true;
        player.resetFallDistance();
        // steam bursts from the back and the heels
        Vec3 back = player.position().subtract(flat.scale(0.4));
        level.sendParticles(ParticleTypes.CLOUD, back.x, back.y + 1.1, back.z, 10, 0.15, 0.2, 0.15, 0.03);
        level.sendParticles(ParticleTypes.POOF, back.x, back.y + 0.2, back.z, 8, 0.2, 0.05, 0.2, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.5f, 1.3f);
        return true;
    }
}
