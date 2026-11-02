package dev.cogwit.trophy;

import dev.cogwit.CogwitMod;
import dev.cogwit.Speech;
import dev.cogwit.horizon.fx.SkillFx;
import dev.cogwit.network.ScreenFxPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FrostedIceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Passives of the newer trophies that need more than a flag in {@link TrophyService}: the Wroughtnaut's reflected
 * blows, the sunbird's daylight regeneration, the Sculptor's platform, the Valkyrie's double jump, the Dead King's
 * second life and the Flamebearer's spell power, and those of the Twilight Forest and Bosses' Rise trophies: the
 * Naga's speed, the Lich's shield, the Minoshroom's appetite, the Hydra's regrowth, the Knight Phantom's plate, the
 * Ur-Ghast's float, the Alpha Yeti's hardiness, the Snow Queen's chill, Ashlord's burning blows, Nerakyss' reach,
 * Sirok's sand, Helvar's soul harvest and Skor's ice road. (Frostmaw, Archevoker and Sirok's poison immunity only tweak
 * the existing immunity checks.)
 */
public final class TrophyPassives {
    /** Game ticks between two Dead King revivals: ten minutes. */
    public static final int REVIVE_COOLDOWN_TICKS = 12_000;
    /** Where the revival cooldown lives in {@link TrophyContent.Data#readyAt()}; no trophy has this key. */
    public static final String REVIVE_KEY = "dead_king_revive";
    public static final double DOUBLE_JUMP_SPEED = 0.62, DOUBLE_JUMP_MAX_SPEED = 0.9;
    public static final float SUN_HEAL = 0.5f;
    public static final int SUN_HEAL_INTERVAL = 40, CATCH_COOLDOWN_TICKS = 100;
    /** Added to Iron's Spells 'n Spellbooks' spell power attributes, as a share of their base value. */
    public static final double SPELL_POWER_BONUS = 0.2, FIRE_SPELL_POWER_BONUS = 0.3;
    private static final ResourceLocation SPELL_POWER = ResourceLocation.fromNamespaceAndPath("irons_spellbooks",
            "spell_power");
    private static final ResourceLocation FIRE_SPELL_POWER = ResourceLocation.fromNamespaceAndPath("irons_spellbooks",
            "fire_spell_power");
    private static final ResourceLocation SPELL_MODIFIER = CogwitMod.id("trophy_spell_power");
    private static final ResourceLocation FIRE_SPELL_MODIFIER = CogwitMod.id("trophy_fire_spell_power");

    // Twilight Forest and Bosses' Rise
    /** Where the Lich's shield cooldown lives in {@link TrophyContent.Data#readyAt()}; no trophy has this key. */
    public static final String LICH_KEY = "lich_shield";
    public static final int LICH_SHIELD_COOLDOWN_TICKS = 600, STRENGTH_TICKS = 400, HYDRA_HEAL_INTERVAL = 20,
            INFERNAL_BURN_SECONDS = 3, SAND_SPEED_TICKS = 30;
    /** The Naga's share of extra walking speed. */
    public static final double NAGA_SPEED_BONUS = 0.12;
    public static final double KNIGHT_ARMOR_BONUS = 3, KNIGHT_TOUGHNESS_BONUS = 1, YETI_HEALTH_BONUS = 4,
            KRAKEN_REACH_BONUS = 1.0, CHILL_RADIUS = 4;
    public static final float HYDRA_HEAL = 0.5f, HYDRA_BELOW = 0.5f, REAP_HEAL = 2f, GHAST_FALL_SHARE = 0.5f;
    public static final int ICE_ROAD_RADIUS = 2;
    private static final ResourceLocation NAGA_SPEED = CogwitMod.id("trophy_naga_speed");
    private static final ResourceLocation KNIGHT_ARMOR = CogwitMod.id("trophy_knight_armor");
    private static final ResourceLocation KNIGHT_TOUGHNESS = CogwitMod.id("trophy_knight_toughness");
    private static final ResourceLocation YETI_HEALTH = CogwitMod.id("trophy_alpha_yeti_health");
    private static final ResourceLocation KRAKEN_REACH = CogwitMod.id("trophy_kraken_reach");
    private static final DustParticleOptions LICH_WARD = new DustParticleOptions(new Vector3f(0.55f, 0.30f, 0.88f), 1.0f);

    /** Players who already used their double jump in the air they are in now. */
    private static final Set<UUID> DOUBLE_JUMPED = ConcurrentHashMap.newKeySet();
    /** Game time of each player's last Sculptor catch, so a long fall is not caught over and over. */
    private static final Map<UUID, Long> CAUGHT = new ConcurrentHashMap<>();
    /** Set while a reflected hit lands, so reflecting never reflects. */
    private static final ThreadLocal<Boolean> REFLECTING = ThreadLocal.withInitial(() -> false);

    private TrophyPassives() {}

    /** The slow passives, kept up twice a second with the rest in {@link TrophyService#tickPassives}. */
    static void tick(Player player, Set<Trophy> passives) {
        // Frostmaw: no creeping frost and no slowness
        if (passives.contains(Trophy.FROSTMAW)) {
            if (player.getTicksFrozen() > 0) {
                player.setTicksFrozen(0);
            }
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        // Umvuthi: the sun mends you
        if (passives.contains(Trophy.UMVUTHI) && player.tickCount % SUN_HEAL_INTERVAL == 0
                && player.getHealth() < player.getMaxHealth() && underSun(player)) {
            sunHeal(player);
        }
        // Echo of Tyros: stronger spells while Iron's Spells 'n Spellbooks is installed
        spellPower(player, SPELL_POWER, SPELL_MODIFIER, SPELL_POWER_BONUS, passives.contains(Trophy.FIRE_BOSS));
        spellPower(player, FIRE_SPELL_POWER, FIRE_SPELL_MODIFIER, FIRE_SPELL_POWER_BONUS,
                passives.contains(Trophy.FIRE_BOSS));
        tickRealms(player, passives);
    }

    /** The slow passives of the Twilight Forest and Bosses' Rise trophies. */
    private static void tickRealms(Player player, Set<Trophy> passives) {
        modifier(player, Attributes.MOVEMENT_SPEED, NAGA_SPEED, NAGA_SPEED_BONUS,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE, passives.contains(Trophy.NAGA));
        modifier(player, Attributes.ARMOR, KNIGHT_ARMOR, KNIGHT_ARMOR_BONUS, AttributeModifier.Operation.ADD_VALUE,
                passives.contains(Trophy.KNIGHT_PHANTOM));
        modifier(player, Attributes.ARMOR_TOUGHNESS, KNIGHT_TOUGHNESS, KNIGHT_TOUGHNESS_BONUS,
                AttributeModifier.Operation.ADD_VALUE, passives.contains(Trophy.KNIGHT_PHANTOM));
        modifier(player, Attributes.MAX_HEALTH, YETI_HEALTH, YETI_HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE,
                passives.contains(Trophy.ALPHA_YETI));
        modifier(player, Attributes.ENTITY_INTERACTION_RANGE, KRAKEN_REACH, KRAKEN_REACH_BONUS,
                AttributeModifier.Operation.ADD_VALUE, passives.contains(Trophy.KRAKEN));
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // Hydra: wounds close over, once you are down to your last half
        if (passives.contains(Trophy.HYDRA) && player.tickCount % HYDRA_HEAL_INTERVAL == 0
                && player.getHealth() < player.getMaxHealth() * HYDRA_BELOW && player.isAlive()) {
            player.heal(HYDRA_HEAL);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.35, 0.5, 0.35, 0);
        }
        // Ur-Ghast: sneak in mid-air to drift down like a ghast
        if (passives.contains(Trophy.UR_GHAST) && !player.onGround() && player.isShiftKeyDown()
                && player.getDeltaMovement().y < -0.2 && !player.getAbilities().flying && !player.isFallFlying()
                && !player.isInWater()) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, true, false));
        }
        // Sirok: quick on the sand
        if (passives.contains(Trophy.SANDWORM) && player.onGround()
                && level.getBlockState(player.blockPosition().below()).is(BlockTags.SAND)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SAND_SPEED_TICKS, 0, true, false));
        }
        // Snow Queen: the cold round you slows hostile mobs
        if (passives.contains(Trophy.SNOW_QUEEN)) {
            chill(level, player);
        }
        // Lich: a ward of souls circles you while the shield is up
        if (passives.contains(Trophy.LICH) && lichShieldReady(player)) {
            for (int i = 0; i < 3; i++) {
                double a = player.tickCount * 0.3 + i * Math.PI * 2 / 3;
                level.sendParticles(i == 0 ? ParticleTypes.SOUL_FIRE_FLAME : LICH_WARD, player.getX() + Math.cos(a) * 0.9,
                        player.getY() + 1.0 + Math.sin(a * 2) * 0.3, player.getZ() + Math.sin(a) * 0.9, 1, 0.02, 0.02, 0.02, 0);
            }
        }
    }

    /** Adds or removes a transient modifier on one of the player's attributes. */
    private static void modifier(Player player, Holder<Attribute> attribute, ResourceLocation id, double amount,
            AttributeModifier.Operation operation, boolean active) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (active && !instance.hasModifier(id)) {
            instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        } else if (!active && instance.hasModifier(id)) {
            instance.removeModifier(id);
        }
    }

    // ---------------------------------------------------------------- Lich

    /** Whether the Lich's shield is up for this player. */
    public static boolean lichShieldReady(Player player) {
        return player.level().getGameTime() >= player.getData(TrophyContent.DATA).readyAt().getOrDefault(LICH_KEY, 0L);
    }

    /**
     * A blow from a creature, projectile or explosion meets the Lich's shield: it is absorbed whole, once every thirty
     * seconds. Returns true when the blow was absorbed. Poison, hunger and the like are not blows and never use it up.
     */
    public static boolean lichShield(Player player, DamageSource source) {
        if (source.getEntity() == null && source.getDirectEntity() == null && !source.is(DamageTypeTags.IS_EXPLOSION)
                || !lichShieldReady(player)) {
            return false;
        }
        long now = player.level().getGameTime();
        TrophyContent.Data data = player.getData(TrophyContent.DATA);
        Map<String, Long> readyAt = new HashMap<>(data.readyAt());
        readyAt.put(LICH_KEY, now + LICH_SHIELD_COOLDOWN_TICKS);
        player.setData(TrophyContent.DATA, new TrophyContent.Data(data.owned(), readyAt));
        if (player.level() instanceof ServerLevel level) {
            Vec3 c = player.position().add(0, 1, 0);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y, c.z, 24, 0.5, 0.7, 0.5, 0.12);
            level.sendParticles(ParticleTypes.SOUL, c.x, c.y, c.z, 12, 0.5, 0.7, 0.5, 0.08);
            level.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y, c.z, 6, 0.4, 0.6, 0.4, 0.04);
            SkillFx.ring(level, c, new Vec3(0, 1, 0), 1.0, 16, LICH_WARD);
            level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 0.9f, 0.7f);
            level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2f, 0.8f);
            level.playSound(null, player.blockPosition(), SoundEvents.SKELETON_AMBIENT, SoundSource.PLAYERS, 0.8f, 0.5f);
        }
        return true;
    }

    // ---------------------------------------------------------------- Minoshroom

    /** A meal gives the Minoshroom's owner strength for twenty seconds. */
    public static void feast(Player player, ItemStack eaten) {
        if (eaten.get(DataComponents.FOOD) == null || !TrophyService.hasPassive(player, Trophy.MINOSHROOM)) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, STRENGTH_TICKS, 0, false, true));
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, player.getX(), player.getY() + 1.4, player.getZ(), 12, 0.4, 0.4, 0.4, 0);
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + 2.0, player.getZ(), 2, 0.2, 0.1, 0.2, 0);
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.8f, 0.7f);
        }
    }

    // ---------------------------------------------------------------- Ur-Ghast

    /** The Ur-Ghast owner lands softly: half the fall damage. */
    public static float softLanding(Player player, float multiplier) {
        return TrophyService.hasPassive(player, Trophy.UR_GHAST) ? multiplier * GHAST_FALL_SHARE : multiplier;
    }

    // ---------------------------------------------------------------- Snow Queen

    private static void chill(ServerLevel level, Player player) {
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(CHILL_RADIUS),
                e -> e instanceof Enemy && SkillFx.isVictim(e, player) && e.distanceTo(player) <= CHILL_RADIUS)) {
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, true, false));
            level.sendParticles(ParticleTypes.SNOWFLAKE, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 2, 0.25, 0.3,
                    0.25, 0.01);
        }
        SkillFx.ring(level, player.position().add(0, 0.2, 0), new Vec3(0, 1, 0), CHILL_RADIUS, 12, ParticleTypes.SNOWFLAKE);
    }

    // ---------------------------------------------------------------- Ashlord

    /** Blows of an Ashlord owner set the creature they hit alight; players, pets and bystanders are not hurt by it. */
    public static void infernalStrike(Player attacker, LivingEntity target) {
        if (!SkillFx.isVictim(target, attacker) || !TrophyService.hasPassive(attacker, Trophy.INFERNAL_DRAGON)) {
            return;
        }
        target.igniteForSeconds(INFERNAL_BURN_SECONDS);
        if (attacker.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 6, 0.25,
                    0.35, 0.25, 0.04);
        }
    }

    // ---------------------------------------------------------------- Helvar

    /** A hostile mob killed by a Helvar owner gives back a heart, a wisp of its soul flying to the killer. */
    public static void harvest(LivingEntity victim, DamageSource source) {
        if (!(source.getEntity() instanceof Player killer) || killer.level().isClientSide || !(victim instanceof Enemy)
                || victim instanceof Player || killer == victim || !killer.isAlive()
                || !TrophyService.hasPassive(killer, Trophy.UNDERWORLD_KNIGHT)) {
            return;
        }
        killer.heal(REAP_HEAL);
        if (killer.level() instanceof ServerLevel level) {
            Vec3 from = victim.getBoundingBox().getCenter();
            Vec3 to = killer.position().add(0, 1.2, 0);
            for (int i = 0; i <= 8; i++) {
                Vec3 p = from.lerp(to, i / 8.0).add(0, Math.sin(i / 8.0 * Math.PI) * 0.8, 0);
                level.sendParticles(i % 2 == 0 ? ParticleTypes.SOUL : ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
            }
            level.sendParticles(ParticleTypes.SOUL, killer.getX(), killer.getY() + 1.2, killer.getZ(), 5, 0.3, 0.5, 0.3, 0.03);
            level.playSound(null, killer.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.7f, 1.5f);
        }
    }

    // ---------------------------------------------------------------- Skor

    /**
     * Water under the feet of a Skor owner freezes into frosted ice, the way Frost Walker boots do: it melts back into
     * water by itself a few seconds after they pass. Only still water with air above it is touched. Returns the number of
     * blocks frozen.
     */
    public static int iceRoad(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.onGround() || player.isShiftKeyDown()
                || player.isSpectator()) {
            return 0;
        }
        BlockState ice = Blocks.FROSTED_ICE.defaultBlockState();
        BlockPos feet = player.blockPosition();
        int frozen = 0;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-ICE_ROAD_RADIUS, -1, -ICE_ROAD_RADIUS),
                feet.offset(ICE_ROAD_RADIUS, -1, ICE_ROAD_RADIUS))) {
            if (!pos.closerToCenterThan(player.position(), ICE_ROAD_RADIUS) || !level.getBlockState(pos.above()).isAir()
                    || level.getBlockState(pos) != FrostedIceBlock.meltsInto()
                    || !ice.canSurvive(level, pos)
                    || !level.isUnobstructed(ice, pos, net.minecraft.world.phys.shapes.CollisionContext.empty())) {
                continue;
            }
            BlockPos fixed = pos.immutable();
            level.setBlockAndUpdate(fixed, ice);
            level.scheduleTick(fixed, Blocks.FROSTED_ICE, Mth.nextInt(level.getRandom(), 60, 120));
            level.sendParticles(ParticleTypes.SNOWFLAKE, fixed.getX() + 0.5, fixed.getY() + 1.1, fixed.getZ() + 0.5, 2, 0.3, 0.05,
                    0.3, 0.01);
            frozen++;
        }
        return frozen;
    }

    // ---------------------------------------------------------------- Ferrous Wroughtnaut

    /** A mob's melee blow to a trophy owner is returned in part: call after the damage was dealt, with what it cost. */
    public static void reflect(Player victim, DamageSource source, float taken) {
        if (taken <= 0 || REFLECTING.get() || !(victim.level() instanceof ServerLevel level)
                || source.getDirectEntity() != source.getEntity()
                || !(source.getDirectEntity() instanceof LivingEntity attacker) || attacker instanceof Player
                || attacker == victim || !attacker.isAlive()
                || !(source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO))
                || !TrophyService.hasPassive(victim, Trophy.FERROUS_WROUGHTNAUT)) {
            return;
        }
        REFLECTING.set(true);
        try {
            attacker.hurt(level.damageSources().thorns(victim), PowerMath.reflected(taken));
        } finally {
            REFLECTING.set(false);
        }
        Vec3 at = attacker.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.25, 0.3, 0.25, 0.3);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 6, 0.2, 0.3, 0.2, 0.2);
        level.playSound(null, attacker.blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    // ---------------------------------------------------------------- Umvuthi

    /** Open sky overhead and the sun up (also in the Aether, where the clock never moves). */
    public static boolean underSun(Player player) {
        BlockPos head = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        return player.level().canSeeSky(head)
                && player.level().getBrightness(LightLayer.SKY, head) - player.level().getSkyDarken() >= 12;
    }

    public static void sunHeal(Player player) {
        player.heal(SUN_HEAL);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY() + 1.1, player.getZ(), 4, 0.35, 0.5,
                    0.35, 0.0);
        }
    }

    // ---------------------------------------------------------------- Sculptor

    /**
     * A long fall with the ground not far below: a stone platform forms under the feet and the fall is eased.
     * Only air is ever replaced. Returns true when a platform was raised.
     */
    public static boolean catchFall(Player player) {
        if (!(player.level() instanceof ServerLevel level) || player.onGround() || player.isInWater()
                || player.getAbilities().flying || player.isFallFlying() || player.isPassenger()
                || player.fallDistance <= PowerMath.CATCH_FALL
                || level.getGameTime() - CAUGHT.getOrDefault(player.getUUID(), -1000L) < CATCH_COOLDOWN_TICKS
                || !TrophyService.hasPassive(player, Trophy.SCULPTOR)) {
            return false;
        }
        BlockPos under = BlockPos.containing(player.getX(), player.getY(), player.getZ()).below();
        if (!level.getBlockState(under).isAir()) {
            return false;
        }
        int ground = -1;
        for (int i = 1; i <= PowerMath.CATCH_GROUND + 1 && ground < 0; i++) {
            BlockState state = level.getBlockState(under.below(i));
            if (!state.isAir() && !state.getFluidState().is(FluidTags.WATER)) {
                ground = i;
            }
        }
        if (!PowerMath.catchesFall(player.fallDistance, ground)) {
            return false;
        }
        BlockState platform = TrophyContent.STONE_PLATFORM.get().defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = under.offset(dx, 0, dz);
                if (level.getBlockState(pos).isAir()) {
                    level.setBlockAndUpdate(pos, platform);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, platform), pos.getX() + 0.5,
                            pos.getY() + 0.9, pos.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0.05);
                }
            }
        }
        CAUGHT.put(player.getUUID(), level.getGameTime());
        // the fall ends here, and what is left of the speed is eased away
        player.resetFallDistance();
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x * 0.4, -0.35, v.z * 0.4);
        player.hurtMarked = true;
        SkillFx.ring(level, Vec3.atCenterOf(under).add(0, 0.55, 0), new Vec3(0, 1, 0), 1.6, 16, ParticleTypes.CLOUD);
        level.playSound(null, player.blockPosition(), SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.2f, 0.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.PLAYERS, 1f, 0.8f);
        return true;
    }

    // ---------------------------------------------------------------- Valkyrie Queen

    /**
     * The double jump asked for by the client: allowed once per airtime in the full set with the Valkyrie's passive.
     * {@code vx}/{@code vz} are the horizontal speed the client had, which the jump keeps (capped, so a modified client
     * cannot launch itself). Returns true when the jump happened.
     */
    public static boolean doubleJump(Player player, double vx, double vz) {
        if (!(player.level() instanceof ServerLevel level) || player.onGround() || player.isInWater()
                || player.isInLava() || player.getAbilities().flying || player.isFallFlying() || player.isPassenger()
                || player.onClimbable() || !Double.isFinite(vx) || !Double.isFinite(vz)
                || !TrophyService.hasPassive(player, Trophy.VALKYRIE_QUEEN) || !DOUBLE_JUMPED.add(player.getUUID())) {
            return false;
        }
        double h = Math.hypot(vx, vz);
        double keep = h > DOUBLE_JUMP_MAX_SPEED ? DOUBLE_JUMP_MAX_SPEED / h : 1;
        player.setDeltaMovement(vx * keep, DOUBLE_JUMP_SPEED, vz * keep);
        player.hurtMarked = true;
        player.resetFallDistance();
        // a pair of wings of light unfolds behind the player and a ring of air bursts from under the feet
        Vec3 back = Vec3.directionFromRotation(0, player.getYRot() + 180).multiply(1, 0, 1);
        Vec3 side = new Vec3(-back.z, 0, back.x);
        Vec3 base = player.position().add(0, 1.2, 0).add(back.scale(0.25));
        for (int sign = -1; sign <= 1; sign += 2) {
            for (int i = 0; i < 7; i++) {
                double t = i / 6.0;
                Vec3 p = base.add(side.scale(sign * (0.25 + t * 1.1))).add(back.scale(t * 0.5))
                        .add(0, Math.sin(t * Math.PI) * 0.55 - t * 0.2, 0);
                level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, side.x * sign, -0.15, side.z * sign, 0.06);
            }
        }
        SkillFx.ring(level, player.position().add(0, 0.1, 0), new Vec3(0, 1, 0), 0.9, 14, ParticleTypes.CLOUD);
        level.playSound(null, player.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.8f,
                1.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8f, 1.8f);
        return true;
    }

    /** Every tick for a player in the world: touching down (or water, or a ladder) gives the double jump back. */
    static void landed(Player player) {
        if (!DOUBLE_JUMPED.isEmpty() && (player.onGround() || player.isInWater() || player.isInLava()
                || player.onClimbable() || player.getAbilities().flying)) {
            DOUBLE_JUMPED.remove(player.getUUID());
        }
    }

    // ---------------------------------------------------------------- Dead King

    /**
     * A lethal blow to a Dead King owner whose second life is ready: they stay on a quarter of their health, harmful
     * effects are lifted, and the dead push back whatever stands close. Returns true if the death should be cancelled.
     */
    public static boolean revive(Player player, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !TrophyService.hasPassive(player, Trophy.DEAD_KING)) {
            return false;
        }
        long now = player.level().getGameTime();
        TrophyContent.Data data = player.getData(TrophyContent.DATA);
        if (now < data.readyAt().getOrDefault(REVIVE_KEY, 0L)) {
            return false;
        }
        Map<String, Long> readyAt = new HashMap<>(data.readyAt());
        readyAt.put(REVIVE_KEY, now + REVIVE_COOLDOWN_TICKS);
        player.setData(TrophyContent.DATA, new TrophyContent.Data(data.owned(), readyAt));

        player.setHealth(PowerMath.reviveHealth(player.getMaxHealth()));
        player.clearFire();
        for (MobEffectInstance effect : List.copyOf(player.getActiveEffects())) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                player.removeEffect(effect.getEffect());
            }
        }
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 2));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400, 0));
        Speech.say(player, Component.translatable("cogwit.trophy.revived"));

        if (player.level() instanceof ServerLevel level) {
            Vec3 c = player.position();
            level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1f, 0.6f);
            level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.35f, 1.6f);
            level.playSound(null, player.blockPosition(), SoundEvents.SKELETON_CONVERTED_TO_STRAY, SoundSource.PLAYERS,
                    1f, 0.5f);
            // a column of souls spirals up round the body, then a ring of them breaks outwards
            for (int t = 0; t < 14; t++) {
                int step = t;
                TrophyScheduler.later(level, t, () -> {
                    for (int arm = 0; arm < 3; arm++) {
                        double a = step * 0.7 + arm * Math.PI * 2 / 3, r = 1.1 - step * 0.03;
                        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x + Math.cos(a) * r, c.y + step * 0.14,
                                c.z + Math.sin(a) * r, 1, 0.02, 0.02, 0.02, 0.01);
                        level.sendParticles(ParticleTypes.SOUL, c.x + Math.cos(a) * r, c.y + step * 0.14 + 0.2,
                                c.z + Math.sin(a) * r, 1, 0.02, 0.02, 0.02, 0.02);
                    }
                });
            }
            TrophyScheduler.later(level, 8, () -> {
                for (int r = 1; r <= 4; r++) {
                    int radius = r;
                    TrophyScheduler.later(level, r, () -> SkillFx.ring(level, c.add(0, 0.2, 0), new Vec3(0, 1, 0),
                            radius, 8 + radius * 5, ParticleTypes.SOUL));
                }
            });
            level.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y + 1, c.z, 16, 0.4, 0.7, 0.4, 0.05);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y + 1, c.z, 20, 0.4, 0.7, 0.4, 0.4);
            for (LivingEntity near : SkillFx.victims(player, player, c.add(0, 1, 0), 4)) {
                Vec3 away = near.position().subtract(c).multiply(1, 0, 1);
                away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
                near.setDeltaMovement(near.getDeltaMovement().add(away.x * 1.1, 0.35, away.z * 1.1));
                near.hurtMarked = true;
            }
            if (player instanceof ServerPlayer sp) {
                SkillFx.screen(sp, ScreenFxPayload.FLASH, 0.6f, 20);
                SkillFx.screen(sp, ScreenFxPayload.SHAKE, 0.4f, 12);
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- Echo of Tyros

    /** Adds or removes a bonus on one of Iron's attributes; does nothing when that mod (or the attribute) is absent. */
    private static void spellPower(Player player, ResourceLocation attribute, ResourceLocation modifier, double bonus,
            boolean active) {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(attribute).orElse(null);
        AttributeInstance instance = holder == null ? null : player.getAttribute(holder);
        if (instance == null) {
            return;
        }
        if (active && !instance.hasModifier(modifier)) {
            instance.addTransientModifier(new AttributeModifier(modifier, bonus,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        } else if (!active && instance.hasModifier(modifier)) {
            instance.removeModifier(modifier);
        }
    }

    /** True while a spell power bonus from the Flamebearer is on this player; false without Iron's Spellbooks. */
    public static boolean hasSpellPowerBonus(Player player) {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(SPELL_POWER).orElse(null);
        AttributeInstance instance = holder == null ? null : player.getAttribute(holder);
        return instance != null && instance.hasModifier(SPELL_MODIFIER);
    }

    /** Whether the optional spellbook mod's attributes exist in this game. */
    public static boolean spellbooksPresent() {
        return BuiltInRegistries.ATTRIBUTE.containsKey(SPELL_POWER)
                && BuiltInRegistries.ATTRIBUTE.containsKey(FIRE_SPELL_POWER);
    }

    public static void forget(Player player) {
        DOUBLE_JUMPED.remove(player.getUUID());
        CAUGHT.remove(player.getUUID());
    }
}
