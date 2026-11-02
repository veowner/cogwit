package dev.cogwit.horizon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.cogwit.CogwitMod;
import net.minecraft.Util;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;

/** Registrations for the Event Horizon armor: material, items and per-player state. */
public final class HorizonContent {
    public static final DeferredRegister<ArmorMaterial> MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, CogwitMod.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CogwitMod.MODID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> HORIZON = MATERIALS.register("horizon",
            () -> new ArmorMaterial(
                    Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                        map.put(ArmorItem.Type.HELMET, 4);
                        map.put(ArmorItem.Type.CHESTPLATE, 9);
                        map.put(ArmorItem.Type.LEGGINGS, 7);
                        map.put(ArmorItem.Type.BOOTS, 4);
                        map.put(ArmorItem.Type.BODY, 11);
                    }),
                    15, SoundEvents.ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.of(HorizonContent.SINGULARITY_CORE.get()),
                    List.of(new ArmorMaterial.Layer(CogwitMod.id("horizon"))),
                    5f, 0.25f));

    public static final DeferredItem<Item> SINGULARITY_CORE = CogwitMod.ITEMS.register("singularity_core",
            () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<Item> TEMPLATE = CogwitMod.ITEMS.register("horizon_template",
            () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

    public static final DeferredItem<HorizonArmorItem> HELMET = armor("horizon_helmet", ArmorItem.Type.HELMET);
    public static final DeferredItem<HorizonArmorItem> CHESTPLATE = armor("horizon_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<HorizonArmorItem> LEGGINGS = armor("horizon_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<HorizonArmorItem> BOOTS = armor("horizon_boots", ArmorItem.Type.BOOTS);

    public static final Codec<HorizonState> STATE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("mass", 0f).forGetter(HorizonState::mass),
            Codec.LONG.optionalFieldOf("last_combat", 0L).forGetter(HorizonState::lastCombat),
            Codec.LONG.optionalFieldOf("blink_ready_at", 0L).forGetter(HorizonState::blinkReadyAt),
            Codec.LONG.optionalFieldOf("well_ready_at", 0L).forGetter(HorizonState::wellReadyAt),
            Codec.LONG.optionalFieldOf("well_until", 0L).forGetter(HorizonState::wellUntil),
            Codec.LONG.optionalFieldOf("void_rescue_ready_at", 0L).forGetter(HorizonState::voidRescueReadyAt),
            Codec.STRING.listOf().optionalFieldOf("tips_seen", List.of()).forGetter(HorizonState::tipsSeen)
    ).apply(i, HorizonState::new));

    public static final Supplier<AttachmentType<HorizonState>> STATE = ATTACHMENTS.register("horizon",
            () -> AttachmentType.builder(() -> HorizonState.EMPTY).serialize(STATE_CODEC).copyOnDeath().build());

    private HorizonContent() {}

    public static void register(IEventBus modBus) {
        MATERIALS.register(modBus);
        ATTACHMENTS.register(modBus);
    }

    private static DeferredItem<HorizonArmorItem> armor(String name, ArmorItem.Type type) {
        return CogwitMod.ITEMS.register(name, () -> new HorizonArmorItem(HORIZON, type, new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .fireResistant()
                .component(DataComponents.UNBREAKABLE, new Unbreakable(true))));
    }

    public static boolean wearsFullSet(Player player) {
        return pieces(player) == Horizon.FULL;
    }

    /** Which Horizon parts the player wears, as a {@link Horizon.Piece} bit mask. */
    public static int pieces(LivingEntity wearer) {
        int mask = 0;
        if (wearer.getItemBySlot(EquipmentSlot.HEAD).is(HELMET.get())) mask |= Horizon.Piece.HELMET.bit;
        if (wearer.getItemBySlot(EquipmentSlot.CHEST).is(CHESTPLATE.get())) mask |= Horizon.Piece.CHEST.bit;
        if (wearer.getItemBySlot(EquipmentSlot.LEGS).is(LEGGINGS.get())) mask |= Horizon.Piece.LEGS.bit;
        if (wearer.getItemBySlot(EquipmentSlot.FEET).is(BOOTS.get())) mask |= Horizon.Piece.BOOTS.bit;
        return mask;
    }

    public static boolean has(LivingEntity wearer, Horizon.Feature feature) {
        return Horizon.has(pieces(wearer), feature);
    }

    public static HorizonState state(Player player) {
        return player.getData(STATE);
    }

    public static void setState(Player player, HorizonState state) {
        player.setData(STATE, state);
    }
}
