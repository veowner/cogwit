package dev.cogwit.crew;

import dev.cogwit.DamageCategories;
import dev.cogwit.brain.Brain;
import dev.cogwit.growth.GrowthContent;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Cogwit, or one of his brothers, off the shoulder and fighting next to his owner. Never saved with the world. */
public class CogwitCreature extends TamableAnimal implements GeoEntity {
    private static final EntityDataAccessor<Integer> KIND =
            SynchedEntityData.defineId(CogwitCreature.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RAGE =
            SynchedEntityData.defineId(CogwitCreature.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.cogwit.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.cogwit.walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.cogwit.attack");
    private static final RawAnimation JUMP_OFF = RawAnimation.begin().thenPlay("animation.cogwit.jump_off");
    private static final RawAnimation HAPPY = RawAnimation.begin().thenPlay("animation.cogwit.happy");
    /** Health a Copper Apple or a healing potion gives back. */
    public static final float APPLE_HEAL = 10f, POTION_HEAL = 16f;
    private static final long HELP_CALL_COOLDOWN = 600;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private long lastHelpCall = -HELP_CALL_COOLDOWN;
    /** The owner as an object, so he is found without a lookup by UUID among the level's players. */
    private Player owner;

    public CogwitCreature(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        for (EquipmentSlot slot : CrewGear.SLOTS) {
            setDropChance(slot, 0f);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TamableAnimal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(KIND, 0);
        builder.define(RAGE, false);
    }

    /** Overdrive: violet, flickering and furious. */
    public boolean rage() {
        return entityData.get(RAGE);
    }

    public void setRage(boolean rage) {
        entityData.set(RAGE, rage);
    }

    public Crew.Kind kind() {
        return Crew.Kind.values()[Math.floorMod(entityData.get(KIND), Crew.Kind.values().length)];
    }

    /** Applies the kind's stats and name; call once right after creating the creature. */
    public void setKind(Crew.Kind kind) {
        entityData.set(KIND, kind.ordinal());
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(kind.health);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(kind.attack);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(kind.speed);
        getAttribute(Attributes.ARMOR).setBaseValue(kind.armor);
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(kind.knockbackResistance);
        getAttribute(Attributes.SCALE).setBaseValue(kind.scale);
        setHealth(getMaxHealth());
        setCustomName(Component.translatable("cogwit.crew." + kind.key()));
    }

    /** Tames the creature for this player and remembers him directly. */
    public void joinCrewOf(Player player) {
        tame(player);
        owner = player;
    }

    @Override
    public LivingEntity getOwner() {
        return owner != null && !owner.isRemoved() ? owner : super.getOwner();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, true) {
            @Override
            protected int getAttackInterval() {
                return adjustedTickDelay(kind().attackInterval);
            }
        });
        goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.2, 6f, 2f));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers(CogwitCreature.class));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                target -> target instanceof Enemy && !(target instanceof Creeper)));
    }

    /** Like on the shoulder, Cogwit won't fight players, and nobody fights the exploding ones on purpose. */
    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return !(target instanceof Player) && !(target instanceof Creeper) && !(target instanceof CogwitCreature)
                && !(target instanceof TamableAnimal pet && pet.isTame());
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof Player) && !(target instanceof CogwitCreature) && super.canAttack(target);
    }

    /** The crew learns from the same lessons as Cogwit: his skills soften their hits too. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player) {
            return false; // neither the owner nor a co-op friend can hurt the crew by accident
        }
        if (getOwner() instanceof Player owner) {
            float reduction = CogwitTalismanItem.worn(owner)
                    .flatMap(stack -> DamageCategories.categorize(source)
                            .map(cat -> Brain.reductionFor(CogwitTalismanItem.brain(stack), cat)))
                    .orElse(0f);
            amount *= 1f - reduction;
        }
        return super.hurt(source, amount);
    }

    /** Armor on a crew member wears out like a player's. */
    @Override
    protected void hurtArmor(DamageSource source, float damage) {
        doHurtEquipment(source, damage, EquipmentSlot.HEAD, EquipmentSlot.CHEST);
    }

    /** Brothers' combo: Spark's quick strikes slow the target, Anvil hits slowed targets half again as hard. */
    public static final float ANVIL_COMBO = 1.5f;

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean slowed = target instanceof LivingEntity living && living.hasEffect(MobEffects.MOVEMENT_SLOWDOWN);
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            if (kind() == Crew.Kind.SPARK) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
            } else if (kind() == Crew.Kind.ANVIL && slowed) {
                living.invulnerableTime = 0;
                living.hurt(damageSources().mobAttack(this),
                        (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * (ANVIL_COMBO - 1f));
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.CRIT, living.getX(), living.getY() + living.getBbHeight() / 2,
                            living.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
                }
            }
        }
        if (hit) {
            ItemStack weapon = getMainHandItem();
            if (!weapon.isEmpty()) {
                weapon.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
            }
            triggerAnim("action", "attack");
        }
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || tickCount % 20 != 0) {
            return;
        }
        if (Crew.isLowHealth(getHealth(), getMaxHealth())) {
            addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false));
            long now = level().getGameTime();
            if (now - lastHelpCall >= HELP_CALL_COOLDOWN && getOwner() instanceof Player owner) {
                lastHelpCall = now;
                CrewService.callForHelp(owner, this);
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && getOwner() instanceof Player owner) {
            CrewService.creatureDown(owner, this);
        }
        super.die(source);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isOwnedBy(player)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level().isClientSide) {
                CrewGearMenu.open(player, this);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (getHealth() < getMaxHealth() && held.is(GrowthContent.COPPER_APPLE.get())) {
            heal(APPLE_HEAL);
            held.consume(1, player);
            cheer(SoundEvents.GENERIC_EAT);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        PotionContents potion = held.get(DataComponents.POTION_CONTENTS);
        if (getHealth() < getMaxHealth() && held.is(Items.POTION) && potion != null
                && (potion.is(Potions.HEALING) || potion.is(Potions.STRONG_HEALING))) {
            heal(POTION_HEAL);
            if (!player.getAbilities().instabuild) {
                player.setItemInHand(hand, new ItemStack(Items.GLASS_BOTTLE));
            }
            cheer(SoundEvents.GENERIC_DRINK);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (held.isEmpty()) {
            cheer(SoundEvents.AMETHYST_BLOCK_CHIME);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    private void cheer(net.minecraft.sounds.SoundEvent sound) {
        if (level() instanceof ServerLevel server) {
            playSound(sound, 0.8f, 1.4f);
            server.sendParticles(ParticleTypes.HEART, getX(), getY() + getBbHeight() + 0.3, getZ(), 3, 0.25, 0.1, 0.25,
                    0.0);
            triggerAnim("action", "happy");
        }
    }

    /** Plays the leap off the shoulder; the client shows it when the creature appears. */
    public void jumpedOff() {
        triggerAnim("action", "jump_off");
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kind", kind().ordinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setKind(Crew.Kind.values()[Math.floorMod(tag.getInt("Kind"), Crew.Kind.values().length)]);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 4,
                state -> state.setAndContinue(state.isMoving() ? WALK : IDLE)));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK)
                .triggerableAnim("jump_off", JUMP_OFF)
                .triggerableAnim("happy", HAPPY));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
