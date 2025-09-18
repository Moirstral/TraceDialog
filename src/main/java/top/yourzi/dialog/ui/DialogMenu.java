package top.yourzi.dialog.ui;

import net.minecraft.client.gui.GuiGraphics;
import top.yourzi.dialog.config.ClientConfig;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogSequence;

/**
 * 对话界面，用于显示对话框和立绘
 */
public class DialogMenu extends DialogScreen {
    public DialogMenu(DialogSequence dialogSequence, DialogEntry dialogEntry, String playerName) {
        this(dialogSequence, dialogEntry, playerName, null);
    }

    public DialogMenu(DialogSequence dialogSequence, DialogEntry dialogEntry, String playerName, net.minecraft.world.entity.Entity speakerEntity) {
        super(dialogSequence, dialogEntry, playerName, speakerEntity);
        if (dialogEntry.getDialogImage() != null && !dialogEntry.getDialogImage().isEmpty()) {
            this.dialogBackgroundImagePath = dialogEntry.getDialogImage();
        } else {
            this.dialogBackgroundImagePath = null;
        }
        this.showOptionsNow = true;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected void initSize() {
        // 使用覆层的配置
        dialogBoxWidth = ClientConfig.OVERLAY_DIALOG_BOX_WIDTH.get();
        dialogBoxHeight = ClientConfig.OVERLAY_DIALOG_BOX_HEIGHT.get();
        int dialogBoxOffset = ClientConfig.OVERLAY_DIALOG_BOX_OFFSETY.get();
        dialogBoxX = (width - dialogBoxWidth) / 2;
        dialogBoxY = height - dialogBoxHeight - dialogBoxOffset;
    }

    @Override
    protected void initButtons() {
        // 没有按钮
    }

    @Override
    public void toggleHistoryScreen() {
        this.showingHistory = false;
    }

    @Override
    protected void renderHistoryScreen(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 没有历史记录页面
    }

    @Override
    protected void preClose() {
        // 直接关闭，无需确认
        executeRemainingCommandsAndClose();
    }
}