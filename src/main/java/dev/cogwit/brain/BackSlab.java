package dev.cogwit.brain;

import dev.cogwit.CogwitMod;
import dev.cogwit.Speech;
import dev.cogwit.horizon.Horizon;
import dev.cogwit.item.CogwitTalismanItem;
import dev.cogwit.horizon.fx.SkillFx;
import dev.cogwit.trophy.DisplayFx;
import dev.cogwit.trophy.TrophyScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Optional;

/**
 * Cogwit's back shield as something you can see: a slab tears out of the ground behind you, takes the blow or the
 * arrow, cracks and sinks back. Stone, sandstone or ice depending on the ground; copper from Cogwit's fifth level.
 * Arrows are caught before they arrive; melee blows when they land (see {@code CogwitEvents}).
 */
@EventBusSubscriber(modid = CogwitMod.MODID)
public final class BackSlab {
    public static final float WIDTH = 1.7f, DEPTH = 0.35f, HEIGHT = 2.1f;
    /** How far behind you the slab stands, and how close an arrow has to be before it is caught. */
    public static final double DISTANCE = 1.0, CATCH_RANGE = 4.5;
    public static final int GROW_TICKS = 3, HOLD_TICKS = 16, SINK_TICKS = 5;

    private BackSlab() {}

    /** What the slab is made of: copper at level 5, else whatever fits the ground under your feet. */
    static BlockState material(Player player, int level) {
        if (level >= 5) {
            return Blocks.WAXED_CUT_COPPER.defaultBlockState();
        }
        BlockState ground = player.level().getBlockState(player.blockPosition().below());
        if (ground.is(BlockTags.SAND)) {
            return Blocks.SANDSTONE.defaultBlockState();
        }
        if (ground.is(BlockTags.SNOW) || ground.is(BlockTags.ICE)) {
            return Blocks.PACKED_ICE.defaultBlockState();
        }
        if (ground.is(BlockTags.BASE_STONE_NETHER)) {
            return ground;
        }
        if (ground.is(Blocks.DEEPSLATE) || player.getY() < 0 && !player.level().canSeeSky(player.blockPosition())) {
            return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        }
        return Blocks.COBBLESTONE.defaultBlockState();
    }

    /** Raises a slab between you and {@code from}; a melee attacker standing there is shoved back. */
    public static void raise(Player player, Vec3 from, int cogwitLevel) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 dir = new Vec3(from.x - player.getX(), 0, from.z - player.getZ());
        dir = dir.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, player.getYRot() + 180) : dir.normalize();
        Vec3 base = player.position().add(dir.scale(DISTANCE));
        BlockState block = material(player, cogwitLevel);
        float yaw = (float) Math.atan2(dir.x, dir.z);
        var slab = DisplayFx.wall(level, block, base, WIDTH, DEPTH, HEIGHT, yaw, GROW_TICKS);
        var dust = new BlockParticleOption(ParticleTypes.BLOCK, block);
        SkillFx.particles(level, dust, base.x, base.y + 0.2, base.z, 30, 0.6, 0.1, 0.6, 0.15);
        SkillFx.particles(level, ParticleTypes.POOF, base.x, base.y + 0.1, base.z, 6, 0.5, 0.05, 0.5, 0.02);
        level.playSound(null, BlockPos.containing(base), block.getSoundType().getPlaceSound(), SoundSource.PLAYERS,
                1.2f, 0.6f);
        level.playSound(null, BlockPos.containing(base), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 0.8f);
        TrophyScheduler.later(level, HOLD_TICKS, () -> {
            SkillFx.particles(level, dust, base.x, base.y + 1.1, base.z, 24, 0.5, 0.6, 0.5, 0.1);
            level.playSound(null, BlockPos.containing(base), block.getSoundType().getBreakSound(), SoundSource.PLAYERS,
                    1f, 0.8f);
            DisplayFx.sinkWall(level, slab, WIDTH, DEPTH, SINK_TICKS);
        });
    }

    /** Pays for the shield and raises it; false when Cogwit can't (locked, asleep, recharging, no tokens). */
    public static boolean tryRaise(Player player, ItemStack talisman, Vec3 from, long now) {
        BrainState before = CogwitTalismanItem.brain(talisman);
        Optional<BrainState> paid = Brain.backShield(before, now);
        if (paid.isEmpty()) {
            return false;
        }
        CogwitTalismanItem.setBrain(talisman, paid.get());
        CogwitTalismanItem.warnIfHungry(player, before.tokens(), paid.get().tokens());
        raise(player, from, Growth.level(paid.get().xp()));
        return true;
    }

    /** Catches arrows and other shots flying at your back before they arrive. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !player.isAlive() || player.isSpectator()) {
            return;
        }
        Optional<ItemStack> worn = CogwitTalismanItem.worn(player);
        if (worn.isEmpty()) {
            return;
        }
        long now = player.level().getGameTime();
        BrainState brain = CogwitTalismanItem.brain(worn.get());
        if (!Growth.has(brain.xp(), Growth.Perk.BACK_SHIELD) || now < brain.backShieldReadyAt()) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 centre = player.getBoundingBox().getCenter();
        for (Projectile shot : player.level().getEntitiesOfClass(Projectile.class,
                player.getBoundingBox().inflate(CATCH_RANGE), p -> p.getOwner() != player && p.isAlive())) {
            Vec3 v = shot.getDeltaMovement();
            Vec3 at = shot.position();
            boolean behind = Brain.isBehind(look.x, look.z, at.x - player.getX(), at.z - player.getZ());
            if (!behind || v.lengthSqr() < 0.04
                    || !Horizon.isIncoming(at.x, at.y, at.z, v.x, v.y, v.z, centre.x, centre.y, centre.z)
                    || shot.getOwner() instanceof Player) {
                continue;
            }
            if (tryRaise(player, worn.get(), at, now)) {
                if (player.level() instanceof ServerLevel level) {
                    SkillFx.particles(level, ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.1, 0.1, 0.1, 0.1);
                }
                shot.discard();
                Speech.think(player, Component.translatable("cogwit.think.slab_shot",
                        shot.getOwner() instanceof LivingEntity shooter ? shooter.getDisplayName()
                                : Component.translatable("cogwit.think.someone")));
            }
            return;
        }
    }
}
