package org.vivecraft.client_vr.provider.openxr;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.RadialHandler;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.provider.InputSimulator;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.provider.VRRenderer;
import org.vivecraft.client_vr.provider.control.VRInputAction;
import org.vivecraft.client_vr.provider.control.VRInputActionSet;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.settings.VRSettings;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.*;

import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class MCOpenXR extends MCVR {

    private static final int APPLICATION_VERSION = 10;
    private static MCOpenXR OME;
    public XrInstance instance;
    public XrSession session;
    public XrSpace xrAppSpace;
    public XrSpace xrViewSpace;
    public XrSwapchain swapchain;
    public final XrEventDataBuffer eventDataBuffer = XrEventDataBuffer.calloc();
    public long time;
    private boolean tried;
    private long systemID;
    public XrView.Buffer viewBuffer;
    public int width;
    public int height;
    private final Map<VRInputActionSet, Long> actionSetHandles =
        new EnumMap<>(VRInputActionSet.class);
    private final VRInputActionSet[] activeActionSetScratch =
        new VRInputActionSet[VRInputActionSet.values().length];
    private XrActiveActionSet.Buffer activeActionSetsBuffer;
    private boolean isActive;
    private boolean sessionRunning;
    private int sessionState = XR10.XR_SESSION_STATE_UNKNOWN;
    private boolean metaTouchPlusProfileEnabled;
    private final HashMap<String, Long> paths = new HashMap<>();
    private final Map<Long, List<Long>> boundSourcesCache = new HashMap<>();
    private final Map<Long, ControllerType> originControllerCache = new HashMap<>();
    private final long[] grip = new long[2];
    private final long[] aim = new long[2];
    private final XrSpace[] gripSpace = new XrSpace[2];
    private final XrSpace[] aimSpace = new XrSpace[2];
    public boolean shouldRender = true;
    public long[] haptics = new long[2];
    public String systemName;
    public String runtimeName;
    public boolean frameBegun;
    private final boolean[] controllerTrackingStateKnown = new boolean[2];
    private long leftLocomotionAction;
    private long rightTurnAction;
    private long rightJumpAction;
    private long menuAction;
    private long inventoryAction;
    private final Vector2f leftLocomotionAxis = new Vector2f();
    private final Vector2f rightTurnAxis = new Vector2f();
    private boolean leftLocomotionActionActive;
    private boolean rightTurnActionActive;
    private boolean rightJumpPressed;
    private boolean menuWasPressed;
    private boolean inventoryWasPressed;
    private boolean leftLocomotionLogged;
    private boolean rightTurnLogged;
    private boolean rightJumpLogged;
    private boolean rightTurnLatched;
    private boolean destroyed;


    public MCOpenXR(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh, VivecraftVRMod.INSTANCE);
        OME = this;
        this.hapticScheduler = new OpenXRHapticSchedular();
        // ForkXR uses tracked controllers even while the player is physically
        // seated. Vivecraft's "Seated" play mode disables motion-controller
        // input, so keep its controller-capable Standing mode and make smooth
        // left-stick locomotion deterministic for both new and old profiles.
        boolean settingsChanged = false;
        if (dh.vrSettings.seated) {
            dh.vrSettings.seated = false;
            settingsChanged = true;
        }
        if (!dh.vrSettings.forceStandingFreeMove) {
            dh.vrSettings.forceStandingFreeMove = true;
            settingsChanged = true;
        }
        if (!dh.vrSettings.walkUpBlocks) {
            dh.vrSettings.walkUpBlocks = true;
            settingsChanged = true;
        }
        if (!dh.vrSettings.simulateFalling) {
            dh.vrSettings.simulateFalling = true;
            settingsChanged = true;
        }
        if (dh.vrSettings.realisticJumpEnabled !=
            VRSettings.RealisticJump.OFF)
        {
            dh.vrSettings.realisticJumpEnabled =
                VRSettings.RealisticJump.OFF;
            settingsChanged = true;
        }
        if (dh.vrSettings.realisticSneakEnabled) {
            dh.vrSettings.realisticSneakEnabled = false;
            settingsChanged = true;
        }
        if (dh.vrSettings.vrFreeMoveMode != VRSettings.FreeMove.HMD) {
            dh.vrSettings.vrFreeMoveMode = VRSettings.FreeMove.HMD;
            settingsChanged = true;
        }
        // Render at 120% in each dimension and downsample into the fixed
        // 1832x1920 Quest swapchain. This preserves the runtime contract while
        // materially improving edge and text clarity.
        if (Math.abs(dh.vrSettings.renderScaleFactor - 1.44F) > 0.0001F) {
            dh.vrSettings.renderScaleFactor = 1.44F;
            settingsChanged = true;
        }
        if (!dh.vrSettings.useFsaa) {
            dh.vrSettings.useFsaa = true;
            settingsChanged = true;
        }
        // OpenXRStereoRenderer does not expose a hidden-area stencil mask.
        // Avoid allocating an unused stencil attachment for the supersampled
        // render target.
        if (dh.vrSettings.vrUseStencil) {
            dh.vrSettings.vrUseStencil = false;
            settingsChanged = true;
        }
        if (settingsChanged) {
            dh.vrSettings.saveOptions();
        }
    }

    @Override
    public String getName() {
        return "OpenXR";
    }

    @Override
    public void destroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;

        if (this.frameBegun && this.session != null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                int error = XR10.xrEndFrame(
                    this.session,
                    XrFrameEndInfo.calloc(stack)
                        .type$Default()
                        .displayTime(this.time)
                        .environmentBlendMode(
                            XR10.XR_ENVIRONMENT_BLEND_MODE_OPAQUE));
                this.logError(error, "xrEndFrame", "shutdown");
            }
            this.frameBegun = false;
        }

        this.destroyActionSpaces(this.gripSpace, "grip");
        this.destroyActionSpaces(this.aimSpace, "aim");
        this.destroySpace(this.xrAppSpace, "xrAppSpace");
        this.destroySpace(this.xrViewSpace, "xrViewSpace");

        if (this.swapchain != null) {
            int error = XR10.xrDestroySwapchain(this.swapchain);
            this.logError(error, "xrDestroySwapchain", "");
            this.swapchain = null;
        }
        if (this.session != null) {
            int error = XR10.xrDestroySession(this.session);
            this.logError(error, "xrDestroySession", "");
            this.session = null;
            this.sessionRunning = false;
        }

        if (this.instance != null) {
            for (long actionSetHandle : this.actionSetHandles.values()) {
                int error = XR10.xrDestroyActionSet(
                    new XrActionSet(actionSetHandle, this.instance));
                this.logError(error, "xrDestroyActionSet", "");
            }
        }
        this.actionSetHandles.clear();

        if (this.activeActionSetsBuffer != null) {
            this.activeActionSetsBuffer.close();
            this.activeActionSetsBuffer = null;
        }
        if (this.viewBuffer != null) {
            this.viewBuffer.close();
            this.viewBuffer = null;
        }

        this.device.closeGraphics();
        if (this.instance != null) {
            int error = XR10.xrDestroyInstance(this.instance);
            this.logError(error, "xrDestroyInstance", "");
            this.instance = null;
        }
        this.eventDataBuffer.close();
        this.isActive = false;
        this.initialized = false;
    }

    private void destroyActionSpaces(XrSpace[] spaces, String label) {
        for (int index = 0; index < spaces.length; index++) {
            this.destroySpace(spaces[index], label + '[' + index + ']');
            spaces[index] = null;
        }
    }

    private void destroySpace(XrSpace space, String label) {
        if (space == null) {
            return;
        }
        int error = XR10.xrDestroySpace(space);
        this.logError(error, "xrDestroySpace", label);
    }

    @Override
    protected ControllerType findActiveBindingControllerType(KeyMapping binding) {
        if (!this.inputInitialized) {
            return null;
        }
        return this.getOriginControllerType(
            this.getInputAction(binding).getLastOrigin());
    }

    @Override
    public void poll(long var1) {
        if (this.initialized && this.sessionRunning) {
            this.mc.getProfiler().push("events");
            this.pollVREvents();
            if (!this.sessionRunning) {
                this.mc.getProfiler().pop();
                return;
            }

            if (!this.dh.vrSettings.seated) {
                this.mc.getProfiler().popPush("controllers");
                this.mc.getProfiler().push("gui");

                if (this.mc.screen == null && this.dh.vrSettings.vrTouchHotbar) {

                    if (this.dh.vrSettings.vrHudLockMode != VRSettings.HUDLock.HEAD && this.hudPopup) {
                        this.processHotbar();
                    }
                }

                this.mc.getProfiler().pop();
            }
            this.mc.getProfiler().popPush("updatePose/Vsync");
            this.updatePose();
            this.mc.getProfiler().popPush("processInputs");
            this.processInputs();
            this.processDedicatedRightTurn();
            this.mc.getProfiler().popPush("hmdSampling");
            this.hmdSampling();
            this.mc.getProfiler().pop();
        }
    }

    private void updatePose() {
        if (mc == null || !this.sessionRunning) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (this.frameBegun) {
                // A Minecraft tick can occasionally occur without the matching
                // render callback. OpenXR still requires that begun frame to
                // be ended before the next xrWaitFrame.
                XrFrameEndInfo abandonedFrame = XrFrameEndInfo.calloc(stack)
                    .type$Default()
                    .displayTime(this.time)
                    .environmentBlendMode(
                        XR10.XR_ENVIRONMENT_BLEND_MODE_OPAQUE);
                int endError =
                    XR10.xrEndFrame(this.session, abandonedFrame);
                logError(endError, "xrEndFrame", "recover abandoned frame");
                this.frameBegun = false;
            }

            XrFrameState frameState = XrFrameState.calloc(stack).type(XR10.XR_TYPE_FRAME_STATE);

            int error = XR10.xrWaitFrame(
                session,
                XrFrameWaitInfo.calloc(stack).type(XR10.XR_TYPE_FRAME_WAIT_INFO),
                frameState);
            logError(error, "xrWaitFrame", "");
            if (error < 0) {
                return;
            }

            time = frameState.predictedDisplayTime();
            this.shouldRender = frameState.shouldRender();

            error = XR10.xrBeginFrame(
                session,
                XrFrameBeginInfo.calloc(stack).type(XR10.XR_TYPE_FRAME_BEGIN_INFO));
            logError(error, "xrBeginFrame", "");
            if (error < 0) {
                return;
            }
            this.frameBegun = true;


            XrViewState viewState = XrViewState.calloc(stack).type(XR10.XR_TYPE_VIEW_STATE);
            IntBuffer intBuf = stack.callocInt(1);

            XrViewLocateInfo viewLocateInfo = XrViewLocateInfo.calloc(stack);
            viewLocateInfo.set(XR10.XR_TYPE_VIEW_LOCATE_INFO,
                0,
                XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO,
                frameState.predictedDisplayTime(),
                xrAppSpace
            );

            error = XR10.xrLocateViews(session, viewLocateInfo, viewState, intBuf, viewBuffer);
            logError(error, "xrLocateViews", "");

            XrSpaceLocation space_location = XrSpaceLocation.calloc(stack).type(XR10.XR_TYPE_SPACE_LOCATION);

            //HMD pose
            error = XR10.xrLocateSpace(xrViewSpace, xrAppSpace, time, space_location);
            logError(error, "xrLocateSpace", "xrViewSpace");
            if (error >= 0) {
                OpenXRUtil.openXRPoseToMarix(space_location.pose(), this.hmdPose);
                headIsTracking = true;
            } else {
                headIsTracking = false;
                this.hmdPose.identity();
                this.hmdPose.m31(1.6F);
            }

            //Eye positions
            OpenXRUtil.openXRPoseToMarix(viewBuffer.get(0).pose(), this.hmdPoseLeftEye);
            OpenXRUtil.openXRPoseToMarix(viewBuffer.get(1).pose(), this.hmdPoseRightEye);

            if (this.inputInitialized) {
                this.mc.getProfiler().push("updateActionState");

                if (this.updateActiveActionSets()) {
                    XrActionsSyncInfo syncInfo = XrActionsSyncInfo.calloc(stack)
                        .type(XR10.XR_TYPE_ACTIONS_SYNC_INFO)
                        .activeActionSets(activeActionSetsBuffer);
                    error = XR10.xrSyncActions(session, syncInfo);
                    logError(error, "xrSyncActions", "");
                }

                this.readLocomotion();
                this.readRightJump();
                this.readMenu();
                this.readInventory();
                this.inputActions.values().forEach(this::readNewData);

                XrActionSet actionSet = new XrActionSet(this.actionSetHandles.get(VRInputActionSet.GLOBAL), instance);
                this.readPoseData(
                    this.grip[RIGHT_CONTROLLER], actionSet, ControllerType.RIGHT);
                this.readPoseData(
                    this.grip[LEFT_CONTROLLER], actionSet, ControllerType.LEFT);
                this.readPoseData(
                    this.aim[RIGHT_CONTROLLER], actionSet, ControllerType.RIGHT);
                this.readPoseData(
                    this.aim[LEFT_CONTROLLER], actionSet, ControllerType.LEFT);

                this.mc.getProfiler().pop();

                //reverse
                if (this.dh.vrSettings.reverseHands) {
                    XrSpace temp = gripSpace[RIGHT_CONTROLLER];
                    gripSpace[RIGHT_CONTROLLER] = gripSpace[LEFT_CONTROLLER];
                    gripSpace[LEFT_CONTROLLER] = temp;
                    temp = aimSpace[RIGHT_CONTROLLER];
                    aimSpace[RIGHT_CONTROLLER] = aimSpace[LEFT_CONTROLLER];
                    aimSpace[LEFT_CONTROLLER] = temp;
                }

                //Controller aim and grip poses
                error = XR10.xrLocateSpace(gripSpace[RIGHT_CONTROLLER], xrAppSpace, time, space_location);
                logError(error, "xrLocateSpace", "gripSpace[0]");
                if (error >= 0 && hasOrientation(space_location)) {
                    OpenXRUtil.openXRPoseToMarix(space_location.pose().orientation(), this.handRotation[RIGHT_CONTROLLER]);
                }

                error = XR10.xrLocateSpace(gripSpace[LEFT_CONTROLLER], xrAppSpace, time, space_location);
                logError(error, "xrLocateSpace", "gripSpace[1]");
                if (error >= 0 && hasOrientation(space_location)) {
                    OpenXRUtil.openXRPoseToMarix(space_location.pose().orientation(), this.handRotation[LEFT_CONTROLLER]);
                }

                error = XR10.xrLocateSpace(aimSpace[RIGHT_CONTROLLER], xrAppSpace, time, space_location);
                logError(error, "xrLocateSpace", "aimSpace[0]");
                if (error >= 0 && hasTrackedPose(space_location)) {
                    OpenXRUtil.openXRPoseToMarix(space_location.pose(), this.controllerPose[RIGHT_CONTROLLER]);
                    OpenXRUtil.openXRPoseToMarix(space_location.pose().orientation(), this.controllerRotation[RIGHT_CONTROLLER]);
                    this.setControllerTracking(RIGHT_CONTROLLER, true);
                } else {
                    this.setControllerTracking(RIGHT_CONTROLLER, false);
                }

                error = XR10.xrLocateSpace(aimSpace[LEFT_CONTROLLER], xrAppSpace, time, space_location);
                logError(error, "xrLocateSpace", "aimSpace[1]");
                if (error >= 0 && hasTrackedPose(space_location)) {
                    OpenXRUtil.openXRPoseToMarix(space_location.pose(), this.controllerPose[LEFT_CONTROLLER]);
                    OpenXRUtil.openXRPoseToMarix(space_location.pose().orientation(), this.controllerRotation[LEFT_CONTROLLER]);
                    this.setControllerTracking(LEFT_CONTROLLER, true);
                } else {
                    this.setControllerTracking(LEFT_CONTROLLER, false);
                }
            }

            this.updateAim();
        }
    }

    public void readNewData(VRInputAction action) {
        switch (action.type) {
            case "boolean" -> {
                if (action.isHanded()) {
                    for (ControllerType controllertype1 : ControllerType.values()) {
                        this.readBoolean(action, controllertype1);
                    }
                } else {
                    this.readBoolean(action, null);
                }
            }

            case "vector1" -> {
                if (action.isHanded()) {
                    for (ControllerType controllertype : ControllerType.values()) {
                        this.readFloat(action, controllertype);
                    }
                } else {
                    this.readFloat(action, null);
                }
            }

            case "vector2" -> {
                if (action.isHanded()) {
                    for (ControllerType controllertype : ControllerType.values()) {
                        this.readVecData(action, controllertype);
                    }
                } else {
                    this.readVecData(action, null);
                }
            }

        }
    }

    private long subactionPath(@Nullable ControllerType hand) {
        if (hand == ControllerType.RIGHT) {
            return this.getPath("/user/hand/right");
        }
        if (hand == ControllerType.LEFT) {
            return this.getPath("/user/hand/left");
        }
        return XR10.XR_NULL_PATH;
    }

    private long activeOrigin(
        VRInputAction action,
        @Nullable ControllerType hand,
        boolean active)
    {
        if (!active) {
            return XR10.XR_NULL_PATH;
        }
        List<Long> origins = this.getOrigins(action);
        if (hand != null) {
            for (long origin : origins) {
                if (this.getOriginControllerType(origin) == hand) {
                    return origin;
                }
            }
            // Some runtimes expose only the user path for an active
            // subaction. Keeping that hand identity is more useful than
            // incorrectly assigning both actions to the first bound source.
            return this.subactionPath(hand);
        }
        return origins.isEmpty() ? XR10.XR_NULL_PATH : origins.get(0);
    }

    private static boolean hasOrientation(XrSpaceLocation location) {
        return (location.locationFlags() &
            XR10.XR_SPACE_LOCATION_ORIENTATION_VALID_BIT) != 0;
    }

    private static boolean hasTrackedPose(XrSpaceLocation location) {
        int required = XR10.XR_SPACE_LOCATION_ORIENTATION_VALID_BIT |
            XR10.XR_SPACE_LOCATION_POSITION_VALID_BIT;
        return (location.locationFlags() & required) == required;
    }

    private void setControllerTracking(int controller, boolean tracking) {
        if (!this.controllerTrackingStateKnown[controller] ||
            this.controllerTracking[controller] != tracking)
        {
            this.controllerTrackingStateKnown[controller] = true;
            String hand = controller == RIGHT_CONTROLLER ? "right" : "left";
            if (tracking) {
                VRSettings.LOGGER.info(
                    "ForkXR: VDXR {} Touch Plus tracking active", hand);
            } else {
                VRSettings.LOGGER.warn(
                    "ForkXR: VDXR is not reporting a valid {} Touch Plus pose",
                    hand);
            }
        }
        this.controllerTracking[controller] = tracking;
    }

    private void readBoolean(VRInputAction action, ControllerType hand) {
        int i = 0;

        if (hand != null) {
            i = hand.ordinal();
        }
        try (MemoryStack stack = MemoryStack.stackPush()){
            XrActionStateGetInfo info = XrActionStateGetInfo.calloc(stack);
            info.type(XR10.XR_TYPE_ACTION_STATE_GET_INFO);
            info.action(new XrAction(action.handle, new XrActionSet(actionSetHandles.get(action.actionSet), instance)));
            info.subactionPath(this.subactionPath(hand));
            XrActionStateBoolean state = XrActionStateBoolean.calloc(stack).type(XR10.XR_TYPE_ACTION_STATE_BOOLEAN);
            int error = XR10.xrGetActionStateBoolean(session, info, state);
            logError(error, "xrGetActionStateBoolean",  action.name);

            action.digitalData[i].state = state.currentState();
            action.digitalData[i].isActive = state.isActive();
            action.digitalData[i].isChanged = state.changedSinceLastSync();
            action.digitalData[i].activeOrigin =
                this.activeOrigin(action, hand, state.isActive());
        }
    }

    private void readFloat(VRInputAction action, ControllerType hand) {
        int i = 0;

        if (hand != null) {
            i = hand.ordinal();
        }
        try (MemoryStack stack = MemoryStack.stackPush()){
            XrActionStateGetInfo info = XrActionStateGetInfo.calloc(stack);
            info.type(XR10.XR_TYPE_ACTION_STATE_GET_INFO);
            info.action(new XrAction(action.handle, new XrActionSet(actionSetHandles.get(action.actionSet), instance)));
            info.subactionPath(this.subactionPath(hand));
            XrActionStateFloat state = XrActionStateFloat.calloc(stack).type(XR10.XR_TYPE_ACTION_STATE_FLOAT);
            int error = XR10.xrGetActionStateFloat(session, info, state);
            logError(error, "xrGetActionStateFloat",  action.name);

            action.analogData[i].deltaX = action.analogData[i].x - state.currentState();
            action.analogData[i].x = state.currentState();
            action.analogData[i].activeOrigin =
                this.activeOrigin(action, hand, state.isActive());
            action.analogData[i].isActive = state.isActive();
            action.analogData[i].isChanged = state.changedSinceLastSync();
        }
    }

    private void readVecData(VRInputAction action, ControllerType hand) {
        int i = 0;

        if (hand != null && action.isHanded()) {
            i = hand.ordinal();
        }
        try (MemoryStack stack = MemoryStack.stackPush()){
            XrActionStateGetInfo info = XrActionStateGetInfo.calloc(stack);
            info.type(XR10.XR_TYPE_ACTION_STATE_GET_INFO);
            info.action(new XrAction(action.handle, new XrActionSet(actionSetHandles.get(action.actionSet), instance)));
            info.subactionPath(this.subactionPath(hand));
            XrActionStateVector2f state = XrActionStateVector2f.calloc(stack).type(XR10.XR_TYPE_ACTION_STATE_VECTOR2F);
            int error = XR10.xrGetActionStateVector2f(session, info, state);
            logError(error, "xrGetActionStateVector2f",  action.name);

            action.analogData[i].deltaX = action.analogData[i].x - state.currentState().x();
            action.analogData[i].deltaY = action.analogData[i].y - state.currentState().y();
            action.analogData[i].x = state.currentState().x();
            action.analogData[i].y = state.currentState().y();
            action.analogData[i].activeOrigin =
                this.activeOrigin(action, hand, state.isActive());
            action.analogData[i].isActive = state.isActive();
            action.analogData[i].isChanged = state.changedSinceLastSync();
        }
    }

    private boolean readJoystickAction(
        long action,
        ControllerType hand,
        Vector2f axis,
        String purpose,
        boolean alreadyLogged)
    {
        if (action == XR10.XR_NULL_HANDLE) {
            axis.zero();
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrActionSet actionSet = new XrActionSet(
                this.actionSetHandles.get(VRInputActionSet.INGAME),
                this.instance);
            XrActionStateGetInfo info = XrActionStateGetInfo.calloc(stack)
                .type(XR10.XR_TYPE_ACTION_STATE_GET_INFO)
                .action(new XrAction(action, actionSet))
                // Match MCXR's proven input model: the physical hand is
                // selected by the suggested binding path, while the action
                // itself is queried without a subaction path. VDXR may expose
                // controller profiles asynchronously and otherwise leaves a
                // handed action permanently inactive.
                .subactionPath(XR10.XR_NULL_PATH);
            XrActionStateVector2f state = XrActionStateVector2f.calloc(stack)
                .type(XR10.XR_TYPE_ACTION_STATE_VECTOR2F);
            int error = XR10.xrGetActionStateVector2f(this.session, info, state);
            this.logError(
                error,
                "xrGetActionStateVector2f",
                "ForkXR " + hand.name().toLowerCase(Locale.ROOT) +
                    " Touch Plus " + purpose);
            if (error < 0 || !state.isActive()) {
                axis.zero();
                return false;
            }

            float x = state.currentState().x();
            float y = state.currentState().y();
            float magnitude = (float) Math.sqrt(x * x + y * y);
            if (magnitude <= 0.15F) {
                axis.zero();
            } else {
                float scaled = Math.min(1.0F, (magnitude - 0.15F) / 0.85F);
                axis.set(
                    x / magnitude * scaled,
                    y / magnitude * scaled);
            }
            if (!alreadyLogged && axis.lengthSquared() > 0.0025F)
            {
                VRSettings.LOGGER.info(
                    "ForkXR: dedicated {} Touch Plus joystick {} active",
                    hand.name().toLowerCase(Locale.ROOT),
                    purpose);
            }
            return true;
        }
    }

    private void readLocomotion() {
        boolean leftWasLogged = this.leftLocomotionLogged;
        this.leftLocomotionActionActive = this.readJoystickAction(
            this.leftLocomotionAction,
            ControllerType.LEFT,
            this.leftLocomotionAxis,
            "locomotion",
            leftWasLogged);
        if (!leftWasLogged && this.leftLocomotionAxis.lengthSquared() > 0.0025F) {
            this.leftLocomotionLogged = true;
        }

        boolean rightWasLogged = this.rightTurnLogged;
        this.rightTurnActionActive = this.readJoystickAction(
            this.rightTurnAction,
            ControllerType.RIGHT,
            this.rightTurnAxis,
            "turning",
            rightWasLogged);
        if (!rightWasLogged && this.rightTurnAxis.lengthSquared() > 0.0025F) {
            this.rightTurnLogged = true;
        }
    }

    public Vector2fc getLeftLocomotionAxis() {
        return this.leftLocomotionAxis;
    }

    private void processDedicatedRightTurn() {
        if (this.mc.screen != null || !this.rightTurnActionActive) {
            this.rightTurnLatched = false;
            return;
        }

        float turn = this.rightTurnAxis.x();
        if (Math.abs(turn) < 0.35F) {
            this.rightTurnLatched = false;
            return;
        }
        if (Math.abs(turn) < 0.65F || this.rightTurnLatched) {
            return;
        }

        this.rightTurnLatched = true;
        this.dh.vrSettings.worldRotation -= 30.0F * Math.signum(turn);
        this.dh.vrSettings.worldRotation %= 360.0F;
        this.seatedRot = this.dh.vrSettings.worldRotation;
    }

    private void readRightJump() {
        if (this.rightJumpAction == XR10.XR_NULL_HANDLE) {
            this.rightJumpPressed = false;
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrActionSet actionSet = new XrActionSet(
                this.actionSetHandles.get(VRInputActionSet.INGAME),
                this.instance);
            XrActionStateGetInfo info = XrActionStateGetInfo.calloc(stack)
                .type(XR10.XR_TYPE_ACTION_STATE_GET_INFO)
                .action(new XrAction(this.rightJumpAction, actionSet))
                .subactionPath(XR10.XR_NULL_PATH);
            XrActionStateBoolean state = XrActionStateBoolean.calloc(stack)
                .type(XR10.XR_TYPE_ACTION_STATE_BOOLEAN);
            int error = XR10.xrGetActionStateBoolean(
                this.session,
                info,
                state);
            this.logError(
                error,
                "xrGetActionStateBoolean",
                "ForkXR right Touch Plus jump");
            this.rightJumpPressed =
                error >= 0 && state.isActive() && state.currentState();
            if (this.rightJumpPressed && !this.rightJumpLogged) {
                this.rightJumpLogged = true;
                VRSettings.LOGGER.info(
                    "ForkXR: right Touch Plus A fallback-jump action active");
            }
        }
    }

    public boolean isRightJumpPressed() {
        return this.rightJumpPressed;
    }

    private void readMenu() {
        if (this.menuAction == XR10.XR_NULL_HANDLE) {
            this.menuWasPressed = false;
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrActionSet actionSet = new XrActionSet(
                this.actionSetHandles.get(VRInputActionSet.GLOBAL),
                this.instance);
            XrActionStateGetInfo info = XrActionStateGetInfo.calloc(stack)
                .type(XR10.XR_TYPE_ACTION_STATE_GET_INFO)
                .action(new XrAction(this.menuAction, actionSet))
                .subactionPath(XR10.XR_NULL_PATH);
            XrActionStateBoolean state = XrActionStateBoolean.calloc(stack)
                .type(XR10.XR_TYPE_ACTION_STATE_BOOLEAN);
            int error = XR10.xrGetActionStateBoolean(
                this.session,
                info,
                state);
            this.logError(
                error,
                "xrGetActionStateBoolean",
                "ForkXR left Touch Plus game menu");
            boolean pressed =
                error >= 0 && state.isActive() && state.currentState();
            if (pressed && !this.menuWasPressed) {
                if (this.mc.screen == null) {
                    this.mc.pauseGame(false);
                } else {
                    InputSimulator.pressKey(GLFW.GLFW_KEY_ESCAPE);
                    InputSimulator.releaseKey(GLFW.GLFW_KEY_ESCAPE);
                }
                VRSettings.LOGGER.info(
                    "ForkXR: left Touch Plus X game-menu action active");
            }
            this.menuWasPressed = pressed;
        }
    }

    private void readInventory() {
        if (this.inventoryAction == XR10.XR_NULL_HANDLE) {
            this.inventoryWasPressed = false;
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrActionSet actionSet = new XrActionSet(
                this.actionSetHandles.get(VRInputActionSet.GLOBAL),
                this.instance);
            XrActionStateGetInfo info = XrActionStateGetInfo.calloc(stack)
                .type(XR10.XR_TYPE_ACTION_STATE_GET_INFO)
                .action(new XrAction(this.inventoryAction, actionSet))
                .subactionPath(XR10.XR_NULL_PATH);
            XrActionStateBoolean state = XrActionStateBoolean.calloc(stack)
                .type(XR10.XR_TYPE_ACTION_STATE_BOOLEAN);
            int error = XR10.xrGetActionStateBoolean(
                this.session,
                info,
                state);
            this.logError(
                error,
                "xrGetActionStateBoolean",
                "ForkXR Touch Plus inventory");
            boolean pressed =
                error >= 0 && state.isActive() && state.currentState();
            if (pressed && !this.inventoryWasPressed) {
                InputSimulator.pressKey(GLFW.GLFW_KEY_E);
                InputSimulator.releaseKey(GLFW.GLFW_KEY_E);
                VRSettings.LOGGER.info(
                    "ForkXR: Touch Plus left Y inventory action active");
            }
            this.inventoryWasPressed = pressed;
        }
    }

    private void readPoseData(
        Long action, XrActionSet set, ControllerType hand)
    {
        try (MemoryStack stack = MemoryStack.stackPush()){
            XrActionStateGetInfo info = XrActionStateGetInfo.calloc(stack);
            info.type(XR10.XR_TYPE_ACTION_STATE_GET_INFO);
            info.action(new XrAction(action, set));
            info.subactionPath(this.subactionPath(hand));
            XrActionStatePose state = XrActionStatePose.calloc(stack).type(XR10.XR_TYPE_ACTION_STATE_POSE);
            int error = XR10.xrGetActionStatePose(session, info, state);
            logError(error, "xrGetActionStatePose",  "");
        }
    }

    private boolean updateActiveActionSets() {
        int count = 0;
        this.activeActionSetScratch[count++] = VRInputActionSet.GLOBAL;
        this.activeActionSetScratch[count++] = VRInputActionSet.MOD;
        this.activeActionSetScratch[count++] = VRInputActionSet.MIXED_REALITY;
        this.activeActionSetScratch[count++] = VRInputActionSet.TECHNICAL;

        if (this.mc.screen == null) {
            this.activeActionSetScratch[count++] = VRInputActionSet.INGAME;
            this.activeActionSetScratch[count++] = VRInputActionSet.CONTEXTUAL;
        } else {
            this.activeActionSetScratch[count++] = VRInputActionSet.GUI;
            if (ClientDataHolderVR.getInstance().vrSettings.ingameBindingsInGui) {
                this.activeActionSetScratch[count++] = VRInputActionSet.INGAME;
            }
        }

        if (KeyboardHandler.SHOWING || RadialHandler.isShowing()) {
            this.activeActionSetScratch[count++] = VRInputActionSet.KEYBOARD;
        }

        if (this.activeActionSetsBuffer == null) {
            this.activeActionSetsBuffer = XrActiveActionSet.calloc(count);
        } else if (this.activeActionSetsBuffer.capacity() != count) {
            this.activeActionSetsBuffer.close();
            this.activeActionSetsBuffer = XrActiveActionSet.calloc(count);
        }

        for (int index = 0; index < count; index++) {
            VRInputActionSet actionSet = this.activeActionSetScratch[index];
            this.activeActionSetsBuffer.get(index).set(
                new XrActionSet(
                    this.getActionSetHandle(actionSet), this.instance),
                XR10.XR_NULL_PATH);
        }

        return count > 0;
    }

    long getActionSetHandle(VRInputActionSet actionSet) {
        return this.actionSetHandles.get(actionSet);
    }

    private void pollVREvents() {
        while (true) {
            eventDataBuffer.clear();
            eventDataBuffer.type(XR10.XR_TYPE_EVENT_DATA_BUFFER);
            int error = XR10.xrPollEvent(instance, eventDataBuffer);
            logError(error, "xrPollEvent",  "");
            if (error != XR10.XR_SUCCESS) {
                break;
            }
            XrEventDataBaseHeader event = XrEventDataBaseHeader.create(eventDataBuffer.address());

            switch (event.type()) {
                case XR10.XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING -> {
                    this.isActive = false;
                    this.sessionRunning = false;
                    VRSettings.LOGGER.error(
                        "ForkXR: the OpenXR runtime reported instance loss");
                }
                case XR10.XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED -> {
                    this.sessionChanged(XrEventDataSessionStateChanged.create(event.address()));
                }
                case XR10.XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED -> {
                    this.boundSourcesCache.clear();
                    this.originControllerCache.clear();
                    VRSettings.LOGGER.info(
                        "ForkXR: Touch Plus interaction profile changed; refreshed controller bindings");
                    this.logCurrentInteractionProfile(ControllerType.LEFT);
                    this.logCurrentInteractionProfile(ControllerType.RIGHT);
                }
                case XR10.XR_TYPE_EVENT_DATA_REFERENCE_SPACE_CHANGE_PENDING -> {
                }
                default -> {
                }
            }
        }
    }

    private void logCurrentInteractionProfile(ControllerType hand) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrInteractionProfileState profile =
                XrInteractionProfileState.calloc(stack).type$Default();
            int error = XR10.xrGetCurrentInteractionProfile(
                this.session,
                this.subactionPath(hand),
                profile);
            this.logError(
                error,
                "xrGetCurrentInteractionProfile",
                hand.name());
            if (error < 0 || profile.interactionProfile() == XR10.XR_NULL_PATH) {
                VRSettings.LOGGER.warn(
                    "ForkXR: VDXR reports no active interaction profile for {}",
                    hand.name().toLowerCase(Locale.ROOT));
                return;
            }

            IntBuffer size = stack.callocInt(1);
            error = XR10.xrPathToString(
                this.instance,
                profile.interactionProfile(),
                size,
                null);
            this.logError(error, "xrPathToString", "interaction profile size");
            if (error < 0 || size.get(0) < 1) {
                return;
            }
            ByteBuffer value = stack.calloc(size.get(0));
            error = XR10.xrPathToString(
                this.instance,
                profile.interactionProfile(),
                size,
                value);
            this.logError(error, "xrPathToString", "interaction profile");
            if (error >= 0) {
                VRSettings.LOGGER.info(
                    "ForkXR: VDXR {} controller profile is {}",
                    hand.name().toLowerCase(Locale.ROOT),
                    memUTF8(memAddress(value)));
            }
        }
    }

    private void sessionChanged(XrEventDataSessionStateChanged xrEventDataSessionStateChanged) {
        int state = xrEventDataSessionStateChanged.state();
        VRSettings.LOGGER.info(
            "ForkXR: OpenXR session state {} -> {}",
            sessionStateName(this.sessionState),
            sessionStateName(state));
        this.sessionState = state;

        switch (state) {
            case XR10.XR_SESSION_STATE_READY: {
                try (MemoryStack stack = MemoryStack.stackPush()){
                    XrSessionBeginInfo sessionBeginInfo = XrSessionBeginInfo.calloc(stack);
                    sessionBeginInfo.type(XR10.XR_TYPE_SESSION_BEGIN_INFO);
                    sessionBeginInfo.next(NULL);
                    sessionBeginInfo.primaryViewConfigurationType(XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO);

                    int error = XR10.xrBeginSession(session, sessionBeginInfo);
                    logError(error, "xrBeginSession",  "XR_SESSION_STATE_READY");
                    this.sessionRunning = error >= 0;
                }
                this.isActive = this.sessionRunning;
                break;
            }
            case XR10.XR_SESSION_STATE_STOPPING: {
                this.isActive = false;
                if (this.sessionRunning) {
                    int error = XR10.xrEndSession(session);
                    logError(error, "xrEndSession",  "XR_SESSION_STATE_STOPPING");
                    if (error >= 0) {
                        this.sessionRunning = false;
                    }
                }
                break;
            }
            case XR10.XR_SESSION_STATE_SYNCHRONIZED,
                 XR10.XR_SESSION_STATE_VISIBLE,
                 XR10.XR_SESSION_STATE_FOCUSED: {
                // SYNCHRONIZED is a running session. OpenXR requires the app
                // to submit frames from this state so the runtime can advance
                // to VISIBLE/FOCUSED. Treating it as inactive deadlocks VDXR
                // on a black compositor surface.
                this.isActive = this.sessionRunning;
                break;
            }
            case XR10.XR_SESSION_STATE_EXITING, XR10.XR_SESSION_STATE_IDLE: {
                this.isActive = false;
                this.sessionRunning = false;
                break;
            }
            case XR10.XR_SESSION_STATE_LOSS_PENDING: {
                this.isActive = false;
                this.sessionRunning = false;
                break;
            }
            default:
                break;
        }
    }

    @Override
    public Vector2f getPlayAreaSize() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrExtent2Df vec = XrExtent2Df.calloc(stack);
            int error = XR10.xrGetReferenceSpaceBoundsRect(session, XR10.XR_REFERENCE_SPACE_TYPE_STAGE, vec);
            logError(error, "xrGetReferenceSpaceBoundsRect",  "");
            return new Vector2f(vec.width(), vec.height());
        }
    }

    @Override
    public boolean init() {
        if (this.initialized) {
            return true;
        } else if (this.tried) {
            return this.initialized;
        } else {
            tried = true;
            this.mc = Minecraft.getInstance();
            try {
                this.initializeOpenXRInstance();
                this.initializeOpenXRSession();
                this.initializeOpenXRSpace();
                this.initializeOpenXRSwapChain();
                this.initInputAndApplication();
            } catch (Exception e) {
                VRSettings.LOGGER.error(
                    "ForkXR: native OpenXR initialization failed", e);
                this.initSuccess = false;
                String message = e.getLocalizedMessage();
                this.initStatus = message == null || message.isBlank()
                    ? e.getClass().getSimpleName()
                    : message;
                this.destroy();
                return false;
            }

            VRSettings.LOGGER.info(
                "ForkXR: native OpenXR initialized; waiting for the runtime READY state");
            this.deviceVelocity = new Vector3f[64];

            for (int i = 0; i < this.poseMatrices.length; ++i) {
                this.poseMatrices[i] = new Matrix4f();
                this.deviceVelocity[i] = new Vector3f();
            }

            this.initialized = true;
            return true;
        }
    }

    private void initializeOpenXRInstance() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            device.initOpenXRLoader(stack);

            //Check extensions
            IntBuffer numExtensions = stack.callocInt(1);
            int error = XR10.xrEnumerateInstanceExtensionProperties((ByteBuffer) null, numExtensions, null);
            logError(error, "xrEnumerateInstanceExtensionProperties",  "get count");

            XrExtensionProperties.Buffer properties = new XrExtensionProperties.Buffer(
                bufferStack(numExtensions.get(0), XrExtensionProperties.SIZEOF, XR10.XR_TYPE_EXTENSION_PROPERTIES)
            );

            //Load extensions
            error = XR10.xrEnumerateInstanceExtensionProperties((ByteBuffer) null, numExtensions, properties);
            logError(error, "xrEnumerateInstanceExtensionProperties",  "get extensions");

            //get needed extensions
            String graphicsExtension = device.getGraphicsExtension();
            boolean missingGraphics = true;
            PointerBuffer extensions = stack.callocPointer(2);
            while (properties.hasRemaining()) {
                XrExtensionProperties prop = properties.get();
                String extensionName = prop.extensionNameString();
                if (extensionName.equals(graphicsExtension)) {
                    missingGraphics = false;
                    extensions.put(memAddress(stackUTF8(graphicsExtension)));
                }
                if (extensionName.equals("XR_META_touch_controller_plus")) {
                    this.metaTouchPlusProfileEnabled = true;
                    extensions.put(memAddress(
                        stackUTF8("XR_META_touch_controller_plus")));
                }
            }

            if (missingGraphics) {
                throw new IllegalStateException(
                    "The active OpenXR runtime does not expose Vulkan enable2");
            }
            VRSettings.LOGGER.info(
                "ForkXR: native Meta Touch Plus OpenXR profile {}",
                this.metaTouchPlusProfileEnabled
                    ? "enabled"
                    : "unavailable; using Oculus Touch compatibility");

            //Create APP info
            XrApplicationInfo applicationInfo = XrApplicationInfo.calloc(stack);
            applicationInfo.apiVersion(XR10.XR_MAKE_VERSION(1, 0, 40));
            applicationInfo.applicationName(stack.UTF8("ForkXR"));
            applicationInfo.applicationVersion(APPLICATION_VERSION);

            //Create instance info
            XrInstanceCreateInfo createInfo = XrInstanceCreateInfo.calloc(stack);
            createInfo.type(XR10.XR_TYPE_INSTANCE_CREATE_INFO);
            createInfo.next(device.getPlatformInfo(stack));
            createInfo.createFlags(0);
            createInfo.applicationInfo(applicationInfo);
            createInfo.enabledApiLayerNames(null);
            createInfo.enabledExtensionNames(extensions.flip());

            //Create XR instance
            PointerBuffer instancePtr = stack.callocPointer(1);
            int xrResult = XR10.xrCreateInstance(createInfo, instancePtr);
            if (xrResult == XR10.XR_ERROR_RUNTIME_FAILURE) {
                throw new IllegalStateException(
                    "The OpenXR runtime could not create an instance; verify that the headset is connected");
            } else if (xrResult == XR10.XR_ERROR_INSTANCE_LOST) {
                throw new IllegalStateException(
                    "The OpenXR runtime was restarted while ForkXR was initializing");
            } else if (xrResult < 0) {
                throw new IllegalStateException(
                    "xrCreateInstance failed with OpenXR result " + xrResult);
            }
            instance = new XrInstance(instancePtr.get(0), createInfo);

            XrInstanceProperties instanceProperties =
                XrInstanceProperties.calloc(stack).type$Default();
            int propertiesResult =
                XR10.xrGetInstanceProperties(instance, instanceProperties);
            logError(propertiesResult, "xrGetInstanceProperties", "");
            this.runtimeName = instanceProperties.runtimeNameString();
            String normalizedRuntime = this.runtimeName
                .toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "");
            if (!(normalizedRuntime.contains("vdxr") ||
                normalizedRuntime.contains("virtualdesktopxr") ||
                normalizedRuntime.contains("virtualdesktopopenxr")))
            {
                throw new IllegalStateException(
                    "Unsupported OpenXR runtime '" + this.runtimeName +
                        "'. ForkXR requires Virtual Desktop VDXR.");
            }

            this.poseMatrices = new Matrix4f[64];

            for (int i = 0; i < this.poseMatrices.length; ++i) {
                this.poseMatrices[i] = new Matrix4f();
            }

            this.initSuccess = true;
        }
    }

    public static MCOpenXR get() {
        return OME;
    }

    private void initializeOpenXRSession() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            //Create system
            XrSystemGetInfo system = XrSystemGetInfo.calloc(stack);
            system.type(XR10.XR_TYPE_SYSTEM_GET_INFO);
            system.next(NULL);
            system.formFactor(XR10.XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY);

            LongBuffer longBuffer = stack.callocLong(1);
            int error = XR10.xrGetSystem(instance, system, longBuffer);
            logError(error, "xrGetSystem",  "");
            this.systemID = longBuffer.get(0);

            if (systemID == 0) {
                throw new IllegalStateException(
                    "The OpenXR runtime did not report a connected headset");
            }

            XrSystemProperties systemProperties = XrSystemProperties.calloc(stack).type(XR10.XR_TYPE_SYSTEM_PROPERTIES);
            error = XR10.xrGetSystemProperties(instance, systemID, systemProperties);
            MCOpenXR.get().logError(error, "xrGetSystemProperties",  "");
            XrSystemTrackingProperties trackingProperties = systemProperties.trackingProperties();
            XrSystemGraphicsProperties graphicsProperties = systemProperties.graphicsProperties();

            MCOpenXR.get().systemName = memUTF8(memAddress(systemProperties.systemName()));
            int vendor = systemProperties.vendorId();
            boolean orientationTracking = trackingProperties.orientationTracking();
            boolean positionTracking = trackingProperties.positionTracking();
            int maxWidth = graphicsProperties.maxSwapchainImageWidth();
            int maxHeight = graphicsProperties.maxSwapchainImageHeight();
            int maxLayerCount = graphicsProperties.maxLayerCount();

            if (maxWidth < VulkanXrBackend.EYE_WIDTH ||
                maxHeight < VulkanXrBackend.EYE_HEIGHT)
            {
                throw new IllegalStateException(
                    "Unsupported Hardware - Meta Quest 3S via VDXR Only " +
                        "(runtime cannot allocate 1832x1920 per eye)");
            }

            VRSettings.LOGGER.info("Found device with id:  {}", systemID);
            VRSettings.LOGGER.info("Headset Name: {}, Vendor: {}", MCOpenXR.get().systemName, vendor);
            VRSettings.LOGGER.info("Headset Orientation Tracking: {}, Position Tracking: {}", orientationTracking, positionTracking);
            VRSettings.LOGGER.info("Headset Max Width: {}, Max Height: {}, Max Layer Count: {}", maxWidth, maxHeight, maxLayerCount);

            //Create session
            XrSessionCreateInfo info = XrSessionCreateInfo.calloc(stack);
            info.type(XR10.XR_TYPE_SESSION_CREATE_INFO);
            info.next(device.checkGraphics(stack, instance, systemID).address());
            info.createFlags(0);
            info.systemId(systemID);

            PointerBuffer sessionPtr = stack.callocPointer(1);
            error = XR10.xrCreateSession(instance, info, sessionPtr);
            logError(error, "xrCreateSession",  "");

            session = new XrSession(sessionPtr.get(0), instance);

        }
    }

    private void initializeOpenXRSpace() {
        try (MemoryStack stack = MemoryStack.stackPush()){
            XrPosef identityPose = XrPosef.calloc(stack);
            identityPose.set(
                XrQuaternionf.calloc(stack).set(0, 0, 0, 1),
                XrVector3f.calloc(stack)
            );

            XrReferenceSpaceCreateInfo referenceSpaceCreateInfo = XrReferenceSpaceCreateInfo.calloc(stack);
            referenceSpaceCreateInfo.type(XR10.XR_TYPE_REFERENCE_SPACE_CREATE_INFO);
            referenceSpaceCreateInfo.next(NULL);
            referenceSpaceCreateInfo.referenceSpaceType(XR10.XR_REFERENCE_SPACE_TYPE_STAGE);
            referenceSpaceCreateInfo.poseInReferenceSpace(identityPose);

            PointerBuffer pp = stack.callocPointer(1);
            int error = XR10.xrCreateReferenceSpace(session, referenceSpaceCreateInfo, pp);
            xrAppSpace = new XrSpace(pp.get(0), session);
            logError(error, "xrCreateReferenceSpace",  "XR_REFERENCE_SPACE_TYPE_STAGE");

            referenceSpaceCreateInfo.referenceSpaceType(XR10.XR_REFERENCE_SPACE_TYPE_VIEW);
            error = XR10.xrCreateReferenceSpace(session, referenceSpaceCreateInfo, pp);
            logError(error, "xrCreateReferenceSpace",  "XR_REFERENCE_SPACE_TYPE_VIEW");
            xrViewSpace = new XrSpace(pp.get(0), session);
        }
    }

    private void initializeOpenXRSwapChain() {
        try (MemoryStack stack = stackPush()) {
            //Check amount of views
            IntBuffer intBuf = stack.callocInt(1);
            int error = XR10.xrEnumerateViewConfigurationViews(instance, systemID,  XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, intBuf, null);
            logError(error, "xrEnumerateViewConfigurationViews",  "get count");

            //Get all views
            ByteBuffer viewConfBuffer = bufferStack(intBuf.get(0), XrViewConfigurationView.SIZEOF, XR10.XR_TYPE_VIEW_CONFIGURATION_VIEW);
            XrViewConfigurationView.Buffer views = new XrViewConfigurationView.Buffer(viewConfBuffer);
            error = XR10.xrEnumerateViewConfigurationViews(instance, systemID,  XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, intBuf, views);
            logError(error, "xrEnumerateViewConfigurationViews",  "get views");
            int viewCountNumber = intBuf.get(0);

            this.viewBuffer = new XrView.Buffer(
                bufferHeap(viewCountNumber, XrView.SIZEOF, XR10.XR_TYPE_VIEW)
            );
            //Check swapchain formats
            error = XR10.xrEnumerateSwapchainFormats(session, intBuf, null);
            logError(error, "xrEnumerateSwapchainFormats",  "get count");

            //Get swapchain formats
            LongBuffer swapchainFormats = stack.callocLong(intBuf.get(0));
            error = XR10.xrEnumerateSwapchainFormats(session, intBuf, swapchainFormats);
            logError(error, "xrEnumerateSwapchainFormats",  "get formats");

            // Use one explicit RGBA sRGB format. glReadPixels produces RGBA bytes
            // and copying those unchanged avoids the channel swap/monochrome bug.
            long chosenFormat = 0;
            swapchainFormats.rewind();
            while (swapchainFormats.hasRemaining()) {
                if (swapchainFormats.get() == VulkanXrBackend.FORMAT) {
                    chosenFormat = VulkanXrBackend.FORMAT;
                    break;
                }
            }

            if (chosenFormat == 0) {
                var formats = new ArrayList<Long>();
                swapchainFormats.rewind();
                while (swapchainFormats.hasRemaining()) {
                    formats.add(swapchainFormats.get());
                }
                throw new RuntimeException("No compatible swapchain / framebuffer format available: " + formats);
            }

            //Make swapchain
            XrSwapchainCreateInfo swapchainCreateInfo = XrSwapchainCreateInfo.calloc(stack);
            swapchainCreateInfo.type(XR10.XR_TYPE_SWAPCHAIN_CREATE_INFO);
            swapchainCreateInfo.next(NULL);
            swapchainCreateInfo.createFlags(0);
            swapchainCreateInfo.usageFlags(
                XR10.XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT |
                    XR10.XR_SWAPCHAIN_USAGE_TRANSFER_DST_BIT);
            swapchainCreateInfo.format(chosenFormat);
            swapchainCreateInfo.sampleCount(1);
            swapchainCreateInfo.width(VulkanXrBackend.EYE_WIDTH);
            swapchainCreateInfo.height(VulkanXrBackend.EYE_HEIGHT);
            swapchainCreateInfo.faceCount(1);
            swapchainCreateInfo.arraySize(2);
            swapchainCreateInfo.mipCount(1);

            PointerBuffer handlePointer = stack.callocPointer(1);
            error = XR10.xrCreateSwapchain(session, swapchainCreateInfo, handlePointer);
            logError(error, "xrCreateSwapchain",  "format: " + chosenFormat);
            swapchain = new XrSwapchain(handlePointer.get(0), session);
            this.width = swapchainCreateInfo.width();
            this.height = swapchainCreateInfo.height();
        }
    }

    /**
     * Creates an array of XrStructs with their types pre set to @param type
     */
    static ByteBuffer bufferStack(int capacity, int sizeof, int type) {
        ByteBuffer b = stackCalloc(capacity * sizeof);

        for (int i = 0; i < capacity; i++) {
            b.position(i * sizeof);
            b.putInt(type);
        }
        b.rewind();
        return b;
    }

    private void initInputAndApplication() {
        this.populateInputActions();
        this.loadActionHandles();
        this.loadDefaultBindings();
        this.inputInitialized = true;
    }

    @Override
    public Matrix4f getControllerComponentTransform(int var1, String var2) {
        return new Matrix4f();
    }

    @Override
    public boolean hasCameraTracker() {
        return false;
    }

    @Override
    public List<Long> getOrigins(VRInputAction var1) {
        List<Long> cached = this.boundSourcesCache.get(var1.handle);
        if (cached != null) {
            return cached;
        }
        try (MemoryStack stack = MemoryStack.stackPush()){
            XrBoundSourcesForActionEnumerateInfo info = XrBoundSourcesForActionEnumerateInfo.calloc(stack);
            info.type(XR10.XR_TYPE_BOUND_SOURCES_FOR_ACTION_ENUMERATE_INFO);
            info.next(NULL);
            info.action(new XrAction(var1.handle, new XrActionSet(actionSetHandles.get(var1.actionSet), instance)));
            IntBuffer buf = stack.callocInt(1);
            int error = XR10.xrEnumerateBoundSourcesForAction(session, info, buf, null);
            logError(error, "xrEnumerateBoundSourcesForAction",  var1.name);

            int size = buf.get();
            if (size <= 0) {
                List<Long> empty = List.of();
                this.boundSourcesCache.put(var1.handle, empty);
                return empty;
            }

            buf = stack.callocInt(size);
            LongBuffer longbuf = stack.callocLong(size);
            error = XR10.xrEnumerateBoundSourcesForAction(session, info, buf, longbuf);
            logError(error, "xrEnumerateBoundSourcesForAction",  var1.name);
            longbuf.rewind();
            long[] array = new long[longbuf.remaining()];
            int index = 0;
            while (longbuf.hasRemaining()) {
                array[index++] = longbuf.get();
            }
            List<Long> origins = Arrays.stream(array).boxed().toList();
            this.boundSourcesCache.put(var1.handle, origins);
            return origins;
        }
    }

    @Override
    public String getOriginName(long l) {
        if (l == XR10.XR_NULL_PATH) {
            return "";
        }
        try (MemoryStack stack = MemoryStack.stackPush()){
            XrInputSourceLocalizedNameGetInfo info = XrInputSourceLocalizedNameGetInfo.calloc(stack);
            info.type(XR10.XR_TYPE_INPUT_SOURCE_LOCALIZED_NAME_GET_INFO);
            info.next(0);
            info.sourcePath(l);
            info.whichComponents(XR10.XR_INPUT_SOURCE_LOCALIZED_NAME_COMPONENT_BIT);

            IntBuffer buf = stack.callocInt(1);
            int error = XR10.xrGetInputSourceLocalizedName(session, info, buf, null);
            logError(error, "xrGetInputSourceLocalizedName",  "get length");

            int size = buf.get();
            if (size <= 0) {
                return "";
            }

            buf = stack.callocInt(size);
            ByteBuffer byteBuffer = stack.calloc(size);
            error = XR10.xrGetInputSourceLocalizedName(session, info, buf, byteBuffer);
            logError(error, "xrGetInputSourceLocalizedName",  "get String");
            if (error < 0) {
                return "";
            }
            return memUTF8(memAddress(byteBuffer));
        }
    }

    @Override
    public VRRenderer createVRRenderer() {
        return new OpenXRStereoRenderer(this);
    }

    @Override
    public boolean isActive() {
        this.pollVREvents();
        return isActive;
    }

    private static String sessionStateName(int state) {
        return switch (state) {
            case XR10.XR_SESSION_STATE_UNKNOWN -> "UNKNOWN";
            case XR10.XR_SESSION_STATE_IDLE -> "IDLE";
            case XR10.XR_SESSION_STATE_READY -> "READY";
            case XR10.XR_SESSION_STATE_SYNCHRONIZED -> "SYNCHRONIZED";
            case XR10.XR_SESSION_STATE_VISIBLE -> "VISIBLE";
            case XR10.XR_SESSION_STATE_FOCUSED -> "FOCUSED";
            case XR10.XR_SESSION_STATE_STOPPING -> "STOPPING";
            case XR10.XR_SESSION_STATE_LOSS_PENDING -> "LOSS_PENDING";
            case XR10.XR_SESSION_STATE_EXITING -> "EXITING";
            default -> "STATE_" + state;
        };
    }

    @Override
    public ControllerType getOriginControllerType(long i) {
        if (i == 0L) {
            return null;
        }
        if (i == this.getPath("/user/hand/right")) {
            return ControllerType.RIGHT;
        }
        if (i == this.getPath("/user/hand/left")) {
            return ControllerType.LEFT;
        }

        ControllerType cached = this.originControllerCache.get(i);
        if (cached != null) {
            return cached;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer size = stack.callocInt(1);
            int error = XR10.xrPathToString(this.instance, i, size, null);
            this.logError(error, "xrPathToString", "controller origin size");
            if (error < 0 || size.get(0) < 1) {
                return null;
            }
            ByteBuffer value = stack.calloc(size.get(0));
            error = XR10.xrPathToString(this.instance, i, size, value);
            this.logError(error, "xrPathToString", "controller origin");
            if (error < 0) {
                return null;
            }
            String path = memUTF8(memAddress(value));
            ControllerType controller = path.contains("/user/hand/right")
                ? ControllerType.RIGHT
                : path.contains("/user/hand/left")
                    ? ControllerType.LEFT
                    : null;
            if (controller != null) {
                this.originControllerCache.put(i, controller);
            }
            return controller;
        }
    }

    @Override
    public float getIPD() {
        return this.getEyePosition(RenderPass.RIGHT).x - this.getEyePosition(RenderPass.LEFT).x;
    }

    @Override
    public String getRuntimeName() {
        return "OpenXR";
    }

    private static final String[] BOTH_HANDS =
        new String[] {"/user/hand/left", "/user/hand/right"};
    private static final String[] RIGHT_HAND_ONLY =
        new String[] {"/user/hand/right"};
    private static final String[] LEFT_HAND_ONLY =
        new String[] {"/user/hand/left"};

    private void loadActionHandles() {
        for (VRInputActionSet vrinputactionset : VRInputActionSet.values()) {
            long actionSet = makeActionSet(instance, vrinputactionset.name, vrinputactionset.localizedName, 0);
            this.actionSetHandles.put(vrinputactionset, actionSet);
        }

        for (VRInputAction vrinputaction : this.inputActions.values()) {
            long action = createAction(vrinputaction.name, vrinputaction.name, vrinputaction.type, new XrActionSet(this.actionSetHandles.get(vrinputaction.actionSet), instance), BOTH_HANDS);
            vrinputaction.setHandle(action);
        }

        setupControllers();

        XrActionSet actionSet = new XrActionSet(this.actionSetHandles.get(VRInputActionSet.GLOBAL), instance);
        this.haptics[RIGHT_CONTROLLER] = createAction("/actions/global/out/righthaptic", "/actions/global/out/righthaptic", "haptic", actionSet, RIGHT_HAND_ONLY);
        this.haptics[LEFT_CONTROLLER] = createAction("/actions/global/out/lefthaptic", "/actions/global/out/lefthaptic", "haptic", actionSet, LEFT_HAND_ONLY);
        XrActionSet ingameActionSet = new XrActionSet(
            this.actionSetHandles.get(VRInputActionSet.INGAME),
            this.instance);
        this.leftLocomotionAction = createAction(
            "/actions/ingame/in/forkxr_left_locomotion",
            "ForkXR left Touch Plus locomotion",
            "vector2",
            ingameActionSet,
            null);
        this.rightTurnAction = createAction(
            "/actions/ingame/in/forkxr_right_turn",
            "ForkXR right Touch Plus turning",
            "vector2",
            ingameActionSet,
            null);
        this.rightJumpAction = createAction(
            "/actions/ingame/in/forkxr_right_jump",
            "ForkXR right Touch Plus jump",
            "boolean",
            ingameActionSet,
            null);
        this.menuAction = createAction(
            "/actions/global/in/forkxr_menu",
            "ForkXR Touch Plus game menu",
            "boolean",
            actionSet,
            null);
        this.inventoryAction = createAction(
            "/actions/global/in/forkxr_inventory",
            "ForkXR Touch Plus inventory",
            "boolean",
            actionSet,
            null);

    }

    private void setupControllers() {
        XrActionSet actionSet = new XrActionSet(this.actionSetHandles.get(VRInputActionSet.GLOBAL), instance);
        this.grip[RIGHT_CONTROLLER] = createAction("/actions/global/in/righthand", "/actions/global/in/righthand", "pose", actionSet, RIGHT_HAND_ONLY);
        this.grip[LEFT_CONTROLLER] = createAction("/actions/global/in/lefthand", "/actions/global/in/lefthand", "pose", actionSet, LEFT_HAND_ONLY);
        this.aim[RIGHT_CONTROLLER] = createAction("/actions/global/in/righthandaim", "/actions/global/in/righthandaim", "pose", actionSet, RIGHT_HAND_ONLY);
        this.aim[LEFT_CONTROLLER] = createAction("/actions/global/in/lefthandaim", "/actions/global/in/lefthandaim", "pose", actionSet, LEFT_HAND_ONLY);
    }

    private void loadDefaultBindings() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int error;
            for (String headset:
                XRBindings.supportedHeadsets(
                    this.metaTouchPlusProfileEnabled))
            {
                VRSettings.LOGGER.info("loading defaults for {}", headset);
                Pair<String, String>[] defaultBindings = XRBindings.getBinding(headset).toArray(new Pair[0]);
                int supplementalBindingCount = 11;
                XrActionSuggestedBinding.Buffer bindings =
                    XrActionSuggestedBinding.calloc(
                        defaultBindings.length + supplementalBindingCount,
                        stack);

                for (int i = 0; i < defaultBindings.length; i++) {
                    Pair<String, String> pair = defaultBindings[i];
                    VRInputAction binding = this.getInputActionByName(pair.getLeft());
                    if (binding.handle == 0L) {
                        VRSettings.LOGGER.error("Handle for '{}'/'{}' is null", pair.getLeft(), pair.getRight());
                        continue;
                    }
                    bindings.get(i).set(
                        new XrAction(binding.handle, new XrActionSet(actionSetHandles.get(binding.actionSet), instance)),
                        getPath(pair.getRight())
                    );
                }

                XrActionSet actionSet = new XrActionSet(actionSetHandles.get(VRInputActionSet.GLOBAL), instance);
                bindings.get(defaultBindings.length).set(
                    new XrAction(this.grip[RIGHT_CONTROLLER], actionSet),
                    getPath("/user/hand/right/input/grip/pose")
                );
                bindings.get(defaultBindings.length + 1).set(
                    new XrAction(this.grip[LEFT_CONTROLLER], actionSet),
                    getPath("/user/hand/left/input/grip/pose")
                );
                bindings.get(defaultBindings.length + 2).set(
                    new XrAction(this.aim[RIGHT_CONTROLLER], actionSet),
                    getPath("/user/hand/right/input/aim/pose")
                );
                bindings.get(defaultBindings.length + 3).set(
                    new XrAction(this.aim[LEFT_CONTROLLER], actionSet),
                    getPath("/user/hand/left/input/aim/pose")
                );

                bindings.get(defaultBindings.length + 4).set(
                    new XrAction(this.haptics[RIGHT_CONTROLLER], actionSet),
                    getPath("/user/hand/right/output/haptic")
                );

                bindings.get(defaultBindings.length + 5).set(
                    new XrAction(this.haptics[LEFT_CONTROLLER], actionSet),
                    getPath("/user/hand/left/output/haptic")
                );

                XrActionSet ingameActionSet = new XrActionSet(
                    actionSetHandles.get(VRInputActionSet.INGAME),
                    instance);
                bindings.get(defaultBindings.length + 6).set(
                    new XrAction(this.leftLocomotionAction, ingameActionSet),
                    getPath("/user/hand/left/input/thumbstick")
                );
                bindings.get(defaultBindings.length + 7).set(
                    new XrAction(this.rightTurnAction, ingameActionSet),
                    getPath("/user/hand/right/input/thumbstick")
                );
                bindings.get(defaultBindings.length + 8).set(
                    new XrAction(this.rightJumpAction, ingameActionSet),
                    getPath("/user/hand/right/input/a/click")
                );
                bindings.get(defaultBindings.length + 9).set(
                    new XrAction(this.menuAction, actionSet),
                    getPath("/user/hand/left/input/x/click")
                );
                bindings.get(defaultBindings.length + 10).set(
                    new XrAction(this.inventoryAction, actionSet),
                    getPath("/user/hand/left/input/y/click")
                );

                XrInteractionProfileSuggestedBinding suggested_binds = XrInteractionProfileSuggestedBinding.calloc(stack);
                suggested_binds.type(XR10.XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING);
                suggested_binds.next(NULL);
                suggested_binds.interactionProfile(getPath(headset));
                suggested_binds.suggestedBindings(bindings);

                error = XR10.xrSuggestInteractionProfileBindings(instance, suggested_binds);
                logError(error, "xrSuggestInteractionProfileBindings",  headset);
            }


            XrSessionActionSetsAttachInfo attach_info = XrSessionActionSetsAttachInfo.calloc(stack);
            attach_info.type(XR10.XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO);
            attach_info.next(NULL);
            attach_info.actionSets(stackPointers(actionSetHandles.values().stream().mapToLong(value -> value).toArray()));

            error = XR10.xrAttachSessionActionSets(session, attach_info);
            logError(error, "xrAttachSessionActionSets",  "");

            XrActionSet actionSet = new XrActionSet(this.actionSetHandles.get(VRInputActionSet.GLOBAL), instance);
            XrActionSpaceCreateInfo actionSpace = XrActionSpaceCreateInfo.calloc(stack);
            actionSpace.type(XR10.XR_TYPE_ACTION_SPACE_CREATE_INFO);
            actionSpace.next(NULL);
            actionSpace.poseInActionSpace()
                .orientation()
                .set(0.0F, 0.0F, 0.0F, 1.0F);
            actionSpace.action(new XrAction(grip[RIGHT_CONTROLLER], actionSet));
            actionSpace.subactionPath(getPath("/user/hand/right"));
            PointerBuffer pp = stackCallocPointer(1);
            error = XR10.xrCreateActionSpace(session, actionSpace, pp);
            logError(error, "xrCreateActionSpace",  "grip: /user/hand/right");
            this.gripSpace[RIGHT_CONTROLLER] = new XrSpace(pp.get(0), session);

            actionSpace.action(new XrAction(grip[LEFT_CONTROLLER], actionSet));
            actionSpace.subactionPath(getPath("/user/hand/left"));
            error = XR10.xrCreateActionSpace(session, actionSpace, pp);
            logError(error, "xrCreateActionSpace",  "grip: /user/hand/left");
            this.gripSpace[LEFT_CONTROLLER] = new XrSpace(pp.get(0), session);

            actionSpace.action(new XrAction(aim[RIGHT_CONTROLLER], actionSet));
            actionSpace.subactionPath(getPath("/user/hand/right"));
            error = XR10.xrCreateActionSpace(session, actionSpace, pp);
            logError(error, "xrCreateActionSpace",  "aim: /user/hand/right");
            this.aimSpace[RIGHT_CONTROLLER] = new XrSpace(pp.get(0), session);

            actionSpace.action(new XrAction(aim[LEFT_CONTROLLER], actionSet));
            actionSpace.subactionPath(getPath("/user/hand/left"));
            error = XR10.xrCreateActionSpace(session, actionSpace, pp);
            logError(error, "xrCreateActionSpace",  "aim: /user/hand/left");
            this.aimSpace[LEFT_CONTROLLER] = new XrSpace(pp.get(0), session);

        }
    }

    public long getPath(String pathString) {
        return this.paths.computeIfAbsent(pathString, s -> {
            try (MemoryStack ignored = stackPush()) {
                LongBuffer buf = stackCallocLong(1);
                int error = XR10.xrStringToPath(instance, pathString, buf);
                logError(error, "getPath",  pathString);
                return buf.get();
            }
        });
    }

    private long createAction(String name, String localisedName, String type, XrActionSet actionSet, @Nullable String[] subactionPaths) {
        try (MemoryStack stack = MemoryStack.stackPush()){
            String s = name.split("/")[name.split("/").length -1].toLowerCase();
            XrActionCreateInfo hands = XrActionCreateInfo.calloc(stack);
            hands.type(XR10.XR_TYPE_ACTION_CREATE_INFO);
            hands.next(NULL);
            hands.actionName(memUTF8(s));
            switch (type) {
                case "boolean" -> hands.actionType(XR10.XR_ACTION_TYPE_BOOLEAN_INPUT);
                case "vector1" -> hands.actionType(XR10.XR_ACTION_TYPE_FLOAT_INPUT);
                case "vector2" -> hands.actionType(XR10.XR_ACTION_TYPE_VECTOR2F_INPUT);
                case "pose" -> hands.actionType(XR10.XR_ACTION_TYPE_POSE_INPUT);
                case "haptic" -> hands.actionType(XR10.XR_ACTION_TYPE_VIBRATION_OUTPUT);
            }
            if(subactionPaths != null) {
                LongBuffer buffer = stackCallocLong(subactionPaths.length);
                for(String path : subactionPaths) {
                    buffer.put(getPath(path));
                }
                hands.countSubactionPaths(subactionPaths.length);
                hands.subactionPaths(buffer.rewind());
            } else {
                hands.countSubactionPaths(0);
                hands.subactionPaths(null);
            }
            hands.localizedActionName(memUTF8(s));
            PointerBuffer buffer = stackCallocPointer(1);

            int error = XR10.xrCreateAction(actionSet, hands, buffer);
            logError(error, "xrCreateAction",  "name:", name, "type:", type);
            return buffer.get(0);
        }
    }

    private long makeActionSet(XrInstance instance, String name, String localisedName, int priority) {
        try (MemoryStack stack = MemoryStack.stackPush()){
            XrActionSetCreateInfo info = XrActionSetCreateInfo.calloc(stack);
            info.type(XR10.XR_TYPE_ACTION_SET_CREATE_INFO);
            info.next(NULL);
            info.actionSetName(memUTF8(localisedName.toLowerCase()));
            info.localizedActionSetName(memUTF8(localisedName.toLowerCase()));
            info.priority(priority);
            PointerBuffer buffer = stack.callocPointer(1);

            int error = XR10.xrCreateActionSet(instance, info, buffer);
            logError(error, "makeActionSet", localisedName.toLowerCase());
            return buffer.get(0);
        }
    }

    static ByteBuffer bufferHeap(int capacity, int sizeof, int type) {
        ByteBuffer b = memCalloc(capacity * sizeof);

        for (int i = 0; i < capacity; i++) {
            b.position(i * sizeof);
            b.putInt(type);
        }
        b.rewind();
        return b;
    }

    /**
     * gets the String for the given xrResult
     */
    private String getResultName(int xrResult) {
        String resultString = switch (xrResult) {
            case 1 -> "XR_TIMEOUT_EXPIRED";
            case 3 -> "XR_SESSION_LOSS_PENDING";
            case 4 -> "XR_EVENT_UNAVAILABLE";
            case 7 -> "XR_SPACE_BOUNDS_UNAVAILABLE";
            case 8 -> "XR_SESSION_NOT_FOCUSED";
            case 9 -> "XR_FRAME_DISCARDED";
            case -1 -> "XR_ERROR_VALIDATION_FAILURE";
            case -2 -> "XR_ERROR_RUNTIME_FAILURE";
            case -3 -> "XR_ERROR_OUT_OF_MEMORY";
            case -4 -> "XR_ERROR_API_VERSION_UNSUPPORTED";
            case -6 -> "XR_ERROR_INITIALIZATION_FAILED";
            case -7 -> "XR_ERROR_FUNCTION_UNSUPPORTED";
            case -8 -> "XR_ERROR_FEATURE_UNSUPPORTED";
            case -9 -> "XR_ERROR_EXTENSION_NOT_PRESENT";
            case -10 -> "XR_ERROR_LIMIT_REACHED";
            case -11 -> "XR_ERROR_SIZE_INSUFFICIENT";
            case -12 -> "XR_ERROR_HANDLE_INVALID";
            case -13 -> "XR_ERROR_INSTANCE_LOST";
            case -14 -> "XR_ERROR_SESSION_RUNNING";
            case -16 -> "XR_ERROR_SESSION_NOT_RUNNING";
            case -17 -> "XR_ERROR_SESSION_LOST";
            case -18 -> "XR_ERROR_SYSTEM_INVALID";
            case -19 -> "XR_ERROR_PATH_INVALID";
            case -20 -> "XR_ERROR_PATH_COUNT_EXCEEDED";
            case -21 -> "XR_ERROR_PATH_FORMAT_INVALID";
            case -22 -> "XR_ERROR_PATH_UNSUPPORTED";
            case -23 -> "XR_ERROR_LAYER_INVALID";
            case -24 -> "XR_ERROR_LAYER_LIMIT_EXCEEDED";
            case -25 -> "XR_ERROR_SWAPCHAIN_RECT_INVALID";
            case -26 -> "XR_ERROR_SWAPCHAIN_FORMAT_UNSUPPORTED";
            case -27 -> "XR_ERROR_ACTION_TYPE_MISMATCH";
            case -28 -> "XR_ERROR_SESSION_NOT_READY";
            case -29 -> "XR_ERROR_SESSION_NOT_STOPPING";
            case -30 -> "XR_ERROR_TIME_INVALID";
            case -31 -> "XR_ERROR_REFERENCE_SPACE_UNSUPPORTED";
            case -32 -> "XR_ERROR_FILE_ACCESS_ERROR";
            case -33 -> "XR_ERROR_FILE_CONTENTS_INVALID";
            case -34 -> "XR_ERROR_FORM_FACTOR_UNSUPPORTED";
            case -35 -> "XR_ERROR_FORM_FACTOR_UNAVAILABLE";
            case -36 -> "XR_ERROR_API_LAYER_NOT_PRESENT";
            case -37 -> "XR_ERROR_CALL_ORDER_INVALID";
            case -38 -> "XR_ERROR_GRAPHICS_DEVICE_INVALID";
            case -39 -> "XR_ERROR_POSE_INVALID";
            case -40 -> "XR_ERROR_INDEX_OUT_OF_RANGE";
            case -41 -> "XR_ERROR_VIEW_CONFIGURATION_TYPE_UNSUPPORTED";
            case -42 -> "XR_ERROR_ENVIRONMENT_BLEND_MODE_UNSUPPORTED";
            case -44 -> "XR_ERROR_NAME_DUPLICATED";
            case -45 -> "XR_ERROR_NAME_INVALID";
            case -46 -> "XR_ERROR_ACTIONSET_NOT_ATTACHED";
            case -47 -> "XR_ERROR_ACTIONSETS_ALREADY_ATTACHED";
            case -48 -> "XR_ERROR_LOCALIZED_NAME_DUPLICATED";
            case -49 -> "XR_ERROR_LOCALIZED_NAME_INVALID";
            case -50 -> "XR_ERROR_GRAPHICS_REQUIREMENTS_CALL_MISSING";
            case -51 -> "XR_ERROR_RUNTIME_UNAVAILABLE";
            default -> null;
        };
        if (resultString == null) {
            // ask the runtime for the xrResult name
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer str = stack.calloc(XR10.XR_MAX_RESULT_STRING_SIZE);

                if (XR10.xrResultToString(instance, xrResult, str) == XR10.XR_SUCCESS) {
                    resultString = (memUTF8(memAddress(str)));
                } else {
                    resultString = "Unknown Error: " + xrResult;
                }
            }
        }
        return resultString;
    }

    /**
     * logs only errors
     * @param xrResult result to check
     * @param caller where the xrResult came from
     * @param args arguments may be helpful in locating the error
     */
    protected void logError(int xrResult, String caller, String... args) {
        if (xrResult < 0) {
            VRSettings.LOGGER.error("{} for {} errored: {}", caller, String.join(" ", args), getResultName(xrResult));
        }
    }
}
