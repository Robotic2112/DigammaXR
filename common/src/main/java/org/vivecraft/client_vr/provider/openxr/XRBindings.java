package org.vivecraft.client_vr.provider.openxr;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.LinkedHashSet;
import java.util.Set;

public final class XRBindings {
    private XRBindings() {
    }

    public static Set<String> supportedHeadsets(
        boolean metaTouchPlusProfileEnabled)
    {
        Set<String> profiles = new LinkedHashSet<>();
        // Keep the OpenXR 1.0 Oculus profile for VDXR compatibility and add
        // the native Quest Touch Plus profile whenever the runtime advertises
        // XR_META_touch_controller_plus.
        profiles.add("/interaction_profiles/oculus/touch_controller");
        if (metaTouchPlusProfileEnabled) {
            profiles.add("/interaction_profiles/meta/touch_plus_controller");
        }
        return profiles;
    }

    private static Set<Pair<String, String>> questTouchPlusBindings() {
        Set<Pair<String, String>> set = new LinkedHashSet<>();

        set.add(new MutablePair<>("/actions/global/in/vivecraft.key.ingameMenuButton", "/user/hand/left/input/menu/click"));

        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiShift", "/user/hand/left/input/squeeze/value"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiMiddleClick", "/user/hand/right/input/squeeze/value"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiLeftClick", "/user/hand/right/input/trigger/value"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiScrollAxis", "/user/hand/right/input/thumbstick/y"));

        set.add(new MutablePair<>("/actions/ingame/in/key.sneak", "/user/hand/left/input/squeeze/value"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.hotbarNext", "/user/hand/right/input/squeeze/value"));
        set.add(new MutablePair<>("/actions/ingame/in/key.attack", "/user/hand/right/input/trigger/value"));
        set.add(new MutablePair<>("/actions/ingame/in/key.use", "/user/hand/left/input/trigger/value"));
        set.add(new MutablePair<>("/actions/keyboard/in/vivecraft.key.keyboardShift", "/user/hand/left/input/squeeze/value"));
        set.add(new MutablePair<>("/actions/keyboard/in/vivecraft.key.keyboardShift", "/user/hand/right/input/squeeze/value"));
        set.add(new MutablePair<>("/actions/keyboard/in/vivecraft.key.keyboardClick", "/user/hand/left/input/trigger/value"));
        set.add(new MutablePair<>("/actions/keyboard/in/vivecraft.key.keyboardClick", "/user/hand/right/input/trigger/value"));

        return set;
    }

    private static Set<Pair<String, String>> viveBindings() {
        Set<Pair<String, String>> set = new LinkedHashSet<>();

        set.add(new MutablePair<>("/actions/global/in/vivecraft.key.ingameMenuButton", "/user/hand/left/input/menu/click"));
        set.add(new MutablePair<>("/actions/global/in/vivecraft.key.toggleKeyboard", "/user/hand/left/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/global/in/key.inventory", "/user/hand/right/input/trackpad/click"));

        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiShift", "/user/hand/left/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiMiddleClick", "/user/hand/right/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiLeftClick", "/user/hand/right/input/trigger/click"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiRightClick", "/user/hand/right/input/trackpad/click"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiScrollAxis", "/user/hand/right/input/trackpad/y"));

        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.hotbarPrev", "/user/hand/left/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.hotbarNext", "/user/hand/right/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/ingame/in/key.attack", "/user/hand/right/input/trigger/click"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.teleport", "/user/hand/left/input/trigger/click"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.radialMenu", "/user/hand/right/input/menu/click"));
        set.add(new MutablePair<>("/actions/ingame/in/key.use", "/user/hand/right/input/trackpad/click"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.teleportFallback", "/user/hand/left/input/trigger/value"));
        set.add(new MutablePair<>("/actions/ingame/in/key.jump", "/user/hand/left/input/trackpad/x"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.freeMoveStrafe", "/user/hand/left/input/trackpad"));
        set.add(new MutablePair<>("/actions/ingame/in/key.sneak", "/user/hand/left/input/trackpad/y"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.rotateAxis", "/user/hand/right/input/trackpad"));

        set.add(new MutablePair<>("/actions/keyboard/in/vivecraft.key.keyboardShift", "/user/hand/left/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/keyboard/in/vivecraft.key.keyboardClick", "/user/hand/left/input/trigger/click"));
        set.add(new MutablePair<>("/actions/keyboard/in/vivecraft.key.keyboardClick", "/user/hand/right/input/trigger/click"));

        set.add(new MutablePair<>("/actions/technical/in/vivecraft.key.trackpadTouch", "/user/hand/left/input/trackpad/click"));
        set.add(new MutablePair<>("/actions/technical/in/vivecraft.key.trackpadTouch", "/user/hand/right/input/trackpad/touch"));

        return set;
    }

    private static Set<Pair<String, String>> cosmosBindings() {
        Set<Pair<String, String>> set = new LinkedHashSet<>();

        set.add(new MutablePair<>("/actions/global/in/vivecraft.key.ingameMenuButton", "/user/hand/left/input/y/click"));
        set.add(new MutablePair<>("/actions/global/in/vivecraft.key.toggleKeyboard", "/user/hand/left/input/y/long"));
        set.add(new MutablePair<>("/actions/global/in/key.inventory", "/user/hand/left/input/x/click"));

        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiShift", "/user/hand/left/input/grip/click"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiMiddleClick", "/user/hand/right/input/grip/click"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiLeftClick", "/user/hand/right/input/trigger/click"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiRightClick", "/user/hand/right/input/a/click"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiScrollAxis", "/user/hand/right/input/joystick/scroll"));

        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.hotbarPrev", "/user/hand/left/input/grip/click"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.hotbarNext", "/user/hand/right/input/grip/click"));
        set.add(new MutablePair<>("/actions/ingame/in/key.attack", "/user/hand/right/input/trigger/click"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.teleport", "/user/hand/left/input/trigger/click"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.radialMenu", "/user/hand/right/input/b/click"));
        set.add(new MutablePair<>("/actions/ingame/in/key.use", "/user/hand/right/input/a/click"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.freeMoveStrafe", "/user/hand/left/input/joystick/position"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.rotateAxis", "/user/hand/right/input/joystick/position"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.teleportFallback", "/user/hand/left/input/trigger/pull"));
        set.add(new MutablePair<>("/actions/ingame/in/key.jump", "/user/hand/left/input/bumper/click"));
        set.add(new MutablePair<>("/actions/ingame/in/key.sneak", "/user/hand/right/input/bumper/click"));

        set.add(new MutablePair<>("/actions/keyboard/in/vivecraft.key.keyboardShift", "/user/hand/left/input/grip/click"));
        set.add(new MutablePair<>("/actions/keyboard/in/vivecraft.key.keyboardClick", "/user/hand/left/input/trigger/click"));
        set.add(new MutablePair<>("/actions/keyboard/in/vivecraft.key.keyboardClick", "/user/hand/right/input/trigger/click"));
        return set;
    }

    public static Set<Pair<String, String>> getBinding(
        String interactionProfile)
    {
        switch (interactionProfile) {
            case "/interaction_profiles/htc/vive_cosmos_controller" -> {
                return cosmosBindings();
            }
            case "/interaction_profiles/htc/vive_controller" -> {
                return viveBindings();
            }
            case "/interaction_profiles/oculus/touch_controller" -> {
                return questTouchPlusBindings();
            }
            case "/interaction_profiles/meta/touch_plus_controller" -> {
                return questTouchPlusBindings();
            }
            default -> {
                return viveBindings();
            }
        }
    }
}
