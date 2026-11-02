package dev.cogwit.trophy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Block and item displays as pure scenery: stone shards that rise out of the ground and sink back, an ice block that
 * tumbles through the air, swords that fly at their victims, all without ever touching the terrain. The client
 * interpolates between transformations, so a shard grows smoothly over a few ticks.
 * Displays carry {@link #TAG} so any that survive a server stop are removed when they load again.
 */
public final class DisplayFx {
    static final String TAG = "cogwit_fx";

    private DisplayFx() {}

    /**
     * A shard standing on {@code base} that grows from nothing to {@code height} over {@code growTicks}. {@code yaw}
     * turns it round its own axis, {@code tiltX} and {@code tiltZ} lean it (radians) around its foot.
     */
    static Display.BlockDisplay rise(ServerLevel level, BlockState block, Vec3 base, float width, float height,
            float yaw, float tiltX, float tiltZ, int growTicks) {
        Display.BlockDisplay shard = EntityType.BLOCK_DISPLAY.create(level);
        if (shard == null) {
            return null;
        }
        Quaternionf lean = new Quaternionf().rotationYXZ(yaw, tiltX, tiltZ);
        CompoundTag tag = new CompoundTag();
        tag.put("Pos", doubles(base.x, base.y, base.z));
        tag.put("block_state", NbtUtils.writeBlockState(block));
        tag.put("transformation", transformation(lean, width, 0.05f, width));
        tag.putInt("interpolation_duration", 0);
        shard.load(tag);
        shard.addTag(TAG);
        level.addFreshEntity(shard);
        TrophyScheduler.later(level, 1, () -> reshape(shard, lean, width, height, width, growTicks));
        return shard;
    }

    /**
     * A wall standing on {@code base}: {@code width} across, {@code depth} thick, growing to {@code height} over
     * {@code growTicks}. {@code yaw} turns its thin side to face along (sin yaw, cos yaw).
     */
    public static Display.BlockDisplay wall(ServerLevel level, BlockState block, Vec3 base, float width, float depth,
            float height, float yaw, int growTicks) {
        Display.BlockDisplay wall = EntityType.BLOCK_DISPLAY.create(level);
        if (wall == null) {
            return null;
        }
        Quaternionf turn = new Quaternionf().rotationY(yaw);
        CompoundTag tag = new CompoundTag();
        tag.put("Pos", doubles(base.x, base.y, base.z));
        tag.put("block_state", NbtUtils.writeBlockState(block));
        tag.put("transformation", transformation(turn, width, 0.05f, depth));
        tag.putInt("interpolation_duration", 0);
        wall.load(tag);
        wall.addTag(TAG);
        level.addFreshEntity(wall);
        TrophyScheduler.later(level, 1, () -> reshape(wall, turn, width, height, depth, growTicks));
        return wall;
    }

    /** Sinks a wall made by {@link #wall} back into the ground over {@code ticks} and removes it. */
    public static void sinkWall(ServerLevel level, Display.BlockDisplay wall, float width, float depth, int ticks) {
        if (wall == null || wall.isRemoved()) {
            return;
        }
        Quaternionf turn = leftRotation(wall.saveWithoutId(new CompoundTag()));
        reshape(wall, turn, width, 0.05f, depth, ticks);
        TrophyScheduler.later(level, ticks + 1, wall::discard);
    }

    /** Sinks a shard back into the ground over {@code ticks} and removes it. */
    static void sink(ServerLevel level, Display.BlockDisplay shard, float width, int ticks) {
        if (shard == null || shard.isRemoved()) {
            return;
        }
        CompoundTag tag = shard.saveWithoutId(new CompoundTag());
        Quaternionf lean = leftRotation(tag);
        reshape(shard, lean, width, 0.05f, width, ticks);
        TrophyScheduler.later(level, ticks + 1, shard::discard);
    }

    // ---------------------------------------------------------------- things that travel

    /**
     * A block that flies: {@code size} blocks wide and centred on {@code centre}, so turning the entity spins it about
     * its middle. Move it with {@link #fly}; the client smooths the steps over a tick.
     */
    static Display.BlockDisplay prop(ServerLevel level, BlockState block, Vec3 centre, float size) {
        return box(level, block, centre, size, size, size);
    }

    /**
     * A block stretched to {@code sx} by {@code sy} by {@code sz} and centred on {@code centre}: a beam, a plate, a
     * flame. Move it with {@link #fly} and stretch it again with {@link #resize}.
     */
    static Display.BlockDisplay box(ServerLevel level, BlockState block, Vec3 centre, float sx, float sy, float sz) {
        Display.BlockDisplay box = EntityType.BLOCK_DISPLAY.create(level);
        if (box == null) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Pos", doubles(centre.x, centre.y, centre.z));
        tag.put("block_state", NbtUtils.writeBlockState(block));
        tag.put("transformation", centred(sx, sy, sz));
        tag.putInt("teleport_duration", 1);
        box.load(tag);
        box.addTag(TAG);
        level.addFreshEntity(box);
        return box;
    }

    /** Stretches a box made by {@link #box} to a new size over {@code ticks}, keeping it centred. */
    static void resize(Display.BlockDisplay box, float sx, float sy, float sz, int ticks) {
        if (box == null || box.isRemoved()) {
            return;
        }
        CompoundTag tag = box.saveWithoutId(new CompoundTag());
        tag.put("transformation", centred(sx, sy, sz));
        tag.putInt("interpolation_duration", ticks);
        tag.putInt("start_interpolation", tag.getInt("start_interpolation") == 0 ? -1 : 0);
        box.load(tag);
    }

    /** Lights a display at full brightness whatever the light round it, so magma, fire and sunlight glow at night. */
    static <D extends Display> D glow(D display) {
        if (display == null || display.isRemoved()) {
            return display;
        }
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        CompoundTag light = new CompoundTag();
        light.putInt("block", 15);
        light.putInt("sky", 15);
        tag.put("brightness", light);
        display.load(tag);
        return display;
    }

    /** Removes a display if it is still there. */
    static void remove(Display display) {
        if (display != null && !display.isRemoved()) {
            display.discard();
        }
    }

    /**
     * A flat item (a sword) glowing at full brightness, {@code size} blocks across, its blade pointing along
     * {@code dir}.
     */
    static Display.ItemDisplay blade(ServerLevel level, ItemStack item, Vec3 centre, float size, Vec3 dir) {
        Display.ItemDisplay blade = EntityType.ITEM_DISPLAY.create(level);
        if (blade == null) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Pos", doubles(centre.x, centre.y, centre.z));
        CompoundTag stack = new CompoundTag();
        stack.putString("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
        stack.putInt("count", 1);
        tag.put("item", stack);
        tag.putString("item_display", "none");
        CompoundTag light = new CompoundTag();
        light.putInt("block", 15);
        light.putInt("sky", 15);
        tag.put("brightness", light);
        tag.put("transformation", bladeTransformation(dir, size));
        tag.putInt("teleport_duration", 1);
        tag.putInt("interpolation_duration", 0);
        blade.load(tag);
        blade.addTag(TAG);
        level.addFreshEntity(blade);
        return blade;
    }

    /** Swings a sword round to point along {@code dir} over {@code ticks}. */
    static void aim(Display.ItemDisplay blade, Vec3 dir, float size, int ticks) {
        if (blade == null || blade.isRemoved()) {
            return;
        }
        CompoundTag tag = blade.saveWithoutId(new CompoundTag());
        tag.put("transformation", bladeTransformation(dir, size));
        tag.putInt("interpolation_duration", ticks);
        tag.putInt("start_interpolation", tag.getInt("start_interpolation") == 0 ? -1 : 0);
        blade.load(tag);
    }

    /** Moves a travelling display to a new centre, turning it by the given angles (degrees). */
    static void fly(Display display, Vec3 centre, float yaw, float pitch) {
        if (display != null && !display.isRemoved()) {
            display.moveTo(centre.x, centre.y, centre.z, yaw, pitch);
        }
    }

    private static CompoundTag bladeTransformation(Vec3 dir, float size) {
        Quaternionf turn = bladeRotation(dir);
        CompoundTag t = new CompoundTag();
        t.put("translation", floats(0, 0, 0));
        t.put("left_rotation", floats(turn.x, turn.y, turn.z, turn.w));
        t.put("scale", floats(size, size, size));
        t.put("right_rotation", floats(0, 0, 0, 1));
        return t;
    }

    /**
     * The turn that points a sword sprite along {@code dir}. The sprite's blade runs up its local (1, 1, 0) diagonal and
     * its flat face looks along +z; the result keeps the face sideways to the flight, never along it.
     */
    static Quaternionf bladeRotation(Vec3 dir) {
        Vector3f along = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        if (along.lengthSquared() < 1e-6f) {
            along.set(0, 1, 0);
        }
        along.normalize();
        Vector3f e1 = new Vector3f(1, 1, 0).normalize();
        Vector3f e3 = new Vector3f(0, 0, 1);
        Vector3f e2 = new Vector3f(e3).cross(e1);
        Vector3f f3 = new Vector3f(along).cross(0, 1, 0);
        if (f3.lengthSquared() < 1e-4f) {
            f3.set(1, 0, 0);
        }
        f3.normalize();
        Vector3f f2 = new Vector3f(f3).cross(along);
        Matrix3f local = new Matrix3f(e1, e2, e3);
        Matrix3f world = new Matrix3f(along, f2, f3);
        return new Quaternionf().setFromNormalized(world.mul(local.transpose(), new Matrix3f()));
    }

    private static void reshape(Display.BlockDisplay shard, Quaternionf lean, float sx, float sy, float sz, int ticks) {
        if (shard.isRemoved()) {
            return;
        }
        CompoundTag tag = shard.saveWithoutId(new CompoundTag());
        tag.put("transformation", transformation(lean, sx, sy, sz));
        tag.putInt("interpolation_duration", ticks);
        // the client only restarts an interpolation when this value changes, so flip it every time
        tag.putInt("start_interpolation", tag.getInt("start_interpolation") == 0 ? -1 : 0);
        shard.load(tag);
    }

    private static CompoundTag centred(float sx, float sy, float sz) {
        CompoundTag t = new CompoundTag();
        t.put("translation", floats(-sx / 2, -sy / 2, -sz / 2));
        t.put("left_rotation", floats(0, 0, 0, 1));
        t.put("scale", floats(sx, sy, sz));
        t.put("right_rotation", floats(0, 0, 0, 1));
        return t;
    }

    /** The transformation that keeps the shard's foot centre on the entity's position whatever its lean. */
    private static CompoundTag transformation(Quaternionf lean, float sx, float sy, float sz) {
        Vector3f foot = lean.transform(new Vector3f(sx / 2, 0, sz / 2)).negate();
        CompoundTag t = new CompoundTag();
        t.put("translation", floats(foot.x, foot.y, foot.z));
        t.put("left_rotation", floats(lean.x, lean.y, lean.z, lean.w));
        t.put("scale", floats(sx, sy, sz));
        t.put("right_rotation", floats(0, 0, 0, 1));
        return t;
    }

    private static Quaternionf leftRotation(CompoundTag displayTag) {
        ListTag q = displayTag.getCompound("transformation").getList("left_rotation", 5);
        return q.size() == 4 ? new Quaternionf(q.getFloat(0), q.getFloat(1), q.getFloat(2), q.getFloat(3))
                : new Quaternionf();
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float v : values) {
            list.add(FloatTag.valueOf(v));
        }
        return list;
    }

    private static ListTag doubles(double... values) {
        ListTag list = new ListTag();
        for (double v : values) {
            list.add(net.minecraft.nbt.DoubleTag.valueOf(v));
        }
        return list;
    }
}
