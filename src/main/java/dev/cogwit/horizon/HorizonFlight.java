package dev.cogwit.horizon;

import dev.cogwit.BalanceConfig;
import dev.cogwit.Speech;
import dev.cogwit.cells.CellBank;
import dev.cogwit.cells.Cells;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The full set's flight runs on tokens: cells first, then Cogwit's own tank. With none left, the suit drops you. */
public final class HorizonFlight {
    private static final Set<UUID> STARVED = new HashSet<>();

    private HorizonFlight() {}

    /** Called every tick for a full-set wearer; pays for the last second of flight and says whether flight stays on. */
    public static boolean tick(Player player) {
        int cost = BalanceConfig.flightTokensPerSecond();
        if (cost <= 0 || player.isCreative() || player.isSpectator()) {
            return true;
        }
        boolean flying = player.getAbilities().flying;
        if (flying && player.tickCount % 20 == 0) {
            drain(player, cost);
        }
        boolean fuel = available(player) > 0;
        if (!fuel && STARVED.add(player.getUUID())) {
            Speech.hint(player, Component.translatable("cogwit.horizon.flight_empty"));
        } else if (fuel) {
            STARVED.remove(player.getUUID());
        }
        return fuel;
    }

    /** Tokens in worn cells plus the worn Cogwit's tank. */
    public static int available(Player player) {
        int cells = Cells.total(CellBank.worn(player).all());
        return cells + CogwitTalismanItem.worn(player).map(s -> CogwitTalismanItem.brain(s).tokens()).orElse(0);
    }

    /** Takes {@code amount} tokens, cells first; returns what could not be paid. */
    public static int drain(Player player, int amount) {
        CellBank.Worn worn = CellBank.worn(player);
        int left = amount;
        if (Cells.total(worn.all()) > 0) {
            Cells.TopUp t = Cells.topUp(0, amount, worn.all());
            CellBank.write(worn, t.cells());
            left -= t.ownTokens();
        }
        Optional<ItemStack> cogwit = CogwitTalismanItem.worn(player);
        if (left > 0 && cogwit.isPresent()) {
            var brain = CogwitTalismanItem.brain(cogwit.get());
            int paid = Math.min(left, brain.tokens());
            CogwitTalismanItem.setBrain(cogwit.get(), brain.withTokens(brain.tokens() - paid));
            left -= paid;
        }
        return left;
    }

    public static void forget(UUID player) {
        STARVED.remove(player);
    }
}
