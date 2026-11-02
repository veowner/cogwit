package dev.cogwit.test;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

/** Mock players for GameTests, kept free of the pack's random damage so hits come out exact. */
final class TestPlayers {
    /** Apothic Attributes gives every player a crit chance; with it a test hit sometimes lands x1.5. */
    private static final ResourceLocation CRIT_CHANCE = ResourceLocation.fromNamespaceAndPath("apothic_attributes", "crit_chance");

    private TestPlayers() {}

    static Player mock(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BuiltInRegistries.ATTRIBUTE.getHolder(CRIT_CHANCE).map(player::getAttribute)
                .ifPresent(crit -> crit.setBaseValue(0));
        return player;
    }
}
