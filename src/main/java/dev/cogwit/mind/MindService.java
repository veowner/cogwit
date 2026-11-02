package dev.cogwit.mind;

import dev.cogwit.CogwitComponents;
import dev.cogwit.Speech;
import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.cells.CellBank;
import dev.cogwit.growth.GrowthContent;
import dev.cogwit.item.CogwitTalismanItem;
import dev.cogwit.network.MarkShowPayload;
import dev.cogwit.network.ScanResultPayload;
import dev.cogwit.ultra.UltraService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server side of Cogwit's brain: hints, moods, scanning, marks and death comments. */
public final class MindService {
    private static final Map<UUID, EnumMap<Mind.Hint, Long>> LAST_HINT = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> WAS_LOW = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();

    private MindService() {}

    public static MindState state(ItemStack talisman) {
        return talisman.getOrDefault(CogwitComponents.MIND.get(), MindState.EMPTY);
    }

    public static void setState(ItemStack talisman, MindState state) {
        talisman.set(CogwitComponents.MIND.get(), state);
    }

    /** Cogwit is pleased for a moment: fed, grown, or you won a fight. */
    public static void cheer(Player player, long ticks) {
        CogwitTalismanItem.worn(player).ifPresent(t -> {
            setState(t, Mind.cheer(state(t), player.level().getGameTime(), ticks));
            dev.cogwit.speech.CogwitVoice.beep(player, dev.cogwit.speech.CogwitVoice.Beep.HAPPY);
        });
    }

    /** Twice a second: watch your back and your health. */
    public static void watch(Player player, ItemStack talisman, long now) {
        DeathCompass.tick(player);
        BrainState brain = CogwitTalismanItem.brain(talisman);
        if (Brain.isAsleep(brain, now)) {
            return;
        }
        boolean scared = false;
        Vec3 look = player.getLookAngle();
        for (Creeper creeper : player.level().getEntitiesOfClass(Creeper.class,
                player.getBoundingBox().inflate(Mind.CREEPER_RANGE))) {
            double distance = creeper.distanceTo(player);
            boolean behind = Brain.isBehind(look.x, look.z, creeper.getX() - player.getX(),
                    creeper.getZ() - player.getZ());
            if (Mind.creeperThreat(distance, behind, creeper.getSwellDir() > 0)) {
                scared = true;
                hint(player, Mind.Hint.CREEPER_BEHIND, now, Component.translatable(
                        behind ? "cogwit.mind.creeper_behind" : "cogwit.mind.creeper_hiss",
                        Math.round(distance)));
                break;
            }
        }
        for (AbstractSkeleton skeleton : player.level().getEntitiesOfClass(AbstractSkeleton.class,
                player.getBoundingBox().inflate(24))) {
            if (skeleton.getTarget() == player && skeleton.isUsingItem()) {
                hint(player, Mind.Hint.SKELETON_AIMING, now, Component.translatable("cogwit.mind.skeleton",
                        skeleton.getDisplayName()));
                break;
            }
        }
        boolean low = player.getHealth() < player.getMaxHealth() * Mind.LOW_HEALTH;
        if (low) {
            scared = true;
            // Overdrive has its own, louder low-health call
            if (!WAS_LOW.getOrDefault(player.getUUID(), false) && !UltraService.isActive(talisman)) {
                hint(player, Mind.Hint.LOW_HP, now, Component.translatable("cogwit.mind.low_hp"));
            }
        }
        WAS_LOW.put(player.getUUID(), low);
        if (scared) {
            setState(talisman, Mind.scare(state(talisman), now));
        }
        if (!player.isCreative() && !player.isSpectator()) {
            forecast(player, now);
        }
    }

    /** Trouble a step away: lava behind the block you dig, a drop ahead, nightfall without a bed, air, a worn tool. */
    static void forecast(Player player, long now) {
        Level level = player.level();
        int close = dev.cogwit.agent.AgentService.threatCount(player, Mind.SURROUND_RANGE);
        if (Mind.surrounded(close, player.getHealth() / player.getMaxHealth())) {
            int heading = dev.cogwit.agent.AgentService.safestHeading(player, Mind.SURROUND_RANGE + 4);
            if (heading >= 0) {
                double[] h = dev.cogwit.agent.Escape.heading(heading);
                Vec3 look = player.getLookAngle();
                int side = dev.cogwit.agent.Escape.relative(look.x, look.z, h[0], h[1]);
                hint(player, Mind.Hint.SURROUNDED, now, Component.translatable("cogwit.think.surrounded", close,
                        Component.translatable("cogwit.dir." + side)));
            }
        }
        ItemStack hand = player.getMainHandItem();
        if (hand.getItem() instanceof DiggerItem
                && player.pick(player.blockInteractionRange(), 1f, false) instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK) {
            for (Direction side : Direction.values()) {
                if (level.getFluidState(hit.getBlockPos().relative(side)).is(FluidTags.LAVA)) {
                    hint(player, Mind.Hint.LAVA, now, Component.translatable("cogwit.think.lava"));
                    break;
                }
            }
        }
        Vec3 last = LAST_POS.put(player.getUUID(), player.position());
        if (last != null && player.onGround()) {
            Vec3 step = player.position().subtract(last).multiply(1, 0, 1);
            if (step.lengthSqr() > 0.25) {
                Vec3 ahead = player.position().add(step.normalize().scale(Mind.CLIFF_AHEAD));
                BlockPos column = BlockPos.containing(ahead.x, player.getY() - 0.5, ahead.z);
                int depth = 0;
                while (depth < 32 && level.getBlockState(column.below(depth)).getCollisionShape(level,
                        column.below(depth)).isEmpty() && level.getFluidState(column.below(depth)).isEmpty()) {
                    depth++;
                }
                boolean water = level.getFluidState(column.below(depth)).is(FluidTags.WATER);
                boolean fallProof = player.hasEffect(MobEffects.SLOW_FALLING)
                        || dev.cogwit.horizon.HorizonContent.has(player, dev.cogwit.horizon.Horizon.Feature.LANDING);
                if (Mind.cliffAhead(depth, water, fallProof)) {
                    hint(player, Mind.Hint.CLIFF, now, Component.translatable("cogwit.think.cliff", depth));
                }
            }
        }
        if (level.dimension() == Level.OVERWORLD && Mind.duskWarning(level.getDayTime())
                && player.getInventory().items.stream().noneMatch(st -> st.is(ItemTags.BEDS))) {
            hint(player, Mind.Hint.NIGHT, now, Component.translatable("cogwit.think.night"));
        }
        if (player.isEyeInFluid(FluidTags.WATER) && !player.hasEffect(MobEffects.WATER_BREATHING)
                && player.getAirSupply() < player.getMaxAirSupply() * Mind.AIR_LOW) {
            int up = 1;
            BlockPos eyes = BlockPos.containing(player.getEyePosition());
            while (up < 48 && level.getFluidState(eyes.above(up)).is(FluidTags.WATER)) {
                up++;
            }
            hint(player, Mind.Hint.AIR, now, up >= 48 ? Component.translatable("cogwit.think.air_deep")
                    : Component.translatable("cogwit.think.air", up));
        }
        if (hand.isDamageableItem() && hand.getMaxDamage() - hand.getDamageValue() <= Mind.TOOL_WARN
                && (hand.isEnchanted() || hand.getRarity() != net.minecraft.world.item.Rarity.COMMON
                || hand.getMaxDamage() >= 1000)) {
            hint(player, Mind.Hint.TOOL, now, Component.translatable("cogwit.think.tool", hand.getHoverName(),
                    hand.getMaxDamage() - hand.getDamageValue()));
        }
    }

    private static void hint(Player player, Mind.Hint hint, long now, Component text) {
        EnumMap<Mind.Hint, Long> last = LAST_HINT.computeIfAbsent(player.getUUID(), id -> new EnumMap<>(Mind.Hint.class));
        if (Mind.ready(last.getOrDefault(hint, 0L), now)) {
            last.put(hint, now);
            Speech.think(player, text);
            dev.cogwit.speech.CogwitVoice.beep(player, hint == Mind.Hint.NIGHT || hint == Mind.Hint.TOOL
                    ? dev.cogwit.speech.CogwitVoice.Beep.THINK : dev.cogwit.speech.CogwitVoice.Beep.ALERT);
        }
    }

    /** The scan key: pay, then outline valuable ores and chests nearby on the owner's screen. */
    public static Mind.Scan scan(Player player) {
        Optional<ItemStack> worn = CogwitTalismanItem.worn(player);
        if (worn.isEmpty()) {
            return Mind.Scan.LOCKED;
        }
        ItemStack talisman = worn.get();
        long now = player.level().getGameTime();
        CellBank.topUp(player, talisman);
        BrainState brain = CogwitTalismanItem.brain(talisman);
        MindState mind = state(talisman);
        Mind.Scan check = Mind.canScan(brain, mind, now);
        if (check != Mind.Scan.OK) {
            Speech.hint(player, check == Mind.Scan.COOLDOWN
                    ? Component.translatable("cogwit.mind.scan.cooldown", Brain.clock(mind.scanReadyAt() - now))
                    : Component.translatable("cogwit.mind.scan." + check.name().toLowerCase()));
            return check;
        }
        CogwitTalismanItem.setBrain(talisman, brain.withTokens(brain.tokens() - Mind.SCAN_TOKEN_COST));
        setState(talisman, mind.withScanReadyAt(now + Mind.SCAN_COOLDOWN_TICKS));
        BlockPos centre = player.blockPosition();
        Found found = scanAround(player.level(), centre);
        List<MindContent.Find> shown = Mind.pickFinds(found.ores(), MindContent.Find::ore,
                f -> f.pos().distSqr(centre), Mind.SCAN_LIMIT);
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new ScanResultPayload(centre, shown,
                    found.containers().subList(0, Math.min(found.containers().size(), Mind.SCAN_CONTAINER_LIMIT))));
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                1f, 1.6f);
        dev.cogwit.speech.CogwitVoice.beep(player, dev.cogwit.speech.CogwitVoice.Beep.THINK);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
                0.5f, 1.8f);
        Speech.hint(player, summary(found));
        return Mind.Scan.OK;
    }

    /** "Diamonds 3 · Iron 14 · Chests 1": the three most valuable kinds found, then containers. */
    static Component summary(Found found) {
        EnumMap<Mind.Ore, Integer> counts = new EnumMap<>(Mind.Ore.class);
        for (MindContent.Find f : found.ores()) {
            counts.merge(f.ore(), 1, Integer::sum);
        }
        if (counts.isEmpty() && found.containers().isEmpty()) {
            return Component.translatable("cogwit.mind.scan.nothing", Mind.SCAN_RADIUS);
        }
        net.minecraft.network.chat.MutableComponent line = Component.empty();
        int shown = 0;
        for (Map.Entry<Mind.Ore, Integer> e : counts.entrySet()) {
            if (shown == 3) {
                break;
            }
            if (shown++ > 0) {
                line.append(" · ");
            }
            line.append(Component.translatable("cogwit.ore." + e.getKey().key(), e.getValue()));
        }
        if (!found.containers().isEmpty()) {
            if (shown > 0) {
                line.append(" · ");
            }
            line.append(Component.translatable("cogwit.ore.chest", found.containers().size()));
        }
        return Component.translatable("cogwit.mind.scan.found", line);
    }

    /** Everything worth outlining around {@code center}, closest first. */
    public record Found(List<MindContent.Find> ores, List<BlockPos> containers) {}

    /**
     * Every ore and container within {@link Mind#SCAN_RADIUS} of {@code center}, closest first. Only loaded chunks,
     * and chunk sections whose palette has nothing of interest are skipped without looking at their blocks.
     */
    public static Found scanAround(Level level, BlockPos center) {
        List<MindContent.Find> ores = new ArrayList<>();
        List<BlockPos> containers = new ArrayList<>();
        int r = Mind.SCAN_RADIUS, r2 = r * r;
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - r);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + r);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = (center.getX() - r) >> 4; cx <= (center.getX() + r) >> 4; cx++) {
            for (int cz = (center.getZ() - r) >> 4; cz <= (center.getZ() + r) >> 4; cz++) {
                if (!(level.getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) {
                    continue;
                }
                for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                    if (section.hasOnlyAir() || !section.maybeHas(MindContent::scanned)) {
                        continue;
                    }
                    for (int y = Math.max(minY, sy << 4); y <= Math.min(maxY, (sy << 4) + 15); y++) {
                        for (int x = cx << 4; x < (cx << 4) + 16; x++) {
                            for (int z = cz << 4; z < (cz << 4) + 16; z++) {
                                int dx = x - center.getX(), dy = y - center.getY(), dz = z - center.getZ();
                                if (dx * dx + dy * dy + dz * dz > r2) {
                                    continue;
                                }
                                BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                                Mind.Ore ore = MindContent.ore(state);
                                if (ore != null) {
                                    ores.add(new MindContent.Find(pos.set(x, y, z).immutable(), ore));
                                } else if (state.is(MindContent.SCAN_CONTAINERS)) {
                                    containers.add(pos.set(x, y, z).immutable());
                                }
                            }
                        }
                    }
                }
            }
        }
        ores.sort(Comparator.comparingDouble(f -> f.pos().distSqr(center)));
        containers.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        return new Found(ores, containers);
    }

    /** The mark key: show what the player points at to everyone nearby, through walls. */
    public static boolean mark(Player player, int entityId, BlockPos pos) {
        if (CogwitTalismanItem.worn(player).isEmpty() || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        double reach = Mind.MARK_REACH + 4;
        Component label;
        BlockPos at;
        if (entityId >= 0) {
            Entity entity = level.getEntity(entityId);
            if (entity == null || entity.distanceTo(player) > reach) {
                return false;
            }
            if (entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) Mind.MARK_SHOW_TICKS, 0, false, false));
            }
            label = entity.getDisplayName();
            at = entity.blockPosition();
        } else {
            if (pos.distToCenterSqr(player.position()) > reach * reach) {
                return false;
            }
            label = level.getBlockState(pos).getBlock().getName();
            at = pos;
        }
        String author = player.getName().getString();
        MarkShowPayload show = new MarkShowPayload(at, author, label.getString());
        for (ServerPlayer other : level.players()) {
            if (other.distanceToSqr(Vec3.atCenterOf(at)) <= Mind.MARK_SHARE_RANGE * Mind.MARK_SHARE_RANGE) {
                PacketDistributor.sendToPlayer(other, show);
                other.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8f, 1.5f);
                if (other != player) {
                    Speech.say(other, Component.translatable("cogwit.mind.mark_chat", author, label,
                            Math.round(Math.sqrt(other.distanceToSqr(Vec3.atCenterOf(at))))));
                }
            }
        }
        return true;
    }

    /** Remembers how the player died, for the comment after respawning. */
    public static void recordDeath(Player player, DamageSource source) {
        Entity killer = source.getEntity();
        String damage = source.typeHolder().unwrapKey().map(k -> k.location().getPath()).orElse("");
        boolean boss = killer != null && killer.getType().is(GrowthContent.BOSSES);
        Mind.DeathKind kind = Mind.classify(damage, boss, killer instanceof Mob, killer instanceof Player);
        MindContent.Deaths before = player.getData(MindContent.DEATHS);
        String type = killer instanceof Mob ? net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(killer.getType()).toString() : "";
        int streak = !type.isEmpty() && type.equals(before.killerType()) ? before.streak() + 1 : type.isEmpty() ? 0 : 1;
        player.setData(MindContent.DEATHS, new MindContent.Deaths(kind.name().toLowerCase(),
                killer != null ? killer.getDisplayName().getString() : "", before.count() + 1,
                player.level().dimension().location().toString(), player.blockPosition(), type, streak));
    }

    /** Back from the dead: Cogwit has something to say about it. */
    public static void commentOnDeath(Player player) {
        MindContent.Deaths deaths = player.getData(MindContent.DEATHS);
        if (deaths.count() == 0 || deaths.kind().isEmpty() || CogwitTalismanItem.worn(player).isEmpty()) {
            return;
        }
        if (deaths.streak() >= 2) {
            Speech.say(player, Component.translatable("cogwit.death.again", deaths.killer(), deaths.streak()));
        } else {
            Speech.say(player, Component.translatable("cogwit.death." + deaths.kind() + "." + Mind.variant(deaths.count()),
                    deaths.killer(), deaths.count()));
        }
        dev.cogwit.speech.CogwitVoice.beep(player, dev.cogwit.speech.CogwitVoice.Beep.SAD);
        DeathCompass.start(player, deaths);
    }
}
