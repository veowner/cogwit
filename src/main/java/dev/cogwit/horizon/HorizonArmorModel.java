package dev.cogwit.horizon;

import dev.cogwit.CogwitMod;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedItemGeoModel;

/** The armor model, plus the faceplate, which swings up and down smoothly for each wearer on its own. */
public class HorizonArmorModel extends DefaultedItemGeoModel<HorizonArmorItem> {
    /** How far the faceplate swings up, in radians, and how much of the swing it covers per tick. */
    static final float OPEN_ANGLE = (float) Math.toRadians(-100), SPEED = 0.16f;

    /** Per wearer: how far open the faceplate is (0..1), when that was worked out, and what it was heading for. */
    private static final class Swing {
        float progress;
        double at;
        boolean target;
        boolean raised;
    }

    private final Int2ObjectOpenHashMap<Swing> swings = new Int2ObjectOpenHashMap<>();

    public HorizonArmorModel() {
        super(CogwitMod.id("armor/horizon_armor"));
    }

    @Override
    public void setCustomAnimations(HorizonArmorItem animatable, long instanceId, AnimationState<HorizonArmorItem> state) {
        super.setCustomAnimations(animatable, instanceId, state);
        if (state.getData(DataTickets.EQUIPMENT_SLOT) != EquipmentSlot.HEAD
                || !(state.getData(DataTickets.ENTITY) instanceof LivingEntity wearer)) {
            return;
        }
        ItemStack helmet = state.getData(DataTickets.ITEMSTACK);
        if (helmet == null) {
            return;
        }
        float progress = swing(wearer, helmet, state.getPartialTick());
        getBone("faceplate").ifPresent(bone -> bone.setRotX(bone.getRotX() + OPEN_ANGLE * ease(progress)));
    }

    /** Moves this wearer's faceplate towards where it should be and returns how far open it is now. */
    private float swing(LivingEntity wearer, ItemStack helmet, float partialTick) {
        Swing s = swings.computeIfAbsent(wearer.getId(), id -> new Swing());
        double now = wearer.level().getGameTime() + partialTick;
        boolean raised = HorizonVisor.raised(helmet);
        boolean target = HorizonVisor.shouldBeOpen(wearer, helmet);
        if (s.at == 0) {
            s.progress = target ? 1 : 0;
        } else {
            float step = (float) Math.max(0, Math.min(4, now - s.at)) * SPEED;
            s.progress = target ? Math.min(1, s.progress + step) : Math.max(0, s.progress - step);
            // the server plays the sound when the wearer flips it by hand; a meal lifting it is heard here
            if (target != s.target && raised == s.raised) {
                wearer.level().playLocalSound(wearer.getX(), wearer.getEyeY(), wearer.getZ(),
                        target ? HorizonSounds.VISOR_OPEN.get() : HorizonSounds.VISOR_CLOSE.get(),
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.1f, false);
            }
        }
        s.at = now;
        s.target = target;
        s.raised = raised;
        if (swings.size() > 64) {
            var level = Minecraft.getInstance().level;
            swings.int2ObjectEntrySet().removeIf(e -> level == null || level.getEntity(e.getIntKey()) == null);
        }
        return s.progress;
    }

    /** Slow at both ends, with a small settle at the top. */
    private static float ease(float t) {
        float smooth = t * t * (3 - 2 * t);
        return smooth + 0.06f * (float) Math.sin(Math.PI * t) * t;
    }
}
