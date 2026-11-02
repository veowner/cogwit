package dev.cogwit.test;

import dev.cogwit.CogwitMod;
import dev.cogwit.compat.create.CreateCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Create integration checks for the cell charger, run with {@code ./gradlew runGameTestServer}.
 * This class must stay free of Create types (even in private signatures): without Create the tests pass
 * at once, since the charger does not exist then, and the scenarios are never loaded.
 */
@GameTestHolder(CogwitMod.MODID)
@PrefixGameTestTemplate(false)
public final class ChargerGameTests {
    private ChargerGameTests() {}

    @GameTest(template = "empty")
    public static void chargerIsRegisteredWithStressImpact(GameTestHelper helper) {
        if (!CreateCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ChargerScenarios.chargerIsRegisteredWithStressImpact(helper);
    }

    @GameTest(template = "empty")
    public static void chargerTakesOneCellAndGivesItBack(GameTestHelper helper) {
        if (!CreateCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ChargerScenarios.chargerTakesOneCellAndGivesItBack(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void spinningMotorChargesTheCell(GameTestHelper helper) {
        if (!CreateCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ChargerScenarios.spinningMotorChargesTheCell(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chargingStopsAtCapacity(GameTestHelper helper) {
        if (!CreateCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ChargerScenarios.chargingStopsAtCapacity(helper);
    }

    @GameTest(template = "empty")
    public static void idleChargerDoesNothingAndSyncsToClient(GameTestHelper helper) {
        if (!CreateCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ChargerScenarios.idleChargerDoesNothingAndSyncsToClient(helper);
    }
}
