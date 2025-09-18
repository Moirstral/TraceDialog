package top.yourzi.dialog.model;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * 表示对话中的选项。
 */
@Getter
@Setter
@Builder
public class DialogOption {
    // 选项显示的文本，可以是字符串或文本组件JSON对象
    private JsonElement text;

    // 相对位置 LEFT, RIGHT, CENTER, 默认为 CENTER
    @SerializedName("text_align")
    private Align textAlign;

    // 背景图片 path
    private String background;

    private JsonElement tooltips;

    // 选择此选项后跳转到的对话ID
    @SerializedName("target")
    private String targetId;

    // 选择该选项后执行的命令
    private List<String> command;

    // 控制该选项是否可见的指令
    @SerializedName("visibility_command")
    private String visibilityCommand;

    // 宽度固定值 大于0，此项优先于 widthPercentage
    private Integer width;

    // 宽度占比
    private Float widthPercentage;

    // 高度 固定值 大于0
    private Integer height;

    // 相对位置 LEFT, RIGHT, CENTER, 默认为 CENTER
    private Align align;

    // 外间距
    private Position margin;

    // 内间距
    private Position padding;

    // 缓存的文本组件
    private transient Component cachedTextComponent;

    public Component getText(HolderLookup.Provider provider, String playerName) {
        if (text == null) return Component.empty();
        return placeHolderReplace("@i", playerName, this.text, provider);
    }

    public void setText(JsonElement text) {
        this.text = text;
        this.cachedTextComponent = null; // 重置缓存
    }

    public Tooltip getTooltips(HolderLookup.Provider provider, String playerName) {
        if (tooltips == null || tooltips.isJsonNull()) return null;
        return Tooltip.create(placeHolderReplace("@i", playerName, tooltips, provider));
    }

    public Align getAlign() {
        return align == null ? Align.CENTER : align;
    }

    public void setAlignIfNull(Align align) {
        if (this.align == null) {
            this.align = align;
        }
    }

    public Align getTextAlign() {
        return textAlign == null ? Align.CENTER : textAlign;
    }

    public Position getPadding() {
        return padding == null ? Position.DEFAULT : padding;
    }

    public Position getMargin() {
        return margin == null ? Position.DEFAULT : margin;
    }

    public int getWidth(int dialogBoxWidth) {
        if (width != null) {
            return width;
        }
        if (widthPercentage != null) {
            return (int) (widthPercentage * dialogBoxWidth);
        }
        return 20;
    }

    public Float getWidthPercentage() {
        return width == null ? widthPercentage : null;
    }

    public Integer getHeight() {
        return height == null ? 20 : Math.abs(height);
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
            return java.util.Optional.empty(); // Continue visitation
        }, net.minecraft.network.chat.Style.EMPTY);

        return newComponent;
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
                    componentAfterJsonProcessing = Component.Serializer.fromJson(targetElement, provider); // Fallback to original
                } catch (JsonSyntaxException e2) {
                    return Component.empty(); // Both failed
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

    @JsonAdapter(Position.Adapter.class)
    public record Position(int left, int top, int right, int bottom) {

        public static final Position DEFAULT = new Position(5);

        public Position(int position) {
            this(position, position, position, position);
        }

        public Position(int left, int top) {
            this(left, top, left, top);
        }

        public Position(int left, int top, int right) {
            this(left, top, right, top);
        }

        public static class Adapter extends TypeAdapter<Position> {
            @Override
            public void write(JsonWriter out, Position value) throws IOException {
                if (value == null) return;
                if (value.left == value.right && value.top == value.bottom) {
                    if (value.left == value.top) {
                        if (value.left == DEFAULT.left) {
                            // 默认值不写入
                            out.nullValue();
                        } else {
                            out.value(value.left);
                        }
                    } else {
                        out.value(value.left + " " + value.top);
                    }
                } else if (value.top == value.bottom) {
                    out.value(value.left + " " + value.top + " " + value.right);
                } else {
                    out.value(value.left + " " + value.top + " " + value.right + " " + value.bottom);
                }
            }

            @Override
            public Position read(JsonReader in) throws IOException {
                JsonToken token = in.peek();
                if (token == JsonToken.NULL) {
                    in.nextNull();
                    return null;
                } else if (token == JsonToken.NUMBER) {
                    return new Position(in.nextInt());
                } else if (token == JsonToken.STRING) {
                    String[] split = in.nextString().split(" ");
                    return switch (split.length) {
                        case 1 -> new Position(Integer.parseInt(split[0]));
                        case 2 -> new Position(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
                        case 3 ->
                                new Position(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
                        case 4 ->
                                new Position(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]), Integer.parseInt(split[3]));
                        default -> throw new JsonParseException("Invalid position format");
                    };
                } else if (token == JsonToken.BEGIN_OBJECT) {
                    JsonObject obj = JsonParser.parseReader(in).getAsJsonObject();
                    int left = obj.has("left") ? obj.get("left").getAsInt() : 0;
                    int top = obj.has("top") ? obj.get("top").getAsInt() : 0;
                    int right = obj.has("right") ? obj.get("right").getAsInt() : 0;
                    int bottom = obj.has("bottom") ? obj.get("bottom").getAsInt() : 0;
                    return new Position(left, top, right, bottom);
                } else if (token == JsonToken.BEGIN_ARRAY) {
                    JsonArray array = JsonParser.parseReader(in).getAsJsonArray();
                    return switch (array.size()) {
                        case 1 -> new Position(array.get(0).getAsInt());
                        case 2 -> new Position(array.get(0).getAsInt(), array.get(1).getAsInt());
                        case 3 ->
                                new Position(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
                        case 4 ->
                                new Position(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt(), array.get(3).getAsInt());
                        default -> throw new JsonParseException("Invalid position array format");
                    };
                }
                throw new JsonParseException("Invalid position format");
            }
        }
    }

    public enum Align {
        LEFT,
        CENTER,
        RIGHT
    }
}