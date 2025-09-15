package top.yourzi.dialog.ui;


import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.model.PortraitAnimationType;
import top.yourzi.dialog.model.PortraitPosition;
import top.yourzi.dialog.util.STBBackendImage;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;

//存储每个立绘的显示数据
@Getter
public class PortraitDisplayData {
    private final static HashMap<ResourceLocation, BufferedImage> CACHED = new HashMap<>();
    ResourceLocation resourceLocation;
    int actualWidth;
    int actualHeight;
    float brightness = 1.0f;
    float size = 1.0f; // 立绘缩放大小
    PortraitPosition position;
    PortraitAnimationType animationType = PortraitAnimationType.NONE;
    @Setter
    long animationStartTime = -1;
    boolean loadedSuccessfully = false;

    public static void clearCache() {
        CACHED.clear();
        Dialog.LOGGER.info("Portrait cache cleared.");
    }

    public PortraitDisplayData(String path, float brightness, PortraitPosition position, PortraitAnimationType animationType, float size) {
        if (path != null && !path.isEmpty()) {
            this.resourceLocation = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, String.format("textures/portraits/%s", path));
            this.brightness = brightness;
            this.size = Math.max(0.0f, Math.min(5.0f, size)); // 限制范围在0-5之间
            this.position = position != null ? position : PortraitPosition.RIGHT; // 位置
            this.animationType = animationType != null ? animationType : PortraitAnimationType.NONE; // 动画类型
            loadDimensions();
            if (top.yourzi.dialog.config.ClientConfig.ENABLE_PORTRAIT_ANIMATIONS.get() && loadedSuccessfully && this.animationType != PortraitAnimationType.NONE) {
                this.animationStartTime = System.currentTimeMillis();
            }
        } else {
            Dialog.LOGGER.warn("Portrait path is null or empty. Cannot load portrait.");
        }
    }

    private void loadDimensions() {
        if (this.resourceLocation == null) return;
        var target_bufferedimage = CACHED.get(this.resourceLocation);
        if (target_bufferedimage == null) {
            try {
                Optional<Resource> resourceOptional = Minecraft.getInstance().getResourceManager().getResource(this.resourceLocation);
                if (resourceOptional.isPresent()) {
                    try (final var inputStream = resourceOptional.get().open()) {
                        target_bufferedimage = STBBackendImage.read(inputStream);
                        this.actualWidth = target_bufferedimage.getWidth();
                        this.actualHeight = target_bufferedimage.getHeight();
                        this.loadedSuccessfully = true;
                        CACHED.put(this.resourceLocation, target_bufferedimage);
                    }
                } else {
                    Dialog.LOGGER.warn("Portrait resource not found: {}.", this.resourceLocation);
                }
            } catch (IOException e) {
                Dialog.LOGGER.error("Error reading portrait image {}: {}.", this.resourceLocation, e.getMessage());
            } catch (Exception e) {
                Dialog.LOGGER.error("Unexpected error loading portrait image {}: {}.", this.resourceLocation, e.getMessage());
            }
        } else {
            this.actualWidth = target_bufferedimage.getWidth();
            this.actualHeight = target_bufferedimage.getHeight();
            this.loadedSuccessfully = true;
        }
    }
}
