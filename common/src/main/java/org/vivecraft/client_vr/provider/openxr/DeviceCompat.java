package org.vivecraft.client_vr.provider.openxr;

import org.lwjgl.openxr.KHRVulkanEnable2;
import org.lwjgl.openxr.XrInstance;
import org.lwjgl.openxr.XrSwapchainImageVulkanKHR;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Struct;
import org.lwjgl.system.Platform;
import org.vivecraft.client_vr.settings.VRSettings;

import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Windows graphics binding for ForkXR.
 *
 * <p>The Minecraft renderer remains OpenGL, but the OpenXR session itself is
 * intentionally Vulkan. No OpenGL graphics binding is ever presented to VDXR,
 * avoiding its Intel Arc external-memory import path.</p>
 */
public interface DeviceCompat {
    long getPlatformInfo(MemoryStack stack);

    void initOpenXRLoader(MemoryStack stack);

    String getGraphicsExtension();

    XrSwapchainImageVulkanKHR.Buffer createImageBuffers(
        int imageCount, MemoryStack stack);

    Struct checkGraphics(
        MemoryStack stack, XrInstance instance, long systemId);

    VulkanXrBackend getVulkanBackend();

    void closeGraphics();

    static DeviceCompat detectDevice() {
        if (Platform.get() != Platform.WINDOWS) {
            throw new IllegalStateException(
                "ForkXR supports the Windows VDXR runtime only");
        }
        return new Desktop();
    }

    final class Desktop implements DeviceCompat {
        private VulkanXrBackend backend;

        @Override
        public long getPlatformInfo(MemoryStack stack) {
            return NULL;
        }

        @Override
        public void initOpenXRLoader(MemoryStack stack) {
            VRSettings.LOGGER.info(
                "ForkXR: loading native Windows OpenXR through VDXR");
        }

        @Override
        public String getGraphicsExtension() {
            return KHRVulkanEnable2.XR_KHR_VULKAN_ENABLE2_EXTENSION_NAME;
        }

        @Override
        public XrSwapchainImageVulkanKHR.Buffer createImageBuffers(
            int imageCount, MemoryStack stack)
        {
            XrSwapchainImageVulkanKHR.Buffer images =
                XrSwapchainImageVulkanKHR.calloc(imageCount, stack);
            for (XrSwapchainImageVulkanKHR image : images) {
                image.type$Default();
            }
            return images;
        }

        @Override
        public Struct checkGraphics(
            MemoryStack stack, XrInstance instance, long systemId)
        {
            if (this.backend != null) {
                throw new IllegalStateException(
                    "The ForkXR Vulkan graphics device already exists");
            }
            this.backend =
                VulkanXrBackend.create(instance, systemId, stack);
            return this.backend.graphicsBinding(stack);
        }

        @Override
        public VulkanXrBackend getVulkanBackend() {
            if (this.backend == null) {
                throw new IllegalStateException(
                    "The ForkXR Vulkan graphics device is not initialized");
            }
            return this.backend;
        }

        @Override
        public void closeGraphics() {
            if (this.backend != null) {
                this.backend.close();
                this.backend = null;
            }
        }
    }
}
