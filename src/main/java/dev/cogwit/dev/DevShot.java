package dev.cogwit.dev;

import dev.cogwit.CogwitMod;
import dev.cogwit.brain.BrainState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EntityType;
import dev.cogwit.crew.CrewService;
import dev.cogwit.crew.CrewGearMenu;
import dev.cogwit.crew.CrewGear;
import dev.cogwit.crew.Crew;
import dev.cogwit.mind.MindService;
import dev.cogwit.brain.Skill;
import dev.cogwit.cells.CellBank;
import dev.cogwit.cells.CellContent;
import dev.cogwit.cells.TokenCellItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import dev.cogwit.guide.GuideClientState;
import dev.cogwit.guide.client.RoadmapScreen;
import dev.cogwit.guide.net.GuideTargetPayload;
import dev.cogwit.horizon.HorizonContent;
import dev.cogwit.horizon.HorizonState;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/**
 * Development-only visual check: with {@code -Dcogwit.devShot=true} the game equips a demo Cogwit,
 * takes screenshots of the HUD, the inventory panel and the shoulder model, then quits.
 * Does nothing in normal play.
 */
public final class DevShot {
    static final boolean ENABLED = Boolean.getBoolean("cogwit.devShot");
    /** "armor" runs only {@link ClientSide#armorLineup}; anything else runs the whole tour. */
    static final boolean ARMOR_ONLY = "armor".equals(System.getProperty("cogwit.devShot.mode"))
            || "exoframe".equals(System.getProperty("cogwit.devShot.mode"))
            || "horizon".equals(System.getProperty("cogwit.devShot.mode"))
            || String.valueOf(System.getProperty("cogwit.devShot.mode")).startsWith("powers")
            || "gifs".equals(System.getProperty("cogwit.devShot.mode"));
    /** The stands wear the copper exoframe instead of the player's Horizon set. */
    static final boolean EXOFRAME = "exoframe".equals(System.getProperty("cogwit.devShot.mode"));
    static final boolean HORIZON = "horizon".equals(System.getProperty("cogwit.devShot.mode"));
    /** "powers" casts trophy actives into a ring of husks; "powers_min" does it with particles set to minimal. */
    static final boolean POWERS = String.valueOf(System.getProperty("cogwit.devShot.mode")).startsWith("powers");
    static final boolean MIN_PARTICLES = "powers_min".equals(System.getProperty("cogwit.devShot.mode"));
    /** "gifs" records the scenes for the mod page frame by frame (gif_<scene>_<n>.png) and the crafting recipe. */
    static final boolean GIFS = "gifs".equals(System.getProperty("cogwit.devShot.mode"));

    private DevShot() {}

    @EventBusSubscriber(modid = CogwitMod.MODID)
    public static final class ServerSide {
        private ServerSide() {}

        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (!ENABLED || !(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            long now = player.level().getGameTime();
            BrainState demo = new BrainState(List.of(
                    new Skill("fire", 30, now, now, "", 0),
                    new Skill("explosion", 12, now - 400, now - 400, "", 0),
                    new Skill("fall", 4, now - 800, now - 800, "", 0),
                    new Skill("mob", 11, now - 900, now - 900, "projectile", 5)),
                    1370, 0, now + 6000, now + 1200, now - 1000, 0).withXp(620).withBackShieldReadyAt(now + 100);
            ItemStack stack = new ItemStack(CogwitMod.COGWIT.get());
            CogwitTalismanItem.setBrain(stack, demo);
            CuriosApi.getCuriosInventory(player).flatMap(inv -> inv.getStacksHandler("charm"))
                    .ifPresent(handler -> handler.getStacks().setStackInSlot(0, stack));
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(HorizonContent.HELMET.get()));
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(HorizonContent.CHESTPLATE.get()));
            player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(HorizonContent.LEGGINGS.get()));
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(HorizonContent.BOOTS.get()));
            CellBank.setCells(player.getItemBySlot(EquipmentSlot.HEAD), List.of(2500));
            CellBank.setCells(player.getItemBySlot(EquipmentSlot.CHEST), List.of(2500, 1800, 700, 0));
            CellBank.setCells(player.getItemBySlot(EquipmentSlot.LEGS), List.of(1200));
            HorizonContent.setState(player, HorizonState.EMPTY.withMass(64f).withLastCombat(now)
                    .withWell(now + 280, 0));
            player.serverLevel().setDayTime(6000);
            placeChargerDemo(player);
            player.setYRot(180f);
            player.setXRot(0f);
        }
    }

    /** Wakes Cogwit up at level 4 and sends the whole crew down in different gear. */
    private static void sendCrewDown(ServerPlayer player) {
        CogwitTalismanItem.worn(player).ifPresent(stack -> {
            CogwitTalismanItem.setBrain(stack, CogwitTalismanItem.brain(stack).withSleepUntil(0).withXp(1100));
            CrewGear.set(stack, Crew.Kind.COGWIT, List.of(ItemStack.EMPTY, new ItemStack(Items.IRON_CHESTPLATE),
                    new ItemStack(Items.IRON_SWORD)));
            CrewGear.set(stack, Crew.Kind.SPARK, List.of(new ItemStack(Items.GOLDEN_HELMET), ItemStack.EMPTY,
                    ItemStack.EMPTY));
            CrewGear.set(stack, Crew.Kind.RIVET, List.of(ItemStack.EMPTY, new ItemStack(Items.DIAMOND_CHESTPLATE),
                    new ItemStack(Items.DIAMOND_AXE)));
            CrewGear.set(stack, Crew.Kind.ANVIL, List.of(new ItemStack(Items.NETHERITE_HELMET),
                    new ItemStack(Items.NETHERITE_CHESTPLATE), new ItemStack(Items.MACE)));
        });
        CrewService.deploy(player, CrewService.Reason.KEY);
        for (var creature : CrewService.crewOf(player)) {
            creature.setNoAi(true);
            creature.setYRot(player.getYRot());
            creature.yBodyRot = creature.getYRot();
            creature.yHeadRot = creature.getYRot();
        }
    }

    /** Level 5 Cogwit, the crew back home, and an Elder Guardian that has picked the player as its target. */
    private static void bossArrives(ServerPlayer player) {
        CogwitTalismanItem.worn(player).ifPresent(stack -> {
            CrewService.recall(player, stack, null);
            CogwitTalismanItem.setBrain(stack, CogwitTalismanItem.brain(stack).withXp(2100).withTokens(2000));
        });
        var boss = EntityType.ELDER_GUARDIAN.create(player.serverLevel());
        if (boss != null) {
            boss.moveTo(player.getX() + 1.5, player.getY(), player.getZ() + 5, 0, 0);
            boss.setNoAi(true);
            boss.setTarget(player);
            player.serverLevel().addFreshEntity(boss);
        }
    }

    /** Hides diamonds and a chest under and around the player, scans for them and marks a spot for the friend. */
    private static void buryTreasure(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos base = player.blockPosition();
        // lay everything out along the way the player looks, so the first-person shot sees it
        double yaw = Math.toRadians(player.getYRot());
        int fx = (int) Math.round(-Math.sin(yaw)), fz = (int) Math.round(Math.cos(yaw));
        int sx = -fz, sz = fx;
        int[][] ores = {{6, 2, -2}, {8, -3, -3}, {10, 1, -2}, {7, 3, -1}};
        for (int[] o : ores) {
            level.setBlockAndUpdate(base.offset(fx * o[0] + sx * o[1], o[2], fz * o[0] + sz * o[1]),
                    Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState());
        }
        level.setBlockAndUpdate(base.offset(fx * 5 - sx * 2, -2, fz * 5 - sz * 2), Blocks.CHEST.defaultBlockState());
        BlockPos markAt = base.offset(fx * 14, 0, fz * 14);
        level.setBlockAndUpdate(markAt, Blocks.STONE_BRICKS.defaultBlockState());
        CogwitTalismanItem.worn(player).ifPresent(stack ->
                MindService.setState(stack, MindService.state(stack).withScanReadyAt(0)));
        MindService.scan(player);
        MindService.mark(player, -1, markAt);
    }

    private static final List<net.minecraft.world.entity.monster.Creeper> DANGER_CREEPER = new java.util.ArrayList<>();

    /** Things for Cogwit's eye to bracket: a zombie, an aiming skeleton, creepers ahead and behind, a diamond. */
    private static void jarvisScene(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double yaw = Math.toRadians(player.getYRot());
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), side = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 base = player.position();
        var zombie = EntityType.ZOMBIE.create(level);
        place(level, zombie, base.add(fwd.scale(4)).add(side.scale(-1.5)), player);
        var skeleton = EntityType.SKELETON.create(level);
        if (skeleton != null) {
            skeleton.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
            place(level, skeleton, base.add(fwd.scale(9)).add(side.scale(-4)), player);
            skeleton.startUsingItem(InteractionHand.MAIN_HAND);
        }
        var behind = EntityType.CREEPER.create(level);
        place(level, behind, base.add(fwd.scale(-3)).add(side.scale(1)), player);
        var hissing = EntityType.CREEPER.create(level);
        place(level, hissing, base.add(fwd.scale(6)).add(side.scale(3)), player);
        DANGER_CREEPER.clear();
        DANGER_CREEPER.add(hissing);
        DANGER_CREEPER.add(behind);
        Vec3 at = base.add(fwd.scale(3)).add(side.scale(1.5));
        var diamond = new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y + 0.2, at.z,
                new ItemStack(Items.DIAMOND, 3));
        diamond.setNoPickUpDelay();
        diamond.setPickUpDelay(32767);
        level.addFreshEntity(diamond);
    }

    private static void place(ServerLevel level, net.minecraft.world.entity.Mob mob, Vec3 at, ServerPlayer player) {
        if (mob == null) {
            return;
        }
        mob.moveTo(at.x, at.y, at.z, 0, 0);
        mob.setNoAi(true);
        level.addFreshEntity(mob);
        mob.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
        mob.yBodyRot = mob.getYRot();
        mob.yHeadRot = mob.getYRot();
    }

    /** Zombies and loot spread out in front of the player for the skills to throw around. */
    private static void skillArena(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double yaw = Math.toRadians(player.getYRot());
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), side = new Vec3(-fwd.z, 0, fwd.x);
        for (int i = 0; i < 6; i++) {
            var zombie = EntityType.HUSK.create(level);
            if (zombie != null) {
                Vec3 at = player.position().add(fwd.scale(5 + (i % 3) * 2)).add(side.scale((i - 2.5) * 1.6));
                zombie.moveTo(at.x, at.y, at.z, 0, 0);
                level.addFreshEntity(zombie);
            }
        }
        for (int i = 0; i < 5; i++) {
            Vec3 at = player.position().add(fwd.scale(6)).add(side.scale(i - 2)).add(0, 0.3, 0);
            var item = new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y, at.z,
                    new ItemStack(i % 2 == 0 ? Items.GOLD_INGOT : Items.EMERALD));
            item.setPickUpDelay(32767);
            level.addFreshEntity(item);
        }
    }

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET};
    private static final ItemStack[] TAKEN_OFF = new ItemStack[ARMOR.length];

    /** Takes the Horizon set off; {@link #suitUp} puts it back a few ticks later so the change is noticed. */
    private static void takeOff(ServerPlayer player) {
        for (int i = 0; i < ARMOR.length; i++) {
            TAKEN_OFF[i] = player.getItemBySlot(ARMOR[i]).copy();
            player.setItemSlot(ARMOR[i], ItemStack.EMPTY);
        }
    }

    private static void suitUp(ServerPlayer player) {
        for (int i = 0; i < ARMOR.length; i++) {
            player.setItemSlot(ARMOR[i], TAKEN_OFF[i]);
        }
    }

    /** If Create is installed, put a charger with a cell and a creative motor in front of the player. */
    private static void placeChargerDemo(ServerPlayer player) {
        Block charger = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(CogwitMod.MODID, "cell_charger"));
        Block motor = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "creative_motor"));
        if (charger == Blocks.AIR || motor == Blocks.AIR) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition().relative(Direction.NORTH, 3);
        BlockState state = charger.defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            if (property.getName().contains("axis") && property.getPossibleValues().contains(Direction.Axis.X)) {
                state = with(state, property, Direction.Axis.X);
            }
        }
        level.setBlockAndUpdate(pos, state);
        BlockState motorState = motor.defaultBlockState();
        for (Property<?> property : motorState.getProperties()) {
            if (property.getName().equals("facing")) {
                motorState = with(motorState, property, Direction.WEST);
            }
        }
        level.setBlockAndUpdate(pos.east(), motorState);
        ItemStack cell = TokenCellItem.withCharge(new ItemStack(CellContent.TOKEN_CELL.get()), 900);
        player.setItemInHand(InteractionHand.MAIN_HAND, cell);
        level.getBlockState(pos).useItemOn(cell, level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState with(BlockState state, Property property, Comparable value) {
        return state.setValue(property, value);
    }

    @EventBusSubscriber(modid = CogwitMod.MODID, value = Dist.CLIENT)
    public static final class ClientSide {
        private static int ticks;

        private ClientSide() {}

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (!ENABLED || mc.player == null || mc.level == null) {
                return;
            }
            ticks++;
            if (ARMOR_ONLY) {
                armorLineup(mc, ticks);
                return;
            }
            switch (ticks) {
                case 100 -> {
                    GuideClientState.updateProgress(0b1111);
                    GuideClientState.updateTarget(new GuideTargetPayload(true, (int) mc.player.getX() + 180,
                            (int) mc.player.getZ() - 420, "cogwit.guide.target.trial_chambers"));
                }
                case 120 -> shot(mc, "cogwit_1_hud");
                case 125 -> mc.setScreen(new InventoryScreen(mc.player));
                case 150 -> shot(mc, "cogwit_2_inventory");
                case 155 -> {
                    mc.setScreen(null);
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                }
                case 190 -> shot(mc, "cogwit_3_shoulder_front");
                case 191 -> mc.options.keyShift.setDown(true);
                case 194 -> shot(mc, "cogwit_3b_shoulder_crouch");
                case 195 -> {
                    mc.options.keyShift.setDown(false);
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                }
                case 230 -> shot(mc, "cogwit_4_shoulder_back");
                case 232 -> {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    putCogwitToSleep(mc);
                }
                case 262 -> shot(mc, "cogwit_6_asleep_front");
                case 265 -> {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    mc.setScreen(new RoadmapScreen());
                }
                case 285 -> shot(mc, "cogwit_5_roadmap");
                case 288 -> {
                    mc.setScreen(null);
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                }
                case 384 -> onServer(mc, DevShot::sendCrewDown);
                case 420 -> shot(mc, "cogwit_7_crew");
                case 422 -> onServer(mc, DevShot::takeOff);
                case 426 -> onServer(mc, DevShot::suitUp);
                case 470 -> onServer(mc, player -> CrewService.crewOf(player).stream()
                        .filter(c -> c.kind() == Crew.Kind.ANVIL).findFirst()
                        .ifPresent(anvil -> CrewGearMenu.open(player, anvil)));
                case 490 -> shot(mc, "cogwit_8_gear");
                case 494 -> {
                    mc.setScreen(null);
                    onServer(mc, DevShot::bossArrives);
                }
                case 600 -> shot(mc, "cogwit_10_ultra_crew");
                case 606 -> {
                    onServer(mc, DevShot::buryTreasure);
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    mc.player.setXRot(30f);
                }
                case 650 -> shot(mc, "cogwit_11_scan_mark");
                case 652 -> {
                    onServer(mc, DevShot::jarvisScene);
                    mc.player.setXRot(6f);
                }
                case 664 -> onServer(mc, player -> DANGER_CREEPER.forEach(c -> c.setSwellDir(1)));
                case 668 -> shot(mc, "cogwit_12_jarvis");
                case 670 -> onServer(mc, player -> DANGER_CREEPER.forEach(net.minecraft.world.entity.Entity::discard));
                case 672 -> onServer(mc, player -> {
                    player.getFoodData().setFoodLevel(9);
                    player.getInventory().add(new ItemStack(Items.BREAD, 5));
                    player.getInventory().add(new ItemStack(Items.WATER_BUCKET));
                    CogwitTalismanItem.worn(player).ifPresent(t -> dev.cogwit.agent.AgentService.setState(t,
                            dev.cogwit.agent.AgentState.DEFAULT));
                });
                case 726 -> shot(mc, "cogwit_13_ask");
                case 730 -> mc.setScreen(new dev.cogwit.client.AgentScreen());
                case 750 -> shot(mc, "cogwit_14_agent");
                case 762 -> {
                    mc.setScreen(null);
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    mc.player.setXRot(12f);
                    onServer(mc, DevShot::skillArena);
                }
                case 770 -> onServer(mc, player -> {
                    dev.cogwit.horizon.HorizonContent.setState(player,
                            dev.cogwit.horizon.HorizonState.EMPTY.withMass(20f));
                    dev.cogwit.horizon.HorizonActions.gravity(player);
                });
                case 836 -> {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    onServer(mc, DevShot::skillArena);
                }
                case 840 -> onServer(mc, player -> {
                    dev.cogwit.horizon.HorizonContent.setState(player,
                            dev.cogwit.horizon.HorizonState.EMPTY.withMass(dev.cogwit.horizon.Horizon.MASS_MAX));
                    dev.cogwit.horizon.HorizonActions.gravity(player);
                });
                case 900 -> {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    mc.player.setXRot(-4f);
                }
                case 904 -> onServer(mc, dev.cogwit.horizon.HorizonActions::blink);
                case 932 -> {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    onServer(mc, player -> {
                        for (var t : new dev.cogwit.trophy.Trophy[] {dev.cogwit.trophy.Trophy.ENDER_DRAGON,
                                dev.cogwit.trophy.Trophy.WITHER, dev.cogwit.trophy.Trophy.WARDEN,
                                dev.cogwit.trophy.Trophy.ELDER_GUARDIAN, dev.cogwit.trophy.Trophy.IGNIS,
                                dev.cogwit.trophy.Trophy.HARBINGER, dev.cogwit.trophy.Trophy.FROSTMAW,
                                dev.cogwit.trophy.Trophy.UMVUTHI, dev.cogwit.trophy.Trophy.VALKYRIE_QUEEN,
                                dev.cogwit.trophy.Trophy.DEAD_KING}) {
                            dev.cogwit.trophy.TrophyService.award(player, t);
                        }
                    });
                }
                case 960 -> shot(mc, "cogwit_15_third_person");
                case 962 -> {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    mc.setScreen(new dev.cogwit.client.AbilityWheelScreen(dev.cogwit.client.CogwitClient.WHEEL_KEY));
                }
                case 963 -> shot(mc, "cogwit_16_wheel");
                case 966 -> mc.setScreen(new dev.cogwit.client.TrophyCodexScreen());
                case 980 -> shot(mc, "cogwit_17_codex");
                case 984 -> {
                    mc.setScreen(null);
                    mc.player.setXRot(8f);
                    onServer(mc, DevShot::skillArena);
                }
                case 990 -> onServer(mc, p -> dev.cogwit.trophy.TrophyService.castTrophy(p,
                        dev.cogwit.trophy.Trophy.WARDEN));
                case 1010 -> onServer(mc, p -> {
                    p.setXRot(25f);
                    dev.cogwit.trophy.TrophyService.castTrophy(p, dev.cogwit.trophy.Trophy.ENDER_DRAGON);
                });
                case 1040 -> onServer(mc, p -> dev.cogwit.trophy.TrophyService.castTrophy(p,
                        dev.cogwit.trophy.Trophy.WITHER));
                case 1060 -> onServer(mc, p -> {
                    p.setXRot(4f);
                    dev.cogwit.trophy.TrophyService.castTrophy(p, dev.cogwit.trophy.Trophy.ELDER_GUARDIAN);
                });
                case 1080 -> dev.cogwit.client.menu.MenuTabs.open(dev.cogwit.client.menu.MenuTabs.Tab.HELP);
                case 1084 -> shot(mc, "cogwit_18_help");
                case 1086 -> dev.cogwit.client.menu.MenuTabs.open(dev.cogwit.client.menu.MenuTabs.Tab.SETTINGS);
                case 1090 -> shot(mc, "cogwit_19_settings");
                case 1092 -> dev.cogwit.client.menu.MenuTabs.open(dev.cogwit.client.menu.MenuTabs.Tab.PATH);
                case 1096 -> shot(mc, "cogwit_20_path_tabs");
                case 1098 -> {
                    mc.setScreen(null);
                    onServer(mc, player -> {
                        for (var t : new dev.cogwit.trophy.Trophy[] {dev.cogwit.trophy.Trophy.NETHERITE_MONSTROSITY,
                                dev.cogwit.trophy.Trophy.ENDER_GUARDIAN, dev.cogwit.trophy.Trophy.LEVIATHAN,
                                dev.cogwit.trophy.Trophy.ANCIENT_REMNANT, dev.cogwit.trophy.Trophy.MALEDICTUS,
                                dev.cogwit.trophy.Trophy.SCYLLA}) {
                            dev.cogwit.trophy.TrophyService.award(player, t);
                        }
                    });
                }
                case 1107 -> mc.setScreen(new dev.cogwit.client.AbilityWheelScreen(dev.cogwit.client.CogwitClient.WHEEL_KEY));
                case 1108 -> shot(mc, "cogwit_21_wheel_full");
                case 1110 -> {
                    mc.setScreen(null);
                    shot(mc, "cogwit_22_hud_compact");
                }
                case 1112 -> onServer(mc, player -> {
                    for (var t : dev.cogwit.trophy.Trophy.values()) {
                        dev.cogwit.trophy.TrophyService.award(player, t);
                    }
                });
                case 1114 -> {
                    for (var t : new dev.cogwit.trophy.Trophy[] {dev.cogwit.trophy.Trophy.WARDEN,
                            dev.cogwit.trophy.Trophy.SCYLLA, dev.cogwit.trophy.Trophy.NAGA, dev.cogwit.trophy.Trophy.HYDRA,
                            dev.cogwit.trophy.Trophy.INFERNAL_DRAGON}) {
                        if (!dev.cogwit.ClientConfig.favourites().contains(t)) {
                            dev.cogwit.ClientConfig.toggleFavourite(t);
                        }
                    }
                }
                case 1120 -> mc.setScreen(new dev.cogwit.client.TrophyCodexScreen());
                case 1128 -> shot(mc, "cogwit_23_codex_all");
                case 1130 -> mc.setScreen(new dev.cogwit.client.AbilityWheelScreen(dev.cogwit.client.CogwitClient.WHEEL_KEY));
                case 1131 -> shot(mc, "cogwit_24_wheel_all");
                case 1132 -> wheelPage(mc, 1);
                case 1133 -> shot(mc, "cogwit_25_wheel_page2");
                case 1134 -> wheelPage(mc, 2);
                case 1135 -> shot(mc, "cogwit_26_wheel_twilight");
                case 1138 -> mc.stop();
                default -> {
                    // two seconds of frames for the armor's idle animation, then the suit-up
                    if (ticks >= 300 && ticks < 380 && ticks % 2 == 0) {
                        shot(mc, String.format("cogwit_anim_%02d", (ticks - 300) / 2));
                    }
                    if (ticks >= 500 && ticks < 560 && ticks % 4 == 0) {
                        shot(mc, String.format("cogwit_ultra_%02d", (ticks - 500) / 4));
                    }
                    if (ticks >= 772 && ticks < 834 && ticks % 3 == 0) {
                        shot(mc, String.format("cogwit_well_%02d", (ticks - 772) / 3));
                    }
                    if (ticks >= 841 && ticks < 890 && ticks % 2 == 1) {
                        shot(mc, String.format("cogwit_collapse_%02d", (ticks - 841) / 2));
                    }
                    if (ticks >= 903 && ticks < 913) {
                        shot(mc, String.format("cogwit_blink_%02d", ticks - 903));
                    }
                    if (ticks >= 990 && ticks < 1086 && ticks % 3 == 0) {
                        shot(mc, String.format("cogwit_trophy_%02d", (ticks - 990) / 3));
                    }
                    if (ticks >= 427 && ticks < 467 && ticks % 2 == 1) {
                        shot(mc, String.format("cogwit_equip_%02d", (ticks - 427) / 2));
                    }
                }
            }
        }

        private static void onServer(Minecraft mc, java.util.function.Consumer<ServerPlayer> action) {
            if (mc.getSingleplayerServer() == null) {
                return;
            }
            java.util.UUID id = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                if (player != null) {
                    action.accept(player);
                }
            });
        }

        /** As if Cogwit had just saved the player a minute ago: closed eye on the visor, apple timer on the panel. */
        private static void putCogwitToSleep(Minecraft mc) {
            if (mc.getSingleplayerServer() == null) {
                return;
            }
            java.util.UUID id = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                if (player == null) {
                    return;
                }
                long now = player.level().getGameTime();
                CogwitTalismanItem.worn(player).ifPresent(stack -> CogwitTalismanItem.setBrain(stack,
                        CogwitTalismanItem.brain(stack).asleep(now - 1200, now + 10800).withRescuedAt(now - 1200)));
            });
        }

        /**
         * Four invisible armor stands in the Horizon set three blocks ahead, turned to show the front, three quarters,
         * the side and the back, with the HUD hidden; one shot, then the game quits.
         */
        private static void armorLineup(Minecraft mc, int ticks) {
            if (EXOFRAME) {
                exoframeShots(mc, ticks);
                return;
            }
            if (HORIZON) {
                horizonShots(mc, ticks);
                return;
            }
            if (POWERS) {
                powerShots(mc, ticks);
                return;
            }
            if (GIFS) {
                gifScenes(mc, ticks);
                return;
            }
            switch (ticks) {
                case 60 -> onServer(mc, player -> {
                    var level = player.serverLevel();
                    // mobs wander in and the stands of earlier runs are saved with the world
                    for (var old : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                            player.getBoundingBox().inflate(40), e -> e instanceof net.minecraft.world.entity.Mob
                                    || e instanceof net.minecraft.world.entity.decoration.ArmorStand)) {
                        old.discard();
                    }
                    double z = player.getZ() - 4.6;
                    float[] yaws = {0f, -40f, -90f, 180f};
                    for (int i = 0; i < yaws.length; i++) {
                        var stand = new net.minecraft.world.entity.decoration.ArmorStand(level,
                                player.getX() - 2.7 + i * 1.8, player.getY(), z);
                        stand.setYRot(yaws[i]);
                        stand.setYBodyRot(yaws[i]);
                        stand.setYHeadRot(yaws[i]);
                        stand.setInvisible(true);
                        stand.setNoGravity(true);
                        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                            // each stand gets its own animation id, or GeckoLib animates them all as the player's armor
                            ItemStack piece = player.getItemBySlot(slot).copy();
                            piece.remove(software.bernie.geckolib.GeckoLibConstants.STACK_ANIMATABLE_ID_COMPONENT.get());
                            software.bernie.geckolib.animatable.GeoItem.getOrAssignId(piece, level);
                            stand.setItemSlot(slot, piece);
                        }
                        // the second stand shows the faceplate raised
                        if (i == 1) {
                            stand.getItemBySlot(EquipmentSlot.HEAD).set(dev.cogwit.CogwitComponents.VISOR_OPEN.get(), true);
                        }
                        level.addFreshEntity(stand);
                    }
                });
                case 64 -> {
                    mc.player.setYRot(180f);
                    mc.player.setXRot(8f);
                    mc.options.hideGui = true;
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                }
                case 90 -> shot(mc, "armor_lineup");
                case 94 -> mc.stop();
                default -> { }
            }
        }

        /**
         * The copper exoframe on the player, seen from the front and the back over diamond and iron armor (no boots,
         * to show both fits), then over bare skin; then the game quits.
         */
        private static void exoframeShots(Minecraft mc, int ticks) {
            switch (ticks) {
                case 60 -> onServer(mc, player -> {
                    player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                    player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                    player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                    player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                    CuriosApi.getCuriosInventory(player).ifPresent(inv -> {
                        inv.getStacksHandler(dev.cogwit.exoframe.ExoframeContent.UPPER_SLOT).ifPresent(h -> h.getStacks()
                                .setStackInSlot(0, new ItemStack(dev.cogwit.exoframe.ExoframeContent.UPPER.get())));
                        inv.getStacksHandler(dev.cogwit.exoframe.ExoframeContent.LOWER_SLOT).ifPresent(h -> h.getStacks()
                                .setStackInSlot(0, new ItemStack(dev.cogwit.exoframe.ExoframeContent.LOWER.get())));
                    });
                });
                case 64 -> {
                    mc.options.hideGui = true;
                    mc.player.setYRot(180f);
                    mc.player.setXRot(10f);
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                }
                case 90 -> shot(mc, "exo_front");
                case 92 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                case 110 -> shot(mc, "exo_back");
                case 112 -> {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    onServer(mc, player -> {
                        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                            player.setItemSlot(slot, ItemStack.EMPTY);
                        }
                    });
                }
                case 130 -> shot(mc, "exo_bare");
                case 134 -> mc.stop();
                default -> { }
            }
        }

        /**
         * The Horizon suit in motion: flying on its boot and hip jets, the landing crouch with its shockwave, then
         * the scan wave over a buried ore field with a thought line on screen; then the game quits.
         */
        private static void horizonShots(Minecraft mc, int ticks) {
            switch (ticks) {
                case 60 -> onServer(mc, player -> {
                    player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(dev.cogwit.horizon.HorizonContent.HELMET.get()));
                    player.setItemSlot(EquipmentSlot.CHEST,
                            new ItemStack(dev.cogwit.horizon.HorizonContent.CHESTPLATE.get()));
                    player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(dev.cogwit.horizon.HorizonContent.LEGGINGS.get()));
                    player.setItemSlot(EquipmentSlot.FEET, new ItemStack(dev.cogwit.horizon.HorizonContent.BOOTS.get()));
                    // earlier tours leave mobs, loot, stands and demo blocks in the saved world: clear the stage
                    var level = player.serverLevel();
                    for (var old : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                            player.getBoundingBox().inflate(40), e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
                        old.discard();
                    }
                    BlockPos feet = player.blockPosition();
                    for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-12, 0, -12), feet.offset(12, 6, 12))) {
                        level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    }
                    dev.cogwit.horizon.HorizonContent.setState(player, dev.cogwit.horizon.HorizonState.EMPTY);
                    player.teleportTo(player.getX(), player.getY() + 2.5, player.getZ());
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                });
                case 64 -> {
                    mc.options.hideGui = true;
                    mc.player.setYRot(180f);
                    mc.player.setXRot(12f);
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                }
                case 92 -> shot(mc, "horizon_fly");
                case 94 -> onServer(mc, player -> {
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                    player.teleportTo(player.getX(), player.getY() - 2.5, player.getZ());
                    dev.cogwit.horizon.HorizonJets.land(player, 1f);
                });
                case 95 -> mc.player.setYBodyRot(mc.player.getYRot() + 40f);
                case 98 -> shot(mc, "horizon_land");
                case 120 -> {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    mc.player.setXRot(55f);
                    mc.options.hideGui = false;
                    BlockPos origin = mc.player.blockPosition();
                    java.util.List<dev.cogwit.mind.MindContent.Find> finds = new java.util.ArrayList<>();
                    dev.cogwit.mind.Mind.Ore[] kinds = dev.cogwit.mind.Mind.Ore.values();
                    for (int i = 0; i < 60; i++) {
                        double a = i * 2.39996, r = 2 + Math.sqrt(i) * 3.4;
                        BlockPos at = origin.offset((int) Math.round(Math.cos(a) * r), -2 - i % 5,
                                (int) Math.round(Math.sin(a) * r));
                        finds.add(new dev.cogwit.mind.MindContent.Find(at, kinds[i % kinds.length]));
                    }
                    dev.cogwit.mind.MindClientState.showScan(origin, finds, java.util.List.of(origin.offset(5, -1, -6)));
                    dev.cogwit.speech.SpeechClientState.think(net.minecraft.network.chat.Component.translatable(
                            "cogwit.think.lava"));
                }
                case 128 -> shot(mc, "horizon_scan_wave");
                case 150 -> shot(mc, "horizon_scan_done");
                case 152 -> {
                    mc.options.hideGui = true;
                    mc.player.setYRot(180f);
                    mc.player.setXRot(15f);
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    onServer(mc, player -> dev.cogwit.brain.BackSlab.raise(player,
                            player.position().add(0.6, 0, 3), 3));
                }
                case 160 -> shot(mc, "slab_stone");
                case 172 -> onServer(mc, player -> dev.cogwit.brain.BackSlab.raise(player,
                        player.position().add(-2.5, 0, 2), 5));
                case 178 -> shot(mc, "slab_copper");
                case 182 -> mc.stop();
                default -> { }
            }
        }

        /**
         * Trophy actives seen from behind and above: the Netherite Monstrosity's slam into a ring of husks, then
         * Umvuthi's solar beam on a husk ahead; a shot every few ticks, then the game quits.
         */
        private static void powerShots(Minecraft mc, int ticks) {
            switch (ticks) {
                case 60 -> {
                    mc.options.particles().set(MIN_PARTICLES ? net.minecraft.client.ParticleStatus.MINIMAL
                            : net.minecraft.client.ParticleStatus.ALL);
                    onServer(mc, player -> {
                        var level = player.serverLevel();
                        for (var old : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                                player.getBoundingBox().inflate(40),
                                e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
                            old.discard();
                        }
                        BlockPos feet = player.blockPosition();
                        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-12, 0, -12), feet.offset(12, 6, 12))) {
                            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                        }
                        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                        level.setDayTime(6000);
                        for (int i = 0; i < 8; i++) {
                            double a = i * Math.PI / 4 + 0.3, r = i % 2 == 0 ? 3.6 : 5.8;
                            powerTarget(level, player.position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
                        }
                    });
                }
                case 64 -> {
                    mc.options.hideGui = true;
                    mc.player.setYRot(180f);
                    mc.player.setXRot(42f);
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                }
                case 70 -> onServer(mc, dev.cogwit.trophy.CataclysmStrikes::lavaSlam);
                case 71 -> shot(mc, "power_slam_0");
                case 74 -> shot(mc, "power_slam_1");
                case 79 -> shot(mc, "power_slam_2");
                case 86 -> shot(mc, "power_slam_3");
                case 94 -> shot(mc, "power_slam_4");
                case 104 -> shot(mc, "power_slam_5");
                case 130 -> onServer(mc, player -> {
                    var level = player.serverLevel();
                    for (var old : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                            player.getBoundingBox().inflate(40))) {
                        old.discard();
                    }
                    powerTarget(level, player.position().add(3, 0, -7));
                });
                case 134 -> mc.player.setXRot(20f);
                case 136 -> onServer(mc, dev.cogwit.trophy.MowziePowers::solarBeam);
                case 142 -> shot(mc, "power_beam_charge");
                case 154 -> shot(mc, "power_beam_burn");
                case 170 -> shot(mc, "power_beam_late");
                case 200 -> mc.stop();
                default -> { }
            }
        }

        /** Which scene the frames being recorded belong to, and from which tick; null between scenes. */
        private static String recording;
        private static int recordFrom;

        /**
         * The mod page's GIFs. The server runs at half speed so the client can save a frame every tick; played back at
         * 20 frames a second they show the game at normal speed. Scenes: the crafting recipe (one still), the back
         * slab catching an arrow, the ore scan, a Horizon landing, the Monstrosity's slam, Umvuthi's beam, the crew
         * jumping down. Then the game quits.
         */
        private static void gifScenes(Minecraft mc, int ticks) {
            if (recording != null) {
                Screenshot.grab(mc.gameDirectory, String.format("gif_%s_%03d.png", recording, ticks - recordFrom),
                        mc.getMainRenderTarget(), message -> { });
            }
            switch (ticks) {
                case 60 -> {
                    mc.options.particles().set(net.minecraft.client.ParticleStatus.ALL);
                    onServer(mc, player -> {
                        var level = player.serverLevel();
                        clearStage(player);
                        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                        level.setDayTime(6000);
                        // ores buried by earlier runs would light up in other scenes
                        BlockPos feet = player.blockPosition();
                        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-34, -3, -34), feet.offset(34, -1, 34))) {
                            if (level.getBlockState(pos).is(net.neoforged.neoforge.common.Tags.Blocks.ORES)) {
                                level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
                            }
                        }
                        level.getServer().tickRateManager().setTickRate(10f);
                        // no trophies: their passives (the Sculptor's platform) and the agent's combos would join in
                        player.setData(dev.cogwit.trophy.TrophyContent.DATA, dev.cogwit.trophy.TrophyContent.Data.NONE);
                        // the recipe on a crafting table
                        BlockPos table = player.blockPosition().offset(0, 0, -2);
                        level.setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
                        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) ->
                                new net.minecraft.world.inventory.CraftingMenu(id, inv,
                                        net.minecraft.world.inventory.ContainerLevelAccess.create(level, table)),
                                net.minecraft.network.chat.Component.translatable("container.crafting")));
                        net.minecraft.world.item.Item[] grid = {Items.COPPER_BLOCK, Items.AMETHYST_SHARD,
                                Items.COPPER_BLOCK, Items.AMETHYST_SHARD, Items.REDSTONE_BLOCK, Items.AMETHYST_SHARD,
                                Items.COPPER_BLOCK, Items.ENDER_EYE, Items.COPPER_BLOCK};
                        for (int i = 0; i < grid.length; i++) {
                            player.containerMenu.getSlot(1 + i).set(new ItemStack(grid[i]));
                        }
                        player.containerMenu.broadcastChanges();
                    });
                }
                case 66 -> shot(mc, "gif_recipe");
                case 68 -> onServer(mc, player -> {
                    player.closeContainer();
                    clearStage(player);
                    CogwitTalismanItem.worn(player).ifPresent(stack -> CogwitTalismanItem.setBrain(stack,
                            CogwitTalismanItem.brain(stack).withBackShieldReadyAt(0)));
                    // iron instead of the Horizon set, whose chestplate would turn the arrow back on its own
                    takeOff(player);
                    Item[] iron = {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
                    for (int i = 0; i < ARMOR.length; i++) {
                        player.setItemSlot(ARMOR[i], new ItemStack(iron[i]));
                    }
                    // a skeleton behind and to the side, in a helmet so the sun leaves it alone
                    var level = player.serverLevel();
                    var skeleton = EntityType.SKELETON.create(level);
                    if (skeleton != null) {
                        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                        skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                        place(level, skeleton, player.position().add(3, 0, 8), player);
                    }
                });
                case 70 -> {
                    mc.options.hideGui = true;
                    mc.player.setYRot(180f);
                    mc.player.setXRot(10f);
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                }
                case 76 -> record("slab", ticks);
                case 80 -> onServer(mc, player -> {
                    var level = player.serverLevel();
                    for (var skeleton : level.getEntitiesOfClass(net.minecraft.world.entity.monster.Skeleton.class,
                            player.getBoundingBox().inflate(16))) {
                        var arrow = new net.minecraft.world.entity.projectile.Arrow(level, skeleton,
                                new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
                        arrow.setPos(skeleton.getX(), skeleton.getEyeY() - 0.1, skeleton.getZ());
                        Vec3 to = player.getBoundingBox().getCenter().subtract(arrow.position());
                        arrow.shoot(to.x, to.y + 0.3, to.z, 1.6f, 0f);
                        level.addFreshEntity(arrow);
                    }
                });
                case 112 -> {
                    recording = null;
                    onServer(mc, player -> {
                        clearStage(player);
                        suitUp(player);
                        buryOres(player);
                    });
                    mc.options.hideGui = true;
                    mc.player.setYRot(180f);
                    mc.player.setXRot(50f);
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                }
                case 132 -> record("scan", ticks);
                case 136 -> onServer(mc, player -> {
                    CogwitTalismanItem.worn(player).ifPresent(stack ->
                            MindService.setState(stack, MindService.state(stack).withScanReadyAt(0)));
                    MindService.scan(player);
                });
                case 176 -> {
                    recording = null;
                    onServer(mc, player -> {
                        clearStage(player);
                        var level = player.serverLevel();
                        for (int i = 0; i < 6; i++) {
                            double a = i * Math.PI / 3 + 0.4, r = i % 2 == 0 ? 2.6 : 3.6;
                            powerTarget(level, player.position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
                        }
                        player.getAbilities().flying = false;
                        player.onUpdateAbilities();
                        player.teleportTo(player.getX(), player.getY() + 9, player.getZ());
                    });
                    mc.options.hideGui = true;
                    mc.player.setYRot(180f);
                    mc.player.setXRot(20f);
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                }
                case 178 -> record("landing", ticks);
                case 216 -> {
                    recording = null;
                    onServer(mc, player -> {
                        clearStage(player);
                        var level = player.serverLevel();
                        for (int i = 0; i < 8; i++) {
                            double a = i * Math.PI / 4 + 0.3, r = i % 2 == 0 ? 3.6 : 5.8;
                            powerTarget(level, player.position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
                        }
                        // the agent would open a gravity well into the crowd
                        long now = level.getGameTime();
                        HorizonContent.setState(player, HorizonContent.state(player).withWell(now + 100000, 0));
                    });
                    // the camera sits between two of the inner ring's geysers rather than inside one
                    mc.player.setYRot(200.5f);
                    mc.player.setXRot(42f);
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                }
                case 220 -> record("slam", ticks);
                case 224 -> onServer(mc, dev.cogwit.trophy.CataclysmStrikes::lavaSlam);
                case 286 -> {
                    recording = null;
                    onServer(mc, player -> {
                        clearStage(player);
                        // dusk, so the beam stands out against the sky
                        player.serverLevel().setDayTime(13000);
                        powerTarget(player.serverLevel(), player.position().add(1, 0, -4.5));
                    });
                    mc.player.setYRot(180f);
                    mc.player.setXRot(12f);
                }
                case 290 -> record("beam", ticks);
                case 294 -> onServer(mc, dev.cogwit.trophy.MowziePowers::solarBeam);
                case 362 -> {
                    recording = null;
                    onServer(mc, player -> {
                        clearStage(player);
                        player.serverLevel().setDayTime(6000);
                        CogwitTalismanItem.worn(player).ifPresent(stack -> CogwitTalismanItem.setBrain(stack,
                                CogwitTalismanItem.brain(stack).withSleepUntil(0).withXp(1100)));
                    });
                    mc.player.setYRot(180f);
                    mc.player.setXRot(15f);
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                }
                case 366 -> record("crew", ticks);
                case 370 -> onServer(mc, player -> CrewService.deploy(player, CrewService.Reason.KEY));
                case 406 -> {
                    recording = null;
                    onServer(mc, player -> player.serverLevel().getServer().tickRateManager().setTickRate(20f));
                }
                case 412 -> mc.stop();
                default -> { }
            }
        }

        private static void record(String scene, int ticks) {
            recording = scene;
            recordFrom = ticks;
        }

        /** Removes everything but players within 40 blocks and clears the air round the player. */
        private static void clearStage(ServerPlayer player) {
            var level = player.serverLevel();
            for (var old : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                    player.getBoundingBox().inflate(40), e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
                old.discard();
            }
            BlockPos feet = player.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-12, 0, -12), feet.offset(12, 6, 12))) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }

        /** A field of ores of every kind under the ground ahead of the player, for the scan to find. */
        private static void buryOres(ServerPlayer player) {
            var level = player.serverLevel();
            BlockPos base = player.blockPosition();
            Block[] kinds = {Blocks.DIAMOND_ORE, Blocks.GOLD_ORE, Blocks.EMERALD_ORE, Blocks.IRON_ORE,
                    Blocks.REDSTONE_ORE, Blocks.LAPIS_ORE, Blocks.COPPER_ORE, Blocks.COAL_ORE};
            // a cone ahead (the player faces -z), two and three blocks down in the flat world's dirt
            for (int i = 0; i < 70; i++) {
                double a = (i * 2.39996) % 2.2 - 1.1, r = 3 + Math.sqrt(i) * 2.1;
                BlockPos at = base.offset((int) Math.round(Math.sin(a) * r), -2 - i % 2, (int) Math.round(-Math.cos(a) * r));
                level.setBlockAndUpdate(at, kinds[i % kinds.length].defaultBlockState());
            }
        }

        /** A husk that stays where it is put (slowness) but still gets thrown around by the skill. */
        private static void powerTarget(ServerLevel level, Vec3 at) {
            var husk = EntityType.HUSK.create(level);
            if (husk != null) {
                husk.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360, 0);
                husk.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20 * 60, 9, false, false));
                husk.setPersistenceRequired();
                level.addFreshEntity(husk);
            }
        }

        /** Opens the ability wheel again and flips it {@code turns} pages on with Tab, as the player would. */
        private static void wheelPage(Minecraft mc, int turns) {
            var wheel = new dev.cogwit.client.AbilityWheelScreen(dev.cogwit.client.CogwitClient.WHEEL_KEY);
            mc.setScreen(wheel);
            for (int i = 0; i < turns; i++) {
                wheel.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB, 0, 0);
            }
        }

        private static void shot(Minecraft mc, String name) {
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> { });
            CogwitMod.LOGGER.info("DevShot saved {} (brain tokens={})", name,
                    CogwitTalismanItem.worn(mc.player).map(s -> CogwitTalismanItem.brain(s).tokens()).orElse(-1));
        }
    }
}
