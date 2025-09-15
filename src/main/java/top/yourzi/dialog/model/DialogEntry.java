package top.yourzi.dialog.model;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 表示单条对话的数据模型。
 */
@Setter
@Getter
@Builder
public class DialogEntry {
    // 对话文本内容，可以是字符串或文本组件JSON对象
    private JsonElement text;
    // 说话者名称，可以是字符串或文本组件JSON对象
    private JsonElement speaker;
    // 立绘信息列表
    @SerializedName("portraits")
    private List<PortraitInfo> portraits;
    // 对话ID，用于跳转
    private String id;
    // 下一条对话的ID，如果为空则按顺序显示下一条
    @SerializedName("next")
    private String nextId;
    // 可选的对话选项
    private DialogOption[] options;
    // 用户选择的选项文本
    private String selectedOptionText;
    // 该对话条目完成后执行的命令
    @SerializedName("command") // 保持JSON字段名为command
    private List<String> commands;
    // 是否允许跳过此对话条目
    @SerializedName("allowSkip")
    private Boolean allowSkip;
    // 是否在此条对话后结束整个对话
    @SerializedName("endDialog")
    private Boolean endDialog;
    // 该对话条目的可见性命令
    @SerializedName("visibility_command")
    private String visibilityCommand;
    // 需要在对话中显示的物品列表
    @SerializedName("display_items")
    private List<DisplayItemInfo> displayItems;
    // 背景图片信息
    @SerializedName("background_image")
    private BackgroundImageInfo backgroundImage;
    // 背景图片信息
    @SerializedName("dialog_image")
    private String dialogImage;
    // 对话音频文件路径（相对于assets/dialog/sounds/目录）
    @SerializedName("audio")
    private String audioPath;

    /**
     * 检查是否允许跳过此对话条目
     *
     * @return 如果允许跳过返回true，否则返回false。如果未设置则默认为true
     */
    public boolean isSkipAllowed() {
        return allowSkip == null || allowSkip;
    }

    /**
     * 检查是否应该在此条对话后结束整个对话
     *
     * @return 如果应该结束对话返回true，否则返回false。如果未设置则默认为false
     */
    public boolean isEndDialog() {
        return endDialog != null && endDialog;
    }

    // 为了兼容旧的单个命令的JSON格式，添加一个辅助的setter
    public void setCommand(String command) {
        if (this.commands == null) {
            this.commands = new ArrayList<>();
        }
        this.commands.clear();
        this.commands.add(command);
    }

    // 提供一个获取单个命令的getter（如果只有一个命令），或者可以根据需要调整
    public String getCommand() {
        if (commands != null && !commands.isEmpty()) {
            return commands.get(0); // 返回第一个命令，或根据逻辑调整
        }
        return null;
    }

    public Component placeHolderReplace(String fromString, String toString, JsonElement targetElement, HolderLookup.Provider provider) {
        if (targetElement == null || targetElement.isJsonNull()) {
            return Component.empty();
        }
        String pString = (toString == null) ? "" : toString;

        if (targetElement.isJsonObject()) {
            JsonObject jsonObjectCopy = targetElement.getAsJsonObject().deepCopy();
            performDeepPlaceholderReplace(jsonObjectCopy, fromString, pString);

            Component componentAfterJsonProcessing;
            try {
                componentAfterJsonProcessing = Component.Serializer.fromJson(jsonObjectCopy, provider);
            } catch (JsonSyntaxException e) {
                try {
                    componentAfterJsonProcessing = Component.Serializer.fromJson(targetElement, provider);
                } catch (JsonSyntaxException e2) {
                    return Component.empty();
                }
            }
            return replaceTextInComponent(componentAfterJsonProcessing, fromString, pString);

        } else if (targetElement.isJsonArray()) {
            MutableComponent combinedText = Component.empty();
            JsonArray jsonArray = targetElement.getAsJsonArray();
            for (JsonElement element : jsonArray) {
                combinedText.append(placeHolderReplace(fromString, pString, element, provider));
            }
            return combinedText;
        } else if (targetElement.isJsonPrimitive() && targetElement.getAsJsonPrimitive().isString()) {
            return Component.literal(targetElement.getAsString().replace(fromString, pString));
        }

        try {
            Component component = Component.Serializer.fromJson(targetElement, provider);
            return replaceTextInComponent(component, fromString, pString);
        } catch (JsonSyntaxException e) {
            return Component.empty();
        }
    }

    private Component replaceTextInComponent(Component component, String placeholder, String replacement) {
        if (component == null) {
            return Component.empty();
        }

        MutableComponent newComponent = Component.empty();
        newComponent.setStyle(component.getStyle());

        component.visit((style, text) -> {
            String replacedText = text.replace(placeholder, replacement);
            newComponent.append(Component.literal(replacedText).setStyle(style));
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);

        return newComponent;
    }

    public Component getText(HolderLookup.Provider provider, String playerName) {
        return placeHolderReplace("@i", playerName, text, provider);
    }

    public Component getSpeaker(HolderLookup.Provider provider, String playerName) {
        return placeHolderReplace("@i", playerName, speaker, provider);
    }

    public boolean hasOptions() {
        return options != null && options.length > 0;
    }

    private boolean performDeepPlaceholderReplace(JsonObject jsonObject, String placeholder, String replacement) {
        boolean modified = false;
        for (Map.Entry<String, JsonElement> entry : new ArrayList<>(jsonObject.entrySet())) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();

            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String originalString = value.getAsString();
                String replacedString = originalString.replace(placeholder, replacement);
                if (!originalString.equals(replacedString)) {
                    jsonObject.addProperty(key, replacedString);
                    modified = true;
                }
            } else if (value.isJsonObject()) {
                if (performDeepPlaceholderReplace(value.getAsJsonObject(), placeholder, replacement)) {
                    modified = true;
                }
            } else if (value.isJsonArray()) {
                JsonArray jsonArray = value.getAsJsonArray();
                if (performDeepPlaceholderReplaceInArray(jsonArray, placeholder, replacement)) {
                    modified = true;
                }
            }
        }
        return modified;
    }

    private boolean performDeepPlaceholderReplaceInArray(JsonArray jsonArray, String placeholder, String replacement) {
        boolean overallArrayModified = false;
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonElement element = jsonArray.get(i);
            boolean elementModifiedInLoop = false;
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String originalString = element.getAsString();
                String replacedString = originalString.replace(placeholder, replacement);
                if (!originalString.equals(replacedString)) {
                    jsonArray.set(i, new JsonPrimitive(replacedString));
                    elementModifiedInLoop = true;
                }
            } else if (element.isJsonObject()) {
                JsonObject nestedObject = element.getAsJsonObject();
                if (performDeepPlaceholderReplace(nestedObject, placeholder, replacement)) {
                    elementModifiedInLoop = true;
                }
            } else if (element.isJsonArray()) {
                if (performDeepPlaceholderReplaceInArray(element.getAsJsonArray(), placeholder, replacement)) {
                    elementModifiedInLoop = true;
                }
            }
            if (elementModifiedInLoop) overallArrayModified = true;
        }
        return overallArrayModified;
    }
}