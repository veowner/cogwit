package dev.cogwit.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the gamepad wheel aim write the pointer position directly, in case cursor events are swallowed. */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("xpos")
    void cogwit$setXpos(double x);

    @Accessor("ypos")
    void cogwit$setYpos(double y);
}
