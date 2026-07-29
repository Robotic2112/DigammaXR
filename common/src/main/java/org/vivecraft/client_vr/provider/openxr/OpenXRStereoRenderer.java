package org.vivecraft.client_vr.provider.openxr;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Tuple;
import org.joml.Matrix4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.XrCompositionLayerProjection;
import org.lwjgl.openxr.XrCompositionLayerProjectionView;
import org.lwjgl.openxr.XrFovf;
import org.lwjgl.openxr.XrFrameEndInfo;
import org.lwjgl.openxr.XrSwapchainImageAcquireInfo;
import org.lwjgl.openxr.XrSwapchainImageBaseHeader;
import org.lwjgl.openxr.XrSwapchainImageReleaseInfo;
import org.lwjgl.openxr.XrSwapchainImageVulkanKHR;
import org.lwjgl.openxr.XrSwapchainImageWaitInfo;
import org.lwjgl.openxr.XR10;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRTextureTarget;
import org.vivecraft.client_vr.provider.VRRenderer;
import org.vivecraft.client_vr.render.RenderConfigException;
import org.vivecraft.client_vr.settings.VRSettings;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Vivecraft stereo renderer backed by a native Vulkan OpenXR swapchain.
 *
 * <p>Vivecraft renders complete left and right eye scenes into normal OpenGL
 * targets. This class reads those final targets and transfers them to VDXR
 * through {@link VulkanXrBackend}. The rest of Vivecraft's camera, hand,
 * controller, GUI, HUD and room-scale pipeline remains unchanged.</p>
 */
public final class OpenXRStereoRenderer extends VRRenderer {
    private final MCOpenXR openxr;
    private long[] swapchainImages;
    private boolean[][] imageInitialized;
    private int swapIndex = -1;
    private boolean frameHasImage;
    private XrCompositionLayerProjectionView.Buffer projectionLayerViews;
    private VRTextureTarget rightFramebuffer;
    private VRTextureTarget leftFramebuffer;
    private boolean firstFrameLogged;
    private boolean firstPixelCheckLogged;

    public OpenXRStereoRenderer(MCOpenXR vr) {
        super(vr);
        this.openxr = vr;
    }

    @Override
    public void createRenderTexture(int width, int height)
        throws RenderConfigException
    {
        if (width != VulkanXrBackend.EYE_WIDTH ||
            height != VulkanXrBackend.EYE_HEIGHT)
        {
            throw renderError(
                "GeneXR eye resolution mismatch",
                "Expected 1832x1920 but renderer requested " +
                    width + "x" + height);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.callocInt(1);
            int error =
                XR10.xrEnumerateSwapchainImages(
                    this.openxr.swapchain, count, null);
            this.openxr.logError(
                error, "xrEnumerateSwapchainImages", "get count");
            if (error < 0 || count.get(0) < 1) {
                throw renderError(
                    "OpenXR swapchain image enumeration failed",
                    "VDXR returned no Vulkan swapchain images");
            }

            int imageCount = count.get(0);
            XrSwapchainImageVulkanKHR.Buffer images =
                this.openxr.device.createImageBuffers(imageCount, stack);
            error = XR10.xrEnumerateSwapchainImages(
                this.openxr.swapchain,
                count,
                XrSwapchainImageBaseHeader.create(
                    images.address(), images.capacity()));
            this.openxr.logError(
                error, "xrEnumerateSwapchainImages", "get Vulkan images");
            if (error < 0) {
                throw renderError(
                    "OpenXR Vulkan image enumeration failed",
                    "VDXR rejected the Vulkan swapchain image buffer");
            }

            this.swapchainImages = new long[imageCount];
            this.imageInitialized = new boolean[imageCount][2];
            for (int i = 0; i < imageCount; i++) {
                this.swapchainImages[i] = images.get(i).image();
            }
        }

        this.rightFramebuffer = new VRTextureTarget(
            "GeneXR Right Eye",
            width,
            height,
            true,
            -1,
            true,
            true,
            ClientDataHolderVR.getInstance().vrSettings.vrUseStencil);
        this.leftFramebuffer = new VRTextureTarget(
            "GeneXR Left Eye",
            width,
            height,
            true,
            -1,
            true,
            true,
            ClientDataHolderVR.getInstance().vrSettings.vrUseStencil);
    }

    @Override
    public void setupRenderConfiguration(boolean render)
        throws IOException, RenderConfigException
    {
        super.setupRenderConfiguration(render);
        this.frameHasImage = false;
        this.swapIndex = -1;

        if (!render || !this.openxr.frameBegun ||
            !this.openxr.shouldRender)
        {
            return;
        }

        this.projectionLayerViews =
            XrCompositionLayerProjectionView.calloc(2);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            boolean imageAcquired = false;
            IntBuffer index = stack.callocInt(1);
            try {
                int error = XR10.xrAcquireSwapchainImage(
                    this.openxr.swapchain,
                    XrSwapchainImageAcquireInfo.calloc(stack).type$Default(),
                    index);
                this.openxr.logError(error, "xrAcquireSwapchainImage", "");
                if (error < 0) {
                    throw renderError(
                        "OpenXR swapchain acquire failed",
                        "VDXR could not provide a Vulkan presentation image");
                }
                imageAcquired = true;

                error = XR10.xrWaitSwapchainImage(
                    this.openxr.swapchain,
                    XrSwapchainImageWaitInfo.calloc(stack)
                        .type$Default()
                        .timeout(XR10.XR_INFINITE_DURATION));
                this.openxr.logError(error, "xrWaitSwapchainImage", "");
                if (error < 0) {
                    throw renderError(
                        "OpenXR swapchain wait failed",
                        "VDXR did not make the acquired Vulkan image writable");
                }

                this.swapIndex = index.get(0);
                if (this.swapIndex < 0 ||
                    this.swapIndex >= this.swapchainImages.length)
                {
                    throw renderError(
                        "Invalid OpenXR swapchain image index",
                        "VDXR returned image index " + this.swapIndex);
                }

                for (int viewIndex = 0; viewIndex < 2; viewIndex++) {
                    this.projectionLayerViews.get(viewIndex)
                        .type$Default()
                        .pose(this.openxr.viewBuffer.get(viewIndex).pose())
                        .fov(this.openxr.viewBuffer.get(viewIndex).fov())
                        .subImage()
                        .swapchain(this.openxr.swapchain)
                        .imageArrayIndex(viewIndex)
                        .imageRect()
                        .offset()
                        .set(0, 0);
                    this.projectionLayerViews.get(viewIndex)
                        .subImage()
                        .imageRect()
                        .extent()
                        .set(
                            VulkanXrBackend.EYE_WIDTH,
                            VulkanXrBackend.EYE_HEIGHT);
                }
                this.frameHasImage = true;
            } catch (RenderConfigException | RuntimeException failure) {
                if (imageAcquired) {
                    int releaseResult = XR10.xrReleaseSwapchainImage(
                        this.openxr.swapchain,
                        XrSwapchainImageReleaseInfo.calloc(stack)
                            .type$Default());
                    this.openxr.logError(
                        releaseResult,
                        "xrReleaseSwapchainImage",
                        "recover failed frame");
                    if (releaseResult < 0) {
                        failure.addSuppressed(
                            new IllegalStateException(
                                "Failed to release the acquired OpenXR " +
                                    "swapchain image: " + releaseResult));
                    }
                }
                this.projectionLayerViews.close();
                this.projectionLayerViews = null;
                this.swapIndex = -1;
                throw failure;
            }
        }
    }

    @Override
    public Matrix4f getProjectionMatrix(
        int eyeType, float nearClip, float farClip)
    {
        XrFovf fov = this.openxr.viewBuffer.get(eyeType).fov();
        return new Matrix4f().setPerspectiveOffCenterFov(
            fov.angleLeft(),
            fov.angleRight(),
            fov.angleDown(),
            fov.angleUp(),
            nearClip,
            farClip);
    }

    @Override
    public void endFrame() throws RenderConfigException {
        if (!this.openxr.frameBegun) {
            this.frameHasImage = false;
            this.swapIndex = -1;
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer layers = null;
            if (this.frameHasImage) {
                try {
                    this.copyStereoEyes();
                    this.openxr.device.getVulkanBackend().uploadStereo(
                        this.swapchainImages[this.swapIndex],
                        this.imageInitialized[this.swapIndex]);
                    this.imageInitialized[this.swapIndex][0] = true;
                    this.imageInitialized[this.swapIndex][1] = true;
                    if (!this.firstFrameLogged) {
                        this.firstFrameLogged = true;
                        VRSettings.LOGGER.info(
                            "GeneXR: first complete Vivecraft stereo frame submitted to VDXR");
                    }
                } finally {
                    int releaseResult = XR10.xrReleaseSwapchainImage(
                        this.openxr.swapchain,
                        XrSwapchainImageReleaseInfo.calloc(stack).type$Default());
                    this.openxr.logError(
                        releaseResult, "xrReleaseSwapchainImage", "");
                    if (releaseResult < 0) {
                        throw renderError(
                            "OpenXR swapchain release failed",
                            "VDXR rejected the completed Vulkan eye image");
                    }
                }

                XrCompositionLayerProjection layer =
                    XrCompositionLayerProjection.calloc(stack)
                        .type$Default()
                        .space(this.openxr.xrAppSpace)
                        .views(this.projectionLayerViews);
                layers = stack.pointers(layer.address());
            }

            XrFrameEndInfo frameEndInfo = XrFrameEndInfo.calloc(stack)
                .type$Default()
                .displayTime(this.openxr.time)
                .environmentBlendMode(
                    XR10.XR_ENVIRONMENT_BLEND_MODE_OPAQUE);
            if (layers != null) {
                frameEndInfo.layers(layers);
            }
            int error =
                XR10.xrEndFrame(this.openxr.session, frameEndInfo);
            this.openxr.frameBegun = false;
            this.openxr.logError(error, "xrEndFrame", "");
            if (error < 0) {
                throw renderError(
                    "OpenXR frame submission failed",
                    "VDXR rejected the completed stereo frame");
            }
        } finally {
            this.openxr.frameBegun = false;
            if (this.projectionLayerViews != null) {
                this.projectionLayerViews.close();
                this.projectionLayerViews = null;
            }
            this.frameHasImage = false;
            this.swapIndex = -1;
        }
    }

    private void copyStereoEyes()
        throws RenderConfigException
    {
        int previousReadFramebuffer =
            GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousPackAlignment =
            GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int previousPixelPackBuffer =
            GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            this.copyEyePixels(this.leftFramebuffer, 0);
            this.copyEyePixels(this.rightFramebuffer, 1);
        } finally {
            GL15.glBindBuffer(
                GL21.GL_PIXEL_PACK_BUFFER, previousPixelPackBuffer);
            GL11.glPixelStorei(
                GL11.GL_PACK_ALIGNMENT, previousPackAlignment);
            GL30.glBindFramebuffer(
                GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
        }
    }

    private void copyEyePixels(RenderTarget source, int arrayLayer)
        throws RenderConfigException
    {
        if (source == null) {
            throw renderError(
                "Missing Vivecraft eye framebuffer",
                arrayLayer == 0
                    ? "The left eye target was not created"
                    : "The right eye target was not created");
        }

        GL30.glBindFramebuffer(
            GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        int status =
            GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw renderError(
                "Incomplete Vivecraft eye framebuffer",
                "OpenGL returned framebuffer status 0x" +
                    Integer.toHexString(status));
        }

        ByteBuffer pixels =
            this.openxr.device.getVulkanBackend()
                .readbackBuffer(arrayLayer);
        GL11.glReadPixels(
            0,
            0,
            VulkanXrBackend.EYE_WIDTH,
            VulkanXrBackend.EYE_HEIGHT,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            pixels);
        if (!this.firstPixelCheckLogged && arrayLayer == 0) {
            this.firstPixelCheckLogged = true;
            int samples = 0;
            int coloredSamples = 0;
            int stride = 4096 * 4;
            for (int offset = 0;
                 offset + 2 < pixels.capacity();
                 offset += stride)
            {
                int red = pixels.get(offset) & 0xFF;
                int green = pixels.get(offset + 1) & 0xFF;
                int blue = pixels.get(offset + 2) & 0xFF;
                if ((red | green | blue) != 0) {
                    coloredSamples++;
                }
                samples++;
            }
            VRSettings.LOGGER.info(
                "GeneXR: first left-eye framebuffer contains color in {}/{} sampled pixels",
                coloredSamples,
                samples);
        }
    }

    private static RenderConfigException renderError(
        String title, String detail)
    {
        return new RenderConfigException(
            Component.literal(title), Component.literal(detail));
    }

    @Override
    public boolean providesStencilMask() {
        return false;
    }

    @Override
    public RenderTarget getLeftEyeTarget() {
        return this.leftFramebuffer;
    }

    @Override
    public RenderTarget getRightEyeTarget() {
        return this.rightFramebuffer;
    }

    @Override
    public String getName() {
        return "GeneXR Native OpenXR/VDXR";
    }

    @Override
    public Tuple<Integer, Integer> getRenderTextureSizes() {
        return new Tuple<>(
            VulkanXrBackend.EYE_WIDTH,
            VulkanXrBackend.EYE_HEIGHT);
    }

    @Override
    public void destroy() {
        super.destroy();
        if (this.leftFramebuffer != null) {
            this.leftFramebuffer.destroyBuffers();
            this.leftFramebuffer = null;
        }
        if (this.rightFramebuffer != null) {
            this.rightFramebuffer.destroyBuffers();
            this.rightFramebuffer = null;
        }
        this.swapchainImages = null;
        this.imageInitialized = null;
    }
}
