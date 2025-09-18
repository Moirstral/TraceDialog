package top.yourzi.dialog.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import top.yourzi.dialog.model.DialogOption;

/**
 * 对话选项按钮
 */
public class OptionButton extends ImageButton {

    private final DialogOption.Align textAlign;

    private final DialogOption.Position padding;

    private final Tooltip tooltip;

    public OptionButton(int x, int y, int width, int height, WidgetSprites sprites, OnPress onPress, Component message, Tooltip tooltip, DialogOption.Align textAlign, DialogOption.Position padding) {
        super(x, y, width, height, sprites, onPress, message);
        this.tooltip = tooltip;
        this.textAlign = textAlign;
        this.padding = padding;
    }

    public OptionButton(int x, int y, int width, int height, WidgetSprites sprites, OnPress onPress, Component message) {
        this(x, y, width, height, sprites, onPress, message, null, DialogOption.Align.CENTER, DialogOption.Position.DEFAULT);
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTicks);
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        Component message = this.getMessage();
        if (message != Component.EMPTY) {
            int stringWidth = font.width(message);
            int textColor = this.active ? 0xFFFFFF : 0xA0A0A0;
            int textX = this.getX() + padding.left();
            int textY = this.getY() + (padding.top() == padding.bottom() ? (this.height - font.lineHeight) / 2 : padding.top());
            switch (textAlign) {
                case LEFT:
                    break;
                case CENTER:
                    textX = this.getX() + (this.width - stringWidth) / 2;
                    break;
                case RIGHT:
                    textX = this.getX() + this.width - stringWidth - padding.right();
                    break;
            }
            guiGraphics.drawString(font, message, textX, textY, textColor);
        }
        if (this.tooltip != null) {
            this.setTooltip(this.tooltip);
        }
    }

}