package top.yourzi.dialog.ui;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.model.BackgroundAnimationType;
import top.yourzi.dialog.model.BackgroundImageInfo;
import top.yourzi.dialog.model.BackgroundRenderOption;
import top.yourzi.dialog.util.STBBackendImage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

// 管理背景图片显示数据
@Getter
public class BackgroundImageDisplayData {
    protected final ResourceLocation imageLocation;
    protected final BackgroundRenderOption renderOption;
    protected final BackgroundAnimationType animationType;
    protected STBBackendImage image;
    protected boolean loadedSuccessfully = false;
    protected int imageWidth;
    protected int imageHeight;
    protected long animationStartTime = -1;

    public BackgroundImageDisplayData(BackgroundImageInfo backgroundImageInfo) {
        this.imageLocation = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "textures/backgrounds/" + backgroundImageInfo.getPath());
        this.renderOption = backgroundImageInfo.getRenderOption();
        this.animationType = backgroundImageInfo.getAnimationType() != null ? backgroundImageInfo.getAnimationType() : BackgroundAnimationType.NONE;
        loadResource();
    }

    private void loadResource() {
        try {
            Optional<Resource> resourceOptional = Minecraft.getInstance().getResourceManager().getResource(imageLocation);
            if (resourceOptional.isPresent()) {
                try (InputStream inputStream = resourceOptional.get().open()) {
                    this.image = STBBackendImage.read(inputStream);
                    this.imageWidth = image.getWidth();
                    this.imageHeight = image.getHeight();
                    this.loadedSuccessfully = true;
                } catch (IOException e) {
                    Dialog.LOGGER.error("Failed to load background image: {}", imageLocation, e);
                }
            } else {
                Dialog.LOGGER.warn("Background image resource not found: {}", imageLocation);
            }
        } catch (Exception e) {
            Dialog.LOGGER.error("Error accessing background image resource: {}", imageLocation, e);
        }
    }

}
