package org.vivecraft.client_vr.provider.openxr;

import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.XrGraphicsBindingVulkan2KHR;
import org.lwjgl.openxr.XrGraphicsRequirementsVulkan2KHR;
import org.lwjgl.openxr.XrInstance;
import org.lwjgl.openxr.XrVulkanDeviceCreateInfoKHR;
import org.lwjgl.openxr.XrVulkanGraphicsDeviceGetInfoKHR;
import org.lwjgl.openxr.XrVulkanInstanceCreateInfoKHR;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.vivecraft.client_vr.settings.VRSettings;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.openxr.KHRVulkanEnable2.xrCreateVulkanDeviceKHR;
import static org.lwjgl.openxr.KHRVulkanEnable2.xrCreateVulkanInstanceKHR;
import static org.lwjgl.openxr.KHRVulkanEnable2.xrGetVulkanGraphicsDevice2KHR;
import static org.lwjgl.openxr.KHRVulkanEnable2.xrGetVulkanGraphicsRequirements2KHR;
import static org.lwjgl.openxr.XR10.XR_FAILED;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Owns the Vulkan device used only for native OpenXR presentation.
 *
 * <p>Minecraft and Vivecraft continue rendering through ordinary OpenGL. Eye
 * pixels are copied through coherent host memory into VDXR-owned Vulkan
 * swapchain images. This deliberately avoids the OpenGL external-memory import
 * path that fails in VDXR on Intel Arc A-series drivers.</p>
 */
final class VulkanXrBackend implements AutoCloseable {
    static final int EYE_WIDTH = 1832;
    static final int EYE_HEIGHT = 1920;
    static final int FORMAT = VK_FORMAT_R8G8B8A8_SRGB;
    private static final int EYE_PIXEL_BYTES =
        EYE_WIDTH * EYE_HEIGHT * 4;
    private static final int FRAME_PIXEL_BYTES = EYE_PIXEL_BYTES * 2;

    private final VkInstance instance;
    private final VkPhysicalDevice physicalDevice;
    private final VkDevice device;
    private final VkQueue queue;
    private final int queueFamily;
    private final long commandPool;
    private final VkCommandBuffer commandBuffer;
    private final long uploadFence;
    private final long stagingBuffer;
    private final long stagingMemory;
    private final ByteBuffer mappedStaging;
    private final ByteBuffer readback;
    private final ByteBuffer[] readbackViews;
    private boolean closed;

    private VulkanXrBackend(
        VkInstance instance,
        VkPhysicalDevice physicalDevice,
        VkDevice device,
        VkQueue queue,
        int queueFamily,
        long commandPool,
        VkCommandBuffer commandBuffer,
        long uploadFence,
        long stagingBuffer,
        long stagingMemory,
        ByteBuffer mappedStaging,
        ByteBuffer readback)
    {
        this.instance = instance;
        this.physicalDevice = physicalDevice;
        this.device = device;
        this.queue = queue;
        this.queueFamily = queueFamily;
        this.commandPool = commandPool;
        this.commandBuffer = commandBuffer;
        this.uploadFence = uploadFence;
        this.stagingBuffer = stagingBuffer;
        this.stagingMemory = stagingMemory;
        this.mappedStaging = mappedStaging;
        this.readback = readback;
        this.readbackViews = new ByteBuffer[] {
            slice(readback, 0, EYE_PIXEL_BYTES),
            slice(readback, EYE_PIXEL_BYTES, FRAME_PIXEL_BYTES)
        };
    }

    static VulkanXrBackend create(
        XrInstance xrInstance, long systemId, MemoryStack stack)
    {
        if (VK.getFunctionProvider() == null) {
            VK.create();
        }
        long getInstanceProcAddr =
            VK.getFunctionProvider().getFunctionAddress("vkGetInstanceProcAddr");
        if (getInstanceProcAddr == 0L) {
            throw new IllegalStateException("The Windows Vulkan loader is unavailable");
        }

        XrGraphicsRequirementsVulkan2KHR requirements =
            XrGraphicsRequirementsVulkan2KHR.calloc(stack).type$Default();
        checkXr(
            xrGetVulkanGraphicsRequirements2KHR(xrInstance, systemId, requirements),
            "xrGetVulkanGraphicsRequirements2KHR");

        int requestedApi = VK_MAKE_API_VERSION(0, 1, 1, 0);
        long minimumApi = requirements.minApiVersionSupported();
        long maximumApi = requirements.maxApiVersionSupported();
        long requestedXrApi = org.lwjgl.openxr.XR10.XR_MAKE_VERSION(1, 1, 0);
        if (requestedXrApi < minimumApi || requestedXrApi > maximumApi) {
            throw new IllegalStateException(
                "VDXR does not accept the required Vulkan 1.1 graphics API");
        }

        VkApplicationInfo applicationInfo = VkApplicationInfo.calloc(stack)
            .sType$Default()
            .pApplicationName(stack.UTF8("ForkXR"))
            .applicationVersion(VK_MAKE_VERSION(10, 0, 0))
            .pEngineName(stack.UTF8("Minecraft Vivecraft OpenXR"))
            .engineVersion(VK_MAKE_VERSION(1, 20, 4))
            .apiVersion(requestedApi);
        VkInstanceCreateInfo instanceInfo = VkInstanceCreateInfo.calloc(stack)
            .sType$Default()
            .pApplicationInfo(applicationInfo);
        XrVulkanInstanceCreateInfoKHR xrInstanceInfo =
            XrVulkanInstanceCreateInfoKHR.calloc(stack)
                .type$Default()
                .systemId(systemId)
                .pfnGetInstanceProcAddr(getInstanceProcAddr)
                .vulkanCreateInfo(instanceInfo);

        PointerBuffer instanceHandle = stack.mallocPointer(1);
        IntBuffer vkResult = stack.mallocInt(1);
        checkXr(
            xrCreateVulkanInstanceKHR(
                xrInstance, xrInstanceInfo, instanceHandle, vkResult),
            "xrCreateVulkanInstanceKHR");
        checkVk(vkResult.get(0), "vkCreateInstance through OpenXR");
        VkInstance vkInstance = new VkInstance(instanceHandle.get(0), instanceInfo);

        XrVulkanGraphicsDeviceGetInfoKHR graphicsDeviceInfo =
            XrVulkanGraphicsDeviceGetInfoKHR.calloc(stack)
                .type$Default()
                .systemId(systemId)
                .vulkanInstance(vkInstance);
        PointerBuffer physicalDeviceHandle = stack.mallocPointer(1);
        checkXr(
            xrGetVulkanGraphicsDevice2KHR(
                xrInstance, graphicsDeviceInfo, physicalDeviceHandle),
            "xrGetVulkanGraphicsDevice2KHR");
        VkPhysicalDevice physicalDevice =
            new VkPhysicalDevice(physicalDeviceHandle.get(0), vkInstance);
        int queueFamily = findGraphicsQueue(physicalDevice, stack);

        VkDeviceQueueCreateInfo.Buffer queueInfo =
            VkDeviceQueueCreateInfo.calloc(1, stack);
        queueInfo.get(0)
            .sType$Default()
            .queueFamilyIndex(queueFamily)
            .pQueuePriorities(stack.floats(1.0F));
        VkDeviceCreateInfo deviceInfo = VkDeviceCreateInfo.calloc(stack)
            .sType$Default()
            .pQueueCreateInfos(queueInfo);
        XrVulkanDeviceCreateInfoKHR xrDeviceInfo =
            XrVulkanDeviceCreateInfoKHR.calloc(stack)
                .type$Default()
                .systemId(systemId)
                .pfnGetInstanceProcAddr(getInstanceProcAddr)
                .vulkanPhysicalDevice(physicalDevice)
                .vulkanCreateInfo(deviceInfo);

        PointerBuffer deviceHandle = stack.mallocPointer(1);
        vkResult.put(0, VK_SUCCESS);
        checkXr(
            xrCreateVulkanDeviceKHR(
                xrInstance, xrDeviceInfo, deviceHandle, vkResult),
            "xrCreateVulkanDeviceKHR");
        checkVk(vkResult.get(0), "vkCreateDevice through OpenXR");
        VkDevice device = new VkDevice(deviceHandle.get(0), physicalDevice, deviceInfo);

        PointerBuffer queueHandle = stack.mallocPointer(1);
        vkGetDeviceQueue(device, queueFamily, 0, queueHandle);
        VkQueue queue = new VkQueue(queueHandle.get(0), device);

        LongBuffer longHandle = stack.mallocLong(1);
        checkVk(
            vkCreateCommandPool(
                device,
                VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .queueFamilyIndex(queueFamily)
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT),
                null,
                longHandle),
            "vkCreateCommandPool");
        long commandPool = longHandle.get(0);

        PointerBuffer commandHandle = stack.mallocPointer(1);
        checkVk(
            vkAllocateCommandBuffers(
                device,
                VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1),
                commandHandle),
            "vkAllocateCommandBuffers");
        VkCommandBuffer commandBuffer =
            new VkCommandBuffer(commandHandle.get(0), device);

        checkVk(
            vkCreateFence(
                device,
                VkFenceCreateInfo.calloc(stack).sType$Default(),
                null,
                longHandle),
            "vkCreateFence(upload)");
        long uploadFence = longHandle.get(0);

        checkVk(
            vkCreateBuffer(
                device,
                VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(FRAME_PIXEL_BYTES)
                    .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE),
                null,
                longHandle),
            "vkCreateBuffer(staging)");
        long stagingBuffer = longHandle.get(0);

        VkMemoryRequirements memoryRequirements = VkMemoryRequirements.malloc(stack);
        vkGetBufferMemoryRequirements(device, stagingBuffer, memoryRequirements);
        int memoryType = findMemoryType(
            physicalDevice,
            memoryRequirements.memoryTypeBits(),
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            stack);
        checkVk(
            vkAllocateMemory(
                device,
                VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memoryRequirements.size())
                    .memoryTypeIndex(memoryType),
                null,
                longHandle),
            "vkAllocateMemory(staging)");
        long stagingMemory = longHandle.get(0);
        checkVk(
            vkBindBufferMemory(device, stagingBuffer, stagingMemory, 0),
            "vkBindBufferMemory(staging)");

        PointerBuffer mappedPointer = stack.mallocPointer(1);
        checkVk(
            vkMapMemory(
                device, stagingMemory, 0, FRAME_PIXEL_BYTES, 0, mappedPointer),
            "vkMapMemory(staging)");
        ByteBuffer mappedStaging =
            MemoryUtil.memByteBuffer(
                mappedPointer.get(0), FRAME_PIXEL_BYTES);
        ByteBuffer readback = MemoryUtil.memAlloc(FRAME_PIXEL_BYTES);

        VRSettings.LOGGER.info(
            "ForkXR: initialized the Intel Arc-safe Vulkan OpenXR bridge");
        return new VulkanXrBackend(
            vkInstance,
            physicalDevice,
            device,
            queue,
            queueFamily,
            commandPool,
            commandBuffer,
            uploadFence,
            stagingBuffer,
            stagingMemory,
            mappedStaging,
            readback);
    }

    XrGraphicsBindingVulkan2KHR graphicsBinding(MemoryStack stack) {
        return XrGraphicsBindingVulkan2KHR.calloc(stack)
            .type$Default()
            .instance(this.instance)
            .physicalDevice(this.physicalDevice)
            .device(this.device)
            .queueFamilyIndex(this.queueFamily)
            .queueIndex(0);
    }

    ByteBuffer readbackBuffer(int arrayLayer) {
        if (arrayLayer < 0 || arrayLayer > 1) {
            throw new IllegalArgumentException(
                "Invalid stereo array layer " + arrayLayer);
        }
        return this.readbackViews[arrayLayer];
    }

    private static ByteBuffer slice(
        ByteBuffer buffer, int start, int end)
    {
        ByteBuffer view = buffer.duplicate();
        view.position(start);
        view.limit(end);
        return view.slice();
    }

    void uploadStereo(
        long image, boolean[] initializedLayers)
    {
        if (initializedLayers == null ||
            initializedLayers.length != 2)
        {
            throw new IllegalArgumentException(
                "Stereo initialization state must contain two layers");
        }

        this.mappedStaging.clear();
        int rowBytes = EYE_WIDTH * 4;
        long source = MemoryUtil.memAddress(this.readback);
        long destination = MemoryUtil.memAddress(this.mappedStaging);
        for (int layer = 0; layer < 2; layer++) {
            long layerOffset = (long) layer * EYE_PIXEL_BYTES;
            for (int row = 0; row < EYE_HEIGHT; row++) {
                long sourceRow = source + layerOffset +
                    (long) (EYE_HEIGHT - 1 - row) * rowBytes;
                long destinationRow =
                    destination + layerOffset + (long) row * rowBytes;
                MemoryUtil.memCopy(
                    sourceRow, destinationRow, rowBytes);
            }
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            checkVk(
                vkResetCommandBuffer(this.commandBuffer, 0),
                "vkResetCommandBuffer");
            checkVk(
                vkBeginCommandBuffer(
                    this.commandBuffer,
                    VkCommandBufferBeginInfo.calloc(stack)
                        .sType$Default()
                        .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)),
                "vkBeginCommandBuffer");

            VkImageMemoryBarrier.Buffer toTransfer =
                VkImageMemoryBarrier.calloc(2, stack);
            for (int layer = 0; layer < 2; layer++) {
                final int layerIndex = layer;
                boolean initialized = initializedLayers[layer];
                toTransfer.get(layer)
                    .sType$Default()
                    .oldLayout(
                        initialized
                            ? VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
                            : VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image)
                    .srcAccessMask(
                        initialized
                            ? VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                            : 0)
                    .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .subresourceRange(range -> range
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(layerIndex)
                        .layerCount(1));
            }
            vkCmdPipelineBarrier(
                this.commandBuffer,
                initializedLayers[0] || initializedLayers[1]
                    ? VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                    : VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                0,
                null,
                null,
                toTransfer);

            VkBufferImageCopy.Buffer region =
                VkBufferImageCopy.calloc(2, stack);
            for (int layer = 0; layer < 2; layer++) {
                final int layerIndex = layer;
                region.get(layer)
                    .bufferOffset((long) layer * EYE_PIXEL_BYTES)
                    .bufferRowLength(0)
                    .bufferImageHeight(0)
                    .imageSubresource(subresource -> subresource
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .mipLevel(0)
                        .baseArrayLayer(layerIndex)
                        .layerCount(1))
                    .imageOffset(offset -> offset.set(0, 0, 0))
                    .imageExtent(
                        extent -> extent.set(
                            EYE_WIDTH, EYE_HEIGHT, 1));
            }
            vkCmdCopyBufferToImage(
                this.commandBuffer,
                this.stagingBuffer,
                image,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                region);

            VkImageMemoryBarrier.Buffer toPresent =
                VkImageMemoryBarrier.calloc(2, stack);
            for (int layer = 0; layer < 2; layer++) {
                final int layerIndex = layer;
                toPresent.get(layer)
                    .sType$Default()
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(
                        VK_ACCESS_COLOR_ATTACHMENT_READ_BIT |
                            VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                    .subresourceRange(range -> range
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(layerIndex)
                        .layerCount(1));
            }
            vkCmdPipelineBarrier(
                this.commandBuffer,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                0,
                null,
                null,
                toPresent);

            checkVk(vkEndCommandBuffer(this.commandBuffer), "vkEndCommandBuffer");
            VkSubmitInfo.Buffer submitInfo = VkSubmitInfo.calloc(1, stack);
            submitInfo.get(0)
                .sType$Default()
                .pCommandBuffers(stack.pointers(this.commandBuffer.address()));
            LongBuffer uploadFenceBuffer = stack.longs(this.uploadFence);
            checkVk(
                vkResetFences(this.device, uploadFenceBuffer),
                "vkResetFences(upload)");
            checkVk(
                vkQueueSubmit(this.queue, submitInfo, this.uploadFence),
                "vkQueueSubmit");
            checkVk(
                vkWaitForFences(
                    this.device,
                    uploadFenceBuffer,
                    true,
                    Long.MAX_VALUE),
                "vkWaitForFences(upload)");
        }
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        vkDeviceWaitIdle(this.device);
        vkUnmapMemory(this.device, this.stagingMemory);
        vkDestroyBuffer(this.device, this.stagingBuffer, null);
        vkFreeMemory(this.device, this.stagingMemory, null);
        vkDestroyFence(this.device, this.uploadFence, null);
        vkDestroyCommandPool(this.device, this.commandPool, null);
        vkDestroyDevice(this.device, null);
        vkDestroyInstance(this.instance, null);
        MemoryUtil.memFree(this.readback);
    }

    private static int findGraphicsQueue(
        VkPhysicalDevice physicalDevice, MemoryStack stack)
    {
        IntBuffer count = stack.mallocInt(1);
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, count, null);
        VkQueueFamilyProperties.Buffer families =
            VkQueueFamilyProperties.calloc(count.get(0), stack);
        vkGetPhysicalDeviceQueueFamilyProperties(
            physicalDevice, count, families);
        for (int i = 0; i < families.capacity(); i++) {
            if ((families.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                return i;
            }
        }
        throw new IllegalStateException(
            "The VDXR-selected Vulkan device has no graphics queue");
    }

    private static int findMemoryType(
        VkPhysicalDevice physicalDevice,
        int allowedTypes,
        int requiredFlags,
        MemoryStack stack)
    {
        VkPhysicalDeviceMemoryProperties properties =
            VkPhysicalDeviceMemoryProperties.malloc(stack);
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, properties);
        for (int i = 0; i < properties.memoryTypeCount(); i++) {
            int flags = properties.memoryTypes(i).propertyFlags();
            if ((allowedTypes & (1 << i)) != 0 &&
                (flags & requiredFlags) == requiredFlags)
            {
                return i;
            }
        }
        throw new IllegalStateException(
            "No coherent host-visible Vulkan staging memory is available");
    }

    private static void checkXr(int result, String operation) {
        if (XR_FAILED(result)) {
            throw new IllegalStateException(
                operation + " failed with OpenXR result " + result);
        }
    }

    private static void checkVk(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(
                operation + " failed with Vulkan result " + result);
        }
    }
}
