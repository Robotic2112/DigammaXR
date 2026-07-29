package org.vivecraft.client_vr;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.vivecraft.client.Xplat;
import org.vivecraft.client.gui.screens.ErrorScreen;
import org.vivecraft.client.utils.TextUtils;
import org.vivecraft.client_vr.gameplay.VRPlayer;
import org.vivecraft.client_vr.menuworlds.MenuWorldRenderer;
import org.vivecraft.client_vr.provider.nullvr.NullVR;
import org.vivecraft.client_vr.provider.openxr.MCOpenXR;
import org.vivecraft.client_vr.render.RenderConfigException;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_xr.render_pass.RenderPassManager;
import org.vivecraft.mod_compat_vr.ShadersHelper;
import org.vivecraft.mod_compat_vr.optifine.OptifineHelper;

/**
 * this class holds the current VR states and handles starting and stopping VR
 */
public class VRState {

    /**
     * true when VR is enabled
     */
    public static boolean VR_ENABLED = false;
    /**
     * true when VR is enabled, and successfully initialized
     */
    public static boolean VR_INITIALIZED = false;
    /**
     * true when VR is enabled, successfully initialized and currently active
     */
    public static boolean VR_RUNNING = false;

    public static void initializeVR() {
        if (VR_INITIALIZED) {
            return;
        }
        try {
            if (OptifineHelper.isOptifineLoaded() && OptifineHelper.isAntialiasing()) {
                throw new RenderConfigException(
                    Component.translatable("vivecraft.messages.incompatiblesettings"),
                    Component.translatable("vivecraft.messages.optifineaa"));
            }

            ClientDataHolderVR dh = ClientDataHolderVR.getInstance();
            Minecraft instance = Minecraft.getInstance();
            dh.vr = dh.vrSettings.stereoProviderPluginID == VRSettings.VRProvider.NULLVR
                ? new NullVR(instance, dh)
                : new MCOpenXR(instance, dh);

            if (!dh.vr.init()) {
                throw new RenderConfigException(Component.translatable("vivecraft.messages.vriniterror"),
                    Component.translatable("vivecraft.messages.rendersetupfailed", dh.vr.initStatus, dh.vr.getName()));
            }

            dh.vrRenderer = dh.vr.createVRRenderer();

            // everything related to VR is created now
            VR_INITIALIZED = true;

            dh.vrRenderer.setupRenderConfiguration(false); //For openXR, setup but don't render yet
            RenderPassManager.setVanillaRenderPass();

            dh.vrPlayer = new VRPlayer();
            dh.vrPlayer.registerTracker(dh.backpackTracker);
            dh.vrPlayer.registerTracker(dh.bowTracker);
            dh.vrPlayer.registerTracker(dh.climbTracker);
            dh.vrPlayer.registerTracker(dh.autoFood);
            dh.vrPlayer.registerTracker(dh.jumpTracker);
            dh.vrPlayer.registerTracker(dh.rowTracker);
            dh.vrPlayer.registerTracker(dh.runTracker);
            dh.vrPlayer.registerTracker(dh.sneakTracker);
            dh.vrPlayer.registerTracker(dh.swimTracker);
            dh.vrPlayer.registerTracker(dh.swingTracker);
            dh.vrPlayer.registerTracker(dh.interactTracker);
            dh.vrPlayer.registerTracker(dh.teleportTracker);
            dh.vrPlayer.registerTracker(dh.horseTracker);
            dh.vrPlayer.registerTracker(dh.vehicleTracker);
            dh.vrPlayer.registerTracker(dh.crawlTracker);
            dh.vrPlayer.registerTracker(dh.cameraTracker);

            dh.menuWorldRenderer = new MenuWorldRenderer();

            dh.menuWorldRenderer.init();

        } catch (Throwable exception) {
            VRSettings.LOGGER.error("Vivecraft: Failed to initialize VR: ", exception);
            destroyVR(true);
            if (exception instanceof RenderConfigException renderConfigException) {
                Minecraft.getInstance()
                    .setScreen(new ErrorScreen(renderConfigException.title, renderConfigException.error));
            } else {
                Minecraft.getInstance()
                    .setScreen(new ErrorScreen(Component.translatable("vivecraft.messages.vriniterror"),
                        TextUtils.throwableToComponent(exception)));
            }
        }
    }

    public static void destroyVR(boolean disableVRSetting) {
        ClientDataHolderVR dh = ClientDataHolderVR.getInstance();
        if (dh.vr != null) {
            dh.vr.destroy();
        }
        dh.vr = null;
        dh.vrPlayer = null;
        if (dh.vrRenderer != null) {
            dh.vrRenderer.destroy();
            dh.vrRenderer = null;
        }
        if (dh.menuWorldRenderer != null) {
            dh.menuWorldRenderer.completeDestroy();
            dh.menuWorldRenderer = null;
        }
        VR_ENABLED = false;
        VR_INITIALIZED = false;
        VR_RUNNING = false;
        if (disableVRSetting) {
            dh.vrSettings.vrEnabled = false;
            dh.vrSettings.saveOptions();
        }
        // fixes an issue with DH shaders where the depth texture gets stuck
        if (Xplat.isModLoaded("distanthorizons") && disableVRSetting) {
            ShadersHelper.maybeReloadShaders();
        }
    }
}
