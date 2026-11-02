package dev.cogwit.horizon;

import dev.cogwit.CogwitMod;
import dev.cogwit.cells.CellBank;
import dev.cogwit.cells.Cells;
import dev.cogwit.network.HorizonSyncPayload;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerFlyableFallEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = CogwitMod.MODID)
public final class HorizonEvents {
    static final ResourceLocation FLIGHT = CogwitMod.id("horizon_flight");
    static final ResourceLocation HEAVY = CogwitMod.id("horizon_heavy");

    private HorizonEvents() {}

    /** Suiting up: each new piece assembles itself; completing the set fires the whole suit at once. */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(player.level() instanceof ServerLevel level)
                || !event.getSlot().isArmor() || !(event.getTo().getItem() instanceof HorizonArmorItem item)
                || ItemStack.isSameItem(event.getFrom(), event.getTo())) {
            return;
        }
        if (HorizonContent.wearsFullSet(player)) {
            for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack piece = player.getItemBySlot(slot);
                if (piece.getItem() instanceof HorizonArmorItem armor) {
                    armor.playEquip(player, piece, level);
                }
            }
            level.playSound(null, player.blockPosition(), HorizonSounds.SUIT_UP.get(), SoundSource.PLAYERS, 1f, 1f);
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5f, 1.5f);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.1, player.getZ(), 24,
                    0.4, 0.6, 0.4, 0.2);
        } else {
            item.playEquip(player, event.getTo(), level);
            level.playSound(null, player.blockPosition(), HorizonSounds.PLATE.get(), SoundSource.PLAYERS, 0.8f, 1f);
        }
    }

    /** Night vision the closed faceplate gives: renewed every second, so it never gets to the flickering end. */
    private static final int NIGHT_SIGHT_TICKS = 300;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        int pieces = HorizonContent.pieces(player);
        boolean sealed = Horizon.has(pieces, Horizon.Feature.SEALED);
        setModifier(player, NeoForgeMod.CREATIVE_FLIGHT, FLIGHT, 1,
                Horizon.has(pieces, Horizon.Feature.FLIGHT) && HorizonFlight.tick(player));
        setModifier(player, NeoForgeMod.SWIM_SPEED, HEAVY, -0.1 * Horizon.count(pieces), pieces != 0);

        long now = player.level().getGameTime();
        HorizonState before = HorizonContent.state(player);
        HorizonState s = Horizon.decay(before, now);
        tickVisor(player, Horizon.has(pieces, Horizon.Feature.VISOR), now);
        if (Horizon.has(pieces, Horizon.Feature.CORE)) {
            HorizonActions.tickReflect(player);
            if (now % 20 == 0) {
                HorizonActions.attractMobs(player);
            }
        }
        if (Horizon.has(pieces, Horizon.Feature.LANDING) && player.level() instanceof ServerLevel level) {
            HorizonJets.tick(level, player, pieces);
        }
        if (sealed) {
            player.clearFire();
            s = HorizonActions.tickVoid(player, s, now);
            if (player.isInWater() && !player.getAbilities().flying && !player.isCreative()
                    && !dev.cogwit.trophy.TrophyService.hasPassive(player, dev.cogwit.trophy.Trophy.ELDER_GUARDIAN)) {
                player.setDeltaMovement(player.getDeltaMovement().add(0, -0.02, 0));
            }
        }
        if (now % 40 == 0) {
            s = pieceTip(player, s, pieces);
        }
        if (!s.equals(before)) {
            HorizonContent.setState(player, s);
        }
        if (now % 5 == 0 && player instanceof ServerPlayer serverPlayer) {
            sync(serverPlayer, s, now, pieces);
        }
    }

    /**
     * At most one new tip every two seconds: a part's tip the first time it is worn, or, for a whole suit put on
     * at once, only the full-set tip (the part tips are then marked as read).
     */
    private static HorizonState pieceTip(Player player, HorizonState s, int pieces) {
        if (Horizon.has(pieces, Horizon.Feature.SEALED) && !s.hasSeenTip("full_set")) {
            for (Horizon.Piece piece : Horizon.Piece.values()) {
                s = s.withTip(pieceTipKey(piece));
            }
            return HorizonTips.once(player, s, "full_set");
        }
        for (Horizon.Piece piece : Horizon.Piece.values()) {
            if ((pieces & piece.bit) != 0 && !s.hasSeenTip(pieceTipKey(piece))) {
                return HorizonTips.once(player, s, pieceTipKey(piece));
            }
        }
        return s;
    }

    private static String pieceTipKey(Horizon.Piece piece) {
        return "piece_" + piece.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Under the closed faceplate: night vision, air under water, and no blindness or darkness. */
    private static void tickVisor(Player player, boolean helmet, long now) {
        ItemStack face = player.getItemBySlot(EquipmentSlot.HEAD);
        boolean closed = helmet && !HorizonVisor.shouldBeOpen(player, face);
        MobEffectInstance sight = player.getEffect(MobEffects.NIGHT_VISION);
        boolean ours = sight != null && sight.isAmbient() && !sight.isVisible()
                && sight.getDuration() <= NIGHT_SIGHT_TICKS;
        if (!closed) {
            if (ours) {
                player.removeEffect(MobEffects.NIGHT_VISION);
            }
            return;
        }
        if (now % 20 == 0 && (sight == null || ours)) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_SIGHT_TICKS, 0, true, false, false));
        }
        if (player.isEyeInFluid(FluidTags.WATER)) {
            player.setAirSupply(player.getMaxAirSupply());
        }
        if (player.hasEffect(MobEffects.BLINDNESS)) {
            player.removeEffect(MobEffects.BLINDNESS);
        }
        if (player.hasEffect(MobEffects.DARKNESS)) {
            player.removeEffect(MobEffects.DARKNESS);
        }
    }

    /** The boots take the fall; from a height the landing becomes a shockwave. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide
                && HorizonContent.has(player, Horizon.Feature.LANDING)) {
            event.setDamageMultiplier(0);
            land(player, event.getDistance());
        }
    }

    /** A player who may fly (legs and boots) lands through this event instead of the one above. */
    @SubscribeEvent
    public static void onFlyableFall(PlayerFlyableFallEvent event) {
        if (!event.getEntity().level().isClientSide && HorizonContent.has(event.getEntity(), Horizon.Feature.LANDING)) {
            land(event.getEntity(), event.getDistance());
        }
    }

    private static void land(Player player, float distance) {
        float strength = Horizon.landingStrength(distance);
        if (strength > 0 && !player.isInWater() && !player.isSpectator()) {
            HorizonJets.land(player, strength);
        }
    }

    /**
     * Each worn part takes its share of the damage (by armor points), the core turns pain into mass and mass into
     * harder hits, the boots ignore falls and only the sealed suit ignores fire.
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        DamageSource source = event.getSource();
        long now = victim.level().getGameTime();

        // Only real melee swings count; the armor's own abilities must not feed mass back into themselves.
        if (source.is(DamageTypes.PLAYER_ATTACK) && source.getEntity() instanceof Player attacker && attacker != victim
                && source.getDirectEntity() == attacker && HorizonContent.has(attacker, Horizon.Feature.CORE)) {
            MeleeResult hit = Horizon.onMeleeHit(HorizonContent.state(attacker), event.getAmount(), now);
            HorizonContent.setState(attacker, hit.state());
            event.setAmount(event.getAmount() + hit.bonus());
        }

        if (!(victim instanceof Player player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        int pieces = HorizonContent.pieces(player);
        if (pieces == 0) {
            return;
        }
        if (HorizonDash.isDashing(player)) {
            // mid-dash the hit passes through the afterimage
            event.setCanceled(true);
            if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, player.getX(),
                        player.getY() + 1, player.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
            }
            return;
        }
        if (source.is(DamageTypeTags.IS_FALL) && Horizon.has(pieces, Horizon.Feature.LANDING)
                || source.is(DamageTypeTags.IS_FIRE) && Horizon.has(pieces, Horizon.Feature.SEALED)) {
            event.setCanceled(true);
            return;
        }
        HorizonState s = HorizonContent.state(player);
        if (Horizon.has(pieces, Horizon.Feature.CORE)) {
            MassGain gain = Horizon.onDamageTaken(s, event.getAmount(), now);
            if (gain.overflow() > 0) {
                CellBank.charge(player, Cells.tokensFromMass(gain.overflow()));
            }
            s = gain.state();
            if (s.mass() >= Horizon.MASS_MAX) {
                s = HorizonTips.once(player, s, "mass_full");
            }
            HorizonContent.setState(player, s);
        }
        event.setAmount(event.getAmount() * Horizon.damageMultiplier(pieces, s.mass(),
                dev.cogwit.BalanceConfig.armorReduction(), dev.cogwit.BalanceConfig.armorMassReduction()));
    }

    static void sync(ServerPlayer player, HorizonState s, long now, int pieces) {
        PacketDistributor.sendToPlayer(player, new HorizonSyncPayload(s.mass(),
                (int) Math.max(0, s.blinkReadyAt() - now), (int) Math.max(0, s.wellReadyAt() - now),
                (int) Math.max(0, s.voidRescueReadyAt() - now), pieces));
    }

    private static void setModifier(Player player, Holder<Attribute> attribute, ResourceLocation id, double amount,
                                    boolean on) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (on && current != null && current.amount() != amount) {
            instance.removeModifier(id);
            current = null;
        }
        boolean has = current != null;
        if (on && !has) {
            instance.addTransientModifier(new AttributeModifier(id, amount,
                    attribute == NeoForgeMod.SWIM_SPEED ? AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                            : AttributeModifier.Operation.ADD_VALUE));
        } else if (!on && has) {
            instance.removeModifier(id);
        }
    }
}
