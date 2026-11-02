package dev.cogwit.trophy;

import dev.cogwit.Speech;
import dev.cogwit.horizon.HorizonActions;
import dev.cogwit.horizon.Horizon;
import dev.cogwit.horizon.HorizonContent;
import dev.cogwit.horizon.fx.SkillFx;
import dev.cogwit.network.ScreenFxPayload;
import dev.cogwit.network.TrophySyncPayload;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server side of trophies: who earns them, keeping them in sync, their passives and their actives. */
public final class TrophyService {
    /** Last hit on each boss by each player, so the kill counts for everyone who fought. */
    private static final Map<UUID, Map<UUID, Long>> HITS = new ConcurrentHashMap<>();
    /** The Elder Guardian laser while it charges: who, at what, since when. */
    private record Beam(Entity target, long started) {}

    private static final Map<UUID, Beam> BEAMS = new ConcurrentHashMap<>();
    public static final int LASER_CHARGE_TICKS = 20;
    public static final float LASER_DAMAGE = 14f, SONIC_DAMAGE = 10f;
    public static final double SONIC_RANGE = 15, LASER_RANGE = 24, BREATH_RANGE = 20;
    private static final ResourceLocation ELDER_SWIM = dev.cogwit.CogwitMod.id("trophy_elder_swim");
    private static final float DRAGON_FLY_SPEED = 0.075f, DEFAULT_FLY_SPEED = 0.05f;
    private static final ResourceLocation MONSTROSITY_KB = dev.cogwit.CogwitMod.id("trophy_monstrosity_kb");
    public static final float MALEDICTUS_DODGE = 0.2f;

    private TrophyService() {}

    // ---------------------------------------------------------------- owning

    public static Set<Trophy> owned(Player player) {
        Set<Trophy> set = EnumSet.noneOf(Trophy.class);
        for (String key : player.getData(TrophyContent.DATA).owned()) {
            Trophy.byKey(key).ifPresent(set::add);
        }
        return set;
    }

    public static boolean has(Player player, Trophy trophy) {
        return player.getData(TrophyContent.DATA).owned().contains(trophy.key());
    }

    /** Your passives plus those of players nearby with a Horizon core, but only while your own chest holds one. */
    public static Set<Trophy> passives(Player player) {
        if (!HorizonContent.has(player, Horizon.Feature.CORE)) {
            return Set.of();
        }
        List<Set<Trophy>> nearby = new ArrayList<>();
        for (Player other : player.level().getEntitiesOfClass(Player.class,
                player.getBoundingBox().inflate(Trophies.SHARE_RANGE),
                p -> p != player && p.isAlive() && HorizonContent.has(p, Horizon.Feature.CORE))) {
            nearby.add(owned(other));
        }
        return Trophies.passives(owned(player), nearby);
    }

    public static boolean hasPassive(Player player, Trophy trophy) {
        return passives(player).contains(trophy);
    }

    /** Gives a trophy: the armor absorbs the boss. Returns false if it was already owned. */
    public static boolean award(Player player, Trophy trophy) {
        TrophyContent.Data data = player.getData(TrophyContent.DATA);
        if (data.owned().contains(trophy.key())) {
            return false;
        }
        List<String> owned = new ArrayList<>(data.owned());
        owned.add(trophy.key());
        player.setData(TrophyContent.DATA, new TrophyContent.Data(owned, data.readyAt()));
        Component name = Component.translatable("cogwit.trophy." + trophy.key() + ".name");
        Speech.say(player, Component.translatable("cogwit.trophy.gained", name,
                Component.translatable("cogwit.trophy." + trophy.key() + ".passive")));
        if (trophy.hasActive && trophy.implemented) {
            Speech.hint(player, Component.translatable("cogwit.trophy.gained_active", name));
        }
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.7f, 1.4f);
            SkillFx.implode(level, player.position().add(0, 1, 0), 3, 80);
            if (player instanceof ServerPlayer sp) {
                SkillFx.screen(sp, ScreenFxPayload.FLASH_VIOLET, 0.5f, 14);
            }
        }
        sync(player);
        return true;
    }

    /** Sends a player's trophies to everyone who can see them, cooldowns only to the player. */
    public static void sync(Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        TrophyContent.Data data = player.getData(TrophyContent.DATA);
        List<Long> ready = new ArrayList<>();
        for (String key : data.owned()) {
            ready.add(data.readyAt().getOrDefault(key, 0L));
        }
        PacketDistributor.sendToPlayer(sp, new TrophySyncPayload(player.getId(), data.owned(), ready, true));
        PacketDistributor.sendToPlayersTrackingEntity(player, new TrophySyncPayload(player.getId(), data.owned(),
                List.of(), false));
    }

    /** Sends one player's trophies to another who just started seeing them. */
    public static void syncTo(ServerPlayer viewer, Player owner) {
        PacketDistributor.sendToPlayer(viewer, new TrophySyncPayload(owner.getId(),
                owner.getData(TrophyContent.DATA).owned(), List.of(), false));
    }

    // ---------------------------------------------------------------- earning

    public static void noteHit(LivingEntity boss, Player attacker) {
        HITS.computeIfAbsent(boss.getUUID(), id -> new ConcurrentHashMap<>())
                .put(attacker.getUUID(), boss.level().getGameTime());
    }

    public static void bossDied(LivingEntity boss, Entity killer) {
        Optional<Trophy> trophy = Trophy.byEntity(BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).toString());
        Map<UUID, Long> hits = HITS.remove(boss.getUUID());
        if (trophy.isEmpty() || boss.getServer() == null) {
            return;
        }
        UUID killerId = killer instanceof Player p ? p.getUUID() : null;
        for (UUID id : Trophies.credited(hits == null ? Map.of() : hits, killerId, boss.level().getGameTime())) {
            Player player = boss.getServer().getPlayerList().getPlayer(id);
            if (player == null && boss.level() instanceof ServerLevel level
                    && level.getEntity(id) instanceof Player inWorld) {
                player = inWorld;
            }
            if (player != null) {
                award(player, trophy.get());
            }
        }
    }

    // ---------------------------------------------------------------- passives

    /** Twice a second: the passives that need keeping up. Immunities are handled in the effect event. */
    public static void tickPassives(Player player) {
        Set<Trophy> passives = passives(player);
        // Ender Dragon: faster flight
        float fly = passives.contains(Trophy.ENDER_DRAGON) ? DRAGON_FLY_SPEED : DEFAULT_FLY_SPEED;
        if (player.getAbilities().getFlyingSpeed() != fly && !player.isCreative()) {
            player.getAbilities().setFlyingSpeed(fly);
            player.onUpdateAbilities();
        }
        // Warden: hostile mobs nearby show through walls
        if (passives.contains(Trophy.WARDEN)) {
            for (LivingEntity mob : player.level().getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(20), e -> e instanceof Enemy && e.isAlive())) {
                mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 15, 0, false, false));
            }
            player.removeEffect(MobEffects.DARKNESS);
        }
        if (passives.contains(Trophy.WITHER)) {
            player.removeEffect(MobEffects.WITHER);
        }
        if (passives.contains(Trophy.SANDWORM)) {
            player.removeEffect(MobEffects.POISON);
        }
        TrophyPassives.tick(player, passives);
        // Elder Guardian: breathing and speed underwater, and the heavy armor no longer drags you down
        AttributeInstance swim = player.getAttribute(NeoForgeMod.SWIM_SPEED);
        boolean elder = passives.contains(Trophy.ELDER_GUARDIAN);
        if (swim != null) {
            if (elder && !swim.hasModifier(ELDER_SWIM)) {
                swim.addTransientModifier(new AttributeModifier(ELDER_SWIM, 1.0,
                        AttributeModifier.Operation.ADD_VALUE));
            } else if (!elder && swim.hasModifier(ELDER_SWIM)) {
                swim.removeModifier(ELDER_SWIM);
            }
        }
        if (elder && player.isInWater()) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false));
        }
        // Leviathan: a torpedo underwater
        if (passives.contains(Trophy.LEVIATHAN) && player.isInWater()) {
            player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 30, 1, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false));
        }
        // Ignis: fire and lava don't burn
        if (passives.contains(Trophy.IGNIS)) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, true, false));
        }
        // Netherite Monstrosity: an immovable wall
        AttributeInstance kb = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        boolean monstrosity = passives.contains(Trophy.NETHERITE_MONSTROSITY);
        if (kb != null) {
            if (monstrosity && !kb.hasModifier(MONSTROSITY_KB)) {
                kb.addTransientModifier(new AttributeModifier(MONSTROSITY_KB, 1.0, AttributeModifier.Operation.ADD_VALUE));
            } else if (!monstrosity && kb.hasModifier(MONSTROSITY_KB)) {
                kb.removeModifier(MONSTROSITY_KB);
            }
        }
    }

    /** Ignis: lava under your feet cools into magma while you stand on it, and melts back after you leave. */
    public static void walkOnLava(Player player) {
        if (!(player.level() instanceof ServerLevel level) || player.isShiftKeyDown()) {
            return;
        }
        net.minecraft.core.BlockPos below = player.blockPosition().below();
        if (!level.getFluidState(below).is(net.minecraft.tags.FluidTags.LAVA) && !player.isInLava()) {
            return;
        }
        net.minecraft.core.BlockPos centre = player.isInLava() ? player.blockPosition() : below;
        for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(centre.offset(-2, 0, -2),
                centre.offset(2, 0, 2))) {
            if (pos.distSqr(centre) > 5 || !level.getFluidState(pos).isSource()
                    || !level.getFluidState(pos).is(net.minecraft.tags.FluidTags.LAVA)
                    || !level.getBlockState(pos.above()).isAir() && !player.isInLava()) {
                continue;
            }
            net.minecraft.core.BlockPos fixed = pos.immutable();
            level.setBlockAndUpdate(fixed, net.minecraft.world.level.block.Blocks.MAGMA_BLOCK.defaultBlockState());
            TrophyScheduler.later(level, 100, () -> {
                if (level.getBlockState(fixed).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)) {
                    level.setBlockAndUpdate(fixed, net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState());
                }
            });
        }
        if (player.isInLava()) {
            player.setDeltaMovement(player.getDeltaMovement().add(0, 0.25, 0));
            player.hurtMarked = true;
        }
    }

    /**
     * Damage passives for a trophy owner being hurt: returns true when the hit should be cancelled outright.
     */
    public static boolean shrugOff(Player player, net.minecraft.world.damagesource.DamageSource source) {
        Set<Trophy> passives = passives(player);
        if (passives.isEmpty() || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        if (passives.contains(Trophy.HARBINGER) && source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
                || passives.contains(Trophy.NETHERITE_MONSTROSITY)
                && source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
                || passives.contains(Trophy.IGNIS) && source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                || passives.contains(Trophy.FROSTMAW) && source.is(net.minecraft.world.damagesource.DamageTypes.FREEZE)
                || passives.contains(Trophy.ARCHEVOKER)
                && source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.EvokerFangs
                || passives.contains(Trophy.SCYLLA) && source.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING)
                || passives.contains(Trophy.ANCIENT_REMNANT) && (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.DROWN))) {
            return true;
        }
        if (passives.contains(Trophy.MALEDICTUS) && player.getRandom().nextFloat() < MALEDICTUS_DODGE) {
            // a ghostly sidestep: the hit passes through an afterimage
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 8, 0.3, 0.5,
                        0.3, 0.02);
                level.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8f, 1.6f);
            }
            return true;
        }
        return passives.contains(Trophy.LICH) && TrophyPassives.lichShield(player, source);
    }

    /** Ender Guardian: a melee hit drags the enemy towards you. */
    public static void meleePull(Player attacker, LivingEntity target) {
        if (!passives(attacker).contains(Trophy.ENDER_GUARDIAN) || target instanceof Player) {
            return;
        }
        Vec3 pull = attacker.position().subtract(target.position()).normalize();
        target.setDeltaMovement(target.getDeltaMovement().add(pull.x * 0.7, 0.15, pull.z * 0.7));
        target.hurtMarked = true;
        if (attacker.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 1, target.getZ(), 10, 0.2,
                    0.4, 0.2, 0.05);
        }
    }

    /** True when the trophy's passive makes an effect harmless for this player. */
    public static boolean immuneTo(Player player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> e) {
        Set<Trophy> passives = passives(player);
        return passives.contains(Trophy.WITHER) && e.is(MobEffects.WITHER)
                || passives.contains(Trophy.WARDEN) && e.is(MobEffects.DARKNESS)
                || passives.contains(Trophy.FROSTMAW) && e.is(MobEffects.MOVEMENT_SLOWDOWN)
                || passives.contains(Trophy.SANDWORM) && e.is(MobEffects.POISON);
    }

    // ---------------------------------------------------------------- actives

    /** The wheel: "blink" and "gravity" are the armor's own skills, anything else names a trophy. */
    public static void cast(Player player, String id) {
        switch (id) {
            case "blink" -> HorizonActions.blink(player);
            case "gravity" -> HorizonActions.gravity(player);
            default -> Trophy.byKey(id).ifPresent(t -> castTrophy(player, t));
        }
    }

    public static boolean castTrophy(Player player, Trophy trophy) {
        if (!has(player, trophy) || !trophy.hasActive || !trophy.implemented) {
            return false;
        }
        if (!HorizonContent.has(player, Horizon.Feature.CORE)) {
            Speech.hint(player, Component.translatable("cogwit.trophy.need_set"));
            return false;
        }
        long now = player.level().getGameTime();
        TrophyContent.Data data = player.getData(TrophyContent.DATA);
        long ready = data.readyAt().getOrDefault(trophy.key(), 0L);
        if (now < ready) {
            Speech.hint(player, Component.translatable("cogwit.trophy.cooldown",
                    Component.translatable("cogwit.trophy." + trophy.key() + ".name"),
                    dev.cogwit.brain.Brain.clock(ready - now)));
            return false;
        }
        boolean done = switch (trophy) {
            case ENDER_DRAGON -> dragonBreath(player);
            case WITHER -> skullVolley(player);
            case WARDEN -> sonicBoom(player);
            case ELDER_GUARDIAN -> laser(player);
            case IGNIS -> CataclysmStrikes.fireDash(player);
            case NETHERITE_MONSTROSITY -> CataclysmStrikes.lavaSlam(player);
            case ENDER_GUARDIAN -> CataclysmStrikes.voidSpikes(player);
            case HARBINGER -> CataclysmStrikes.missiles(player);
            case LEVIATHAN -> CataclysmPowers.abyssBeam(player);
            case ANCIENT_REMNANT -> CataclysmPowers.sandstorm(player);
            case MALEDICTUS -> CataclysmPowers.phantomBlades(player);
            case SCYLLA -> CataclysmPowers.storm(player);
            case FROSTMAW -> MowziePowers.iceBreath(player);
            case UMVUTHI -> MowziePowers.solarBeam(player);
            case SCULPTOR -> MowziePowers.stonePillars(player);
            case SLIDER -> AetherPowers.ram(player);
            case VALKYRIE_QUEEN -> AetherPowers.lightning(player);
            case SUN_SPIRIT -> AetherPowers.ignite(player);
            case DEAD_KING -> SpellbookPowers.raiseDead(player);
            case FIRE_BOSS -> SpellbookPowers.firestorm(player);
            case ARCHEVOKER -> SpellbookPowers.fangs(player);
            case NAGA -> TwilightPowers.coil(player);
            case LICH -> TwilightPowers.soulBolts(player);
            case MINOSHROOM -> TwilightPowers.axeSlam(player);
            case HYDRA -> TwilightPowers.threeHeads(player);
            case KNIGHT_PHANTOM -> TwilightPowers.phantomSwords(player);
            case UR_GHAST -> TwilightPowers.tearBombs(player);
            case ALPHA_YETI -> TwilightPowers.iceBlock(player);
            case SNOW_QUEEN -> TwilightPowers.icePrison(player);
            case INFERNAL_DRAGON -> BossRisePowers.infernalDive(player);
            case KRAKEN -> BossRisePowers.tentacles(player);
            case SANDWORM -> BossRisePowers.sandEruption(player);
            case UNDERWORLD_KNIGHT -> BossRisePowers.riftSlashes(player);
            case YETI -> BossRisePowers.groundPound(player);
            default -> false;
        };
        if (done) {
            Map<String, Long> readyAt = new HashMap<>(data.readyAt());
            readyAt.put(trophy.key(), now + trophy.cooldownTicks);
            player.setData(TrophyContent.DATA, new TrophyContent.Data(data.owned(), readyAt));
            sync(player);
        }
        return done;
    }

    /** Where the player is looking: the first block or entity hit within {@code range}. */
    private static Vec3 aimPoint(Player player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        HitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return block.getLocation();
    }

    private static boolean dragonBreath(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Vec3 target = aimPoint(player, BREATH_RANGE);
        // settle the pool on the ground below the aim point
        Vec3 ground = level.clip(new ClipContext(target.add(0, 0.5, 0), target.subtract(0, 6, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
        Vec3 mouth = player.getEyePosition().subtract(0, 0.2, 0);
        SkillFx.line(level, mouth.add(player.getLookAngle().scale(1.5)), ground.add(0, 0.4, 0),
                ParticleTypes.DRAGON_BREATH, 0.7);
        DragonBreathCloud.breathe(player, ground);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 1f, 1f);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.5f, 1.4f);
        return true;
    }

    private static boolean skullVolley(Player player) {
        Vec3 look = player.getLookAngle();
        for (int i = -2; i <= 2; i++) {
            double yaw = Math.toRadians(i * 9);
            double x = look.x * Math.cos(yaw) - look.z * Math.sin(yaw);
            double z = look.x * Math.sin(yaw) + look.z * Math.cos(yaw);
            TrophySkull.fire(player, x, look.y + Math.abs(i) * 0.02, z);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 1f, 0.9f);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getEyeY(), player.getZ(), 20, 0.3, 0.2, 0.3,
                    0.05);
        }
        return true;
    }

    /** Warden's sonic boom: a straight line through walls and armor, throwing everything on it back. */
    private static boolean sonicBoom(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle();
        for (int i = 1; i <= SONIC_RANGE; i++) {
            Vec3 p = eye.add(dir.scale(i));
            level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        AABB sweep = new AABB(eye, eye.add(dir.scale(SONIC_RANGE))).inflate(1.5);
        for (LivingEntity target : SkillFx.victims(player, player, eye, SONIC_RANGE + 1)) {
            if (!sweep.intersects(target.getBoundingBox())) {
                continue;
            }
            Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
            double along = to.dot(dir);
            if (along < 0 || to.subtract(dir.scale(along)).length() > 1.5) {
                continue;
            }
            target.hurt(level.damageSources().sonicBoom(player), SONIC_DAMAGE);
            target.setDeltaMovement(target.getDeltaMovement().add(dir.x * 1.6, 0.4 + dir.y * 0.5, dir.z * 1.6));
            target.hurtMarked = true;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2f, 1f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.SHAKE, 0.45f, 8);
        }
        return true;
    }

    /** Elder Guardian's laser locks onto the creature under the crosshair, charges for a second, then strikes. */
    private static boolean laser(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(LASER_RANGE));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end,
                player.getBoundingBox().expandTowards(player.getLookAngle().scale(LASER_RANGE)).inflate(1.5),
                e -> e instanceof LivingEntity && e.isAlive() && !(e instanceof Player), LASER_RANGE * LASER_RANGE);
        if (hit == null) {
            Speech.hint(player, Component.translatable("cogwit.trophy.no_target"));
            return false;
        }
        BEAMS.put(player.getUUID(), new Beam(hit.getEntity(), player.level().getGameTime()));
        player.level().playSound(null, player.blockPosition(), SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 1.2f,
                0.8f);
        return true;
    }

    /** Every tick: a charging laser draws its beam, thickening, and fires when charged. */
    public static void tickBeams(Player player) {
        Beam beam = BEAMS.get(player.getUUID());
        if (beam == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Entity target = beam.target();
        long age = level.getGameTime() - beam.started();
        if (!target.isAlive() || target.level() != level || target.distanceTo(player) > LASER_RANGE + 4) {
            BEAMS.remove(player.getUUID());
            return;
        }
        Vec3 from = player.getEyePosition().subtract(0, 0.25, 0);
        Vec3 to = target.getBoundingBox().getCenter();
        float t = Math.min(1f, age / (float) LASER_CHARGE_TICKS);
        // the beam warms from teal to white-hot as it charges, like the guardian's own
        Vector3f colour = new Vector3f(0.45f + 0.55f * t, 0.85f + 0.15f * t, 0.8f + 0.2f * t);
        SkillFx.line(level, from, to, new DustParticleOptions(colour, 0.6f + t), 0.35 - 0.15 * t);
        if (age < LASER_CHARGE_TICKS) {
            return;
        }
        BEAMS.remove(player.getUUID());
        target.hurt(level.damageSources().indirectMagic(player, player), LASER_DAMAGE);
        Vec3 push = to.subtract(from).normalize();
        target.setDeltaMovement(target.getDeltaMovement().add(push.x * 0.8, 0.3, push.z * 0.8));
        target.hurtMarked = true;
        level.sendParticles(ParticleTypes.FLASH, to.x, to.y, to.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 30, 0.2, 0.2, 0.2, 0.25);
        level.playSound(null, target.blockPosition(), SoundEvents.ELDER_GUARDIAN_HURT, SoundSource.PLAYERS, 1f, 1.5f);
        level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.4f,
                1.8f);
    }

    public static void forget(Player player) {
        BEAMS.remove(player.getUUID());
        TrophyPassives.forget(player);
    }
}
