package dev.cogwit.crew;

import dev.cogwit.CogwitComponents;
import dev.cogwit.Speech;
import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.cells.CellBank;
import dev.cogwit.item.CogwitTalismanItem;
import dev.cogwit.ultra.UltraService;
import dev.cogwit.ultra.Overdrive;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Sends Cogwit (and his brothers) into the fight and brings them back to the shoulder. Server side only. */
public final class CrewService {
    public enum Reason { KEY, RESCUE, OVERDRIVE }

    private CrewService() {}

    public static CrewState state(ItemStack talisman) {
        return talisman.getOrDefault(CogwitComponents.CREW.get(), CrewState.EMPTY);
    }

    public static void setState(ItemStack talisman, CrewState state) {
        talisman.set(CogwitComponents.CREW.get(), state);
    }

    public static boolean isDeployed(ItemStack talisman) {
        return state(talisman).deployed();
    }

    /** The V key: down into the fight, or back up to the shoulder. */
    public static void toggle(Player player) {
        CogwitTalismanItem.worn(player).ifPresent(talisman -> {
            if (isDeployed(talisman)) {
                recall(player, talisman, "cogwit.crew.recalled");
            } else {
                deploy(player, Reason.KEY);
            }
        });
    }

    /** @return true when the crew is out after the call */
    public static boolean deploy(Player player, Reason reason) {
        Optional<ItemStack> worn = CogwitTalismanItem.worn(player);
        if (worn.isEmpty() || !(player.level() instanceof ServerLevel)) {
            return false;
        }
        ItemStack talisman = worn.get();
        if (isDeployed(talisman)) {
            return true;
        }
        long now = player.level().getGameTime();
        CellBank.topUp(player, talisman);
        BrainState brain = CogwitTalismanItem.brain(talisman);
        if (reason == Reason.KEY) {
            Crew.Deploy check = Crew.canDeploy(brain, brain.tokens(), now);
            if (check != Crew.Deploy.OK) {
                Speech.hint(player, Component.translatable("cogwit.crew.cant." + check.name().toLowerCase()));
                return false;
            }
        }
        List<Crew.Kind> squad = Crew.squad(brain, state(talisman), now);
        for (int i = 0; i < squad.size(); i++) {
            spawn(player, talisman, squad.get(i), i);
        }
        setState(talisman, state(talisman).withDeployed(true));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_TURTLE.value(),
                SoundSource.PLAYERS, 1f, 1.5f);
        if (reason == Reason.RESCUE) {
            Speech.say(player, Component.translatable("cogwit.crew.covering"));
        } else if (reason == Reason.OVERDRIVE) {
            Speech.hint(player, Component.translatable("cogwit.crew.ultra"));
        } else {
            Speech.hint(player, Component.translatable("cogwit.crew.deployed", Crew.upkeep(squad)));
        }
        return true;
    }

    /** Everyone back on the shoulder; their gear goes back onto the talisman with whatever wear it took. */
    public static void recall(Player player, ItemStack talisman, String message) {
        for (CogwitCreature creature : crewOf(player)) {
            CrewGear.set(talisman, creature.kind(), gearOf(creature));
            poof(creature);
            creature.discard();
        }
        if (isDeployed(talisman)) {
            setState(talisman, state(talisman).withDeployed(false));
            if (message != null) {
                Speech.hint(player, Component.translatable(message));
            }
        }
    }

    /** Called every second while the talisman is worn: pay upkeep, bring back recovered brothers, notice losses. */
    public static void tick(Player player, ItemStack talisman, long now) {
        if (!isDeployed(talisman)) {
            return;
        }
        List<CogwitCreature> crew = crewOf(player);
        if (crew.stream().noneMatch(c -> c.kind() == Crew.Kind.COGWIT)) {
            recall(player, talisman, null); // Cogwit is gone (unloaded, other dimension): he is simply back up
            return;
        }
        BrainState brain = CogwitTalismanItem.brain(talisman);
        boolean covering = Crew.isCovering(brain, now);
        if (Brain.isAsleep(brain, now) && !covering) {
            recall(player, talisman, "cogwit.crew.sleepy");
            return;
        }
        List<Crew.Kind> squad = Crew.squad(brain, state(talisman), now);
        for (int i = 0; i < squad.size(); i++) {
            Crew.Kind kind = squad.get(i);
            if (crew.stream().noneMatch(c -> c.kind() == kind)) {
                spawn(player, talisman, kind, i);
                Speech.hint(player, Component.translatable("cogwit.crew.returned",
                        Component.translatable("cogwit.crew." + kind.key())));
            }
        }
        if (covering) {
            return;
        }
        CellBank.topUp(player, talisman);
        brain = CogwitTalismanItem.brain(talisman);
        int cost = Overdrive.upkeep(Crew.upkeep(squad), UltraService.state(talisman));
        if (brain.tokens() < cost) {
            recall(player, talisman, "cogwit.crew.out_of_tokens");
            return;
        }
        CogwitTalismanItem.setBrain(talisman, brain.withTokens(brain.tokens() - cost));
        CogwitTalismanItem.warnIfHungry(player, brain.tokens(), brain.tokens() - cost);
    }

    /** A crew member hit zero health: Cogwit is knocked back to the shoulder, a brother sits out for a while. */
    public static void creatureDown(Player owner, CogwitCreature creature) {
        Optional<ItemStack> worn = CogwitTalismanItem.worn(owner);
        if (worn.isEmpty()) {
            return;
        }
        ItemStack talisman = worn.get();
        long now = owner.level().getGameTime();
        CrewGear.set(talisman, creature.kind(), gearOf(creature));
        if (creature.kind() == Crew.Kind.COGWIT) {
            CogwitTalismanItem.setBrain(talisman, Crew.knockout(CogwitTalismanItem.brain(talisman), now));
            for (CogwitCreature other : crewOf(owner)) {
                if (other != creature) {
                    CrewGear.set(talisman, other.kind(), gearOf(other));
                    poof(other);
                    other.discard();
                }
            }
            setState(talisman, state(talisman).withDeployed(false));
            Speech.say(owner, Component.translatable("cogwit.crew.knocked_out", Brain.clock(Crew.KNOCKOUT_TICKS)));
        } else {
            setState(talisman, Crew.brotherFell(state(talisman), creature.kind(), now));
            Speech.say(owner, Component.translatable("cogwit.crew.brother_fell",
                    Component.translatable("cogwit.crew." + creature.kind().key()),
                    Brain.clock(Crew.BROTHER_RETURN_TICKS)));
        }
    }

    public static void callForHelp(Player owner, CogwitCreature creature) {
        Speech.say(owner, creature.kind() == Crew.Kind.COGWIT
                ? Component.translatable("cogwit.crew.help_me")
                : Component.translatable("cogwit.crew.help_brother",
                Component.translatable("cogwit.crew." + creature.kind().key())));
    }

    /** Every crew member of this player, in any loaded dimension. */
    public static List<CogwitCreature> crewOf(Player player) {
        List<CogwitCreature> crew = new ArrayList<>();
        if (player.getServer() == null) {
            return crew;
        }
        for (ServerLevel level : player.getServer().getAllLevels()) {
            crew.addAll(level.getEntities(EntityTypeTest.forClass(CogwitCreature.class),
                    c -> c.isAlive() && player.getUUID().equals(c.getOwnerUUID())));
        }
        return crew;
    }

    static List<ItemStack> gearOf(CogwitCreature creature) {
        List<ItemStack> gear = new ArrayList<>();
        for (var slot : CrewGear.SLOTS) {
            gear.add(creature.getItemBySlot(slot).copy());
        }
        return gear;
    }

    private static void spawn(Player player, ItemStack talisman, Crew.Kind kind, int index) {
        ServerLevel level = (ServerLevel) player.level();
        CogwitCreature creature = CrewContent.CREATURE.get().create(level);
        if (creature == null) {
            return;
        }
        creature.setKind(kind);
        creature.joinCrewOf(player);
        List<ItemStack> gear = CrewGear.of(talisman, kind);
        for (int i = 0; i < CrewGear.SLOTS.length; i++) {
            creature.setItemSlot(CrewGear.SLOTS[i], gear.get(i));
        }
        // fan out around the owner, starting from the left shoulder Cogwit sits on
        double angle = Math.toRadians(player.getYRot() + 90 + index * 70);
        Vec3 at = player.position().add(-Math.sin(angle) * 1.2, 0.2, Math.cos(angle) * 1.2);
        creature.moveTo(at.x, at.y, at.z, player.getYRot(), 0);
        if (!level.noCollision(creature)) {
            creature.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
        }
        level.addFreshEntity(creature);
        creature.jumpedOff();
        level.sendParticles(ParticleTypes.WAX_ON, creature.getX(), creature.getY() + 0.4, creature.getZ(), 12, 0.3,
                0.3, 0.3, 0.1);
    }

    private static void poof(CogwitCreature creature) {
        if (creature.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.WAX_OFF, creature.getX(), creature.getY() + 0.4, creature.getZ(), 12,
                    0.3, 0.3, 0.3, 0.1);
        }
    }
}
