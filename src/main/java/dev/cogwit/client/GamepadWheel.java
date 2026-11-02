package dev.cogwit.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

/**
 * Lets a gamepad drive radial menus that pick by mouse position (Cogwit's ability wheel, Iron's spell
 * wheel): the right stick moves the cursor around the wheel's centre. Mouse users are untouched, since
 * nothing moves while the stick rests.
 *
 * <p>Controlify owns the pad through SDL, so GLFW often cannot see it; the pad is read through
 * Controlify's API by reflection (it is an optional dependency), with GLFW as the fallback.
 */
public final class GamepadWheel {
    private static final GLFWGamepadState STATE = GLFWGamepadState.create();
    private static final ResourceLocation RIGHT = axis("right_stick_right"), LEFT = axis("right_stick_left"),
            DOWN = axis("right_stick_down"), UP = axis("right_stick_up");

    private static boolean controlifyTried;
    private static Method apiGet, currentController, input, stateNow, axisState, buttons, buttonDown;

    private GamepadWheel() {}

    private static ResourceLocation axis(String name) {
        return ResourceLocation.fromNamespaceAndPath("controlify", "axis/" + name);
    }

    private static void bindControlify() {
        controlifyTried = true;
        try {
            Class<?> api = Class.forName("dev.isxander.controlify.api.ControlifyApi");
            Class<?> entity = Class.forName("dev.isxander.controlify.controller.ControllerEntity");
            Class<?> component = Class.forName("dev.isxander.controlify.controller.input.InputComponent");
            Class<?> view = Class.forName("dev.isxander.controlify.controller.input.ControllerStateView");
            apiGet = api.getMethod("get");
            currentController = api.getMethod("getCurrentController");
            input = entity.getMethod("input");
            stateNow = component.getMethod("stateNow");
            axisState = view.getMethod("getAxisState", ResourceLocation.class);
            buttons = view.getMethod("getButtons");
            buttonDown = view.getMethod("isButtonDown", ResourceLocation.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            apiGet = null;
        }
    }

    /** Controlify's current controller state, or null when Controlify is missing or no pad is active. */
    private static Object controlifyState() {
        if (!controlifyTried) {
            bindControlify();
        }
        if (apiGet == null) {
            return null;
        }
        try {
            Optional<?> controller = (Optional<?>) currentController.invoke(apiGet.invoke(null));
            if (controller.isEmpty()) {
                return null;
            }
            Optional<?> component = (Optional<?>) input.invoke(controller.get());
            return component.isEmpty() ? null : stateNow.invoke(component.get());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** The right stick as {x, y} (y down), or null when no pad can be read. */
    private static float[] rightStick() {
        Object state = controlifyState();
        if (state != null) {
            try {
                return new float[]{
                        (float) axisState.invoke(state, RIGHT) - (float) axisState.invoke(state, LEFT),
                        (float) axisState.invoke(state, DOWN) - (float) axisState.invoke(state, UP)};
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        return readGlfw() ? new float[]{STATE.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X), STATE.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y)} : null;
    }

    private static boolean readGlfw() {
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (GLFW.glfwJoystickIsGamepad(jid) && GLFW.glfwGetGamepadState(jid, STATE)) {
                return true;
            }
        }
        return false;
    }

    /** Whether one gamepad button is held, named as Controlify does ("east" is circle / B). */
    public static boolean buttonDown(String name, int glfwButton) {
        Object state = controlifyState();
        if (state != null) {
            try {
                return (boolean) buttonDown.invoke(state, ResourceLocation.fromNamespaceAndPath("controlify", "button/" + name));
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }
        return readGlfw() && STATE.buttons(glfwButton) == GLFW.GLFW_PRESS;
    }

    /** Whether any gamepad button is held, so a wheel opened from the pad stays open until it is released. */
    public static boolean anyButtonHeld() {
        Object state = controlifyState();
        if (state != null) {
            try {
                for (Object button : (Set<?>) buttons.invoke(state)) {
                    if ((boolean) buttonDown.invoke(state, button)) {
                        return true;
                    }
                }
                return false;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }
        if (!readGlfw()) {
            return false;
        }
        for (int b = 0; b <= GLFW.GLFW_GAMEPAD_BUTTON_LAST; b++) {
            if (STATE.buttons(b) == GLFW.GLFW_PRESS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Points the cursor from a centre in GUI pixels along the right stick, between {@code near} and
     * {@code far} by how hard the stick is pushed. Returns false when the stick rests or there is no pad.
     */
    public static boolean steer(double centreX, double centreY, double near, double far) {
        float[] stick = rightStick();
        double[] aim = stick == null ? null : GamepadAim.aim(stick[0], stick[1]);
        if (aim == null) {
            return false;
        }
        double r = near + (far - near) * aim[2];
        double gx = centreX + aim[0] * r, gy = centreY + aim[1] * r;
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        // glfwSetCursorPos takes window points, which differ from framebuffer pixels on a Retina screen
        int[] w = new int[1], h = new int[1];
        GLFW.glfwGetWindowSize(window.getWindow(), w, h);
        GLFW.glfwSetCursorPos(window.getWindow(), gx * w[0] / window.getGuiScaledWidth(), gy * h[0] / window.getGuiScaledHeight());
        // and write MouseHandler directly, in the units it keeps (screen pixels), in case the cursor event is swallowed
        var mouse = (dev.cogwit.mixin.MouseHandlerAccessor) mc.mouseHandler;
        mouse.cogwit$setXpos(gx * window.getScreenWidth() / window.getGuiScaledWidth());
        mouse.cogwit$setYpos(gy * window.getScreenHeight() / window.getGuiScaledHeight());
        return true;
    }
}
