package top.yourzi.dialog.model;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 表示一个完整的对话序列，通常对应一个JSON文件。
 */
@Setter
@Getter
public class DialogSequence {
    // 对话序列的唯一标识符
    private String id;
    // 对话框类型 可选值：OVERLAY、SCREEN 默认为SCREEN
    private DialogType type;
    // 对话序列的标题
    private String title;
    // 对话序列的描述
    private String description;
    // 对话序列中的所有对话条目
    private DialogEntry[] entries;
    // 对话序列的起始对话ID，如果为空则从第一个条目开始
    @SerializedName("start")
    private String startId;
    // 是否允许通过ESC键关闭对话，默认为false
    @SerializedName("allowClose")
    private Boolean allowClose;

    /**
     * 检查是否允许关闭对话。
     * @return 如果允许关闭则返回true，否则返回false。默认为false。
     */
    public boolean isCloseAllowed() {
        return allowClose != null && allowClose;
    }

    public DialogType getType() {
        if (type == null) {
            type = DialogType.SCREEN;
        }
        return type;
    }

    /**
     * 获取对话序列的第一个对话条目。
     * @return 第一个对话条目；如果序列为空或未定义起始ID且无条目，则返回null。
     */
    public DialogEntry getFirstEntry() {
        if (entries == null || entries.length == 0) {
            return null;
        }
        
        if (startId != null && !startId.isEmpty()) {
            for (DialogEntry entry : entries) {
                if (startId.equals(entry.getId())) {
                    return entry;
                }
            }
        }
        
        return entries[0];
    }
    
    /**
     * 根据ID查找对话条目。
     * @param id 对话条目的ID。
     * @return 对应的对话条目；如果未找到，则返回null。
     */
    public DialogEntry findEntryById(String id) {
        if (id == null || id.isEmpty() || entries == null) {
            return null;
        }
        
        for (DialogEntry entry : entries) {
            if (id.equals(entry.getId())) {
                return entry;
            }
        }
        
        return null;
    }
    
    /**
     * 获取指定对话条目的下一个对话条目。
     * @param currentEntry 当前的对话条目。
     * @return 下一个对话条目；如果没有下一个条目，则返回null。
     */
    public DialogEntry getNextEntry(DialogEntry currentEntry) {
        if (currentEntry == null || entries == null || entries.length == 0) {
            return null;
        }
        
        // 如果当前对话指定了下一个对话的ID，则查找对应的对话条目
        if (currentEntry.getNextId() != null && !currentEntry.getNextId().isEmpty()) {
            return findEntryById(currentEntry.getNextId());
        }

        // 否则，按顺序查找下一个对话条目
        for (int i = 0; i < entries.length - 1; i++) {
            if (entries[i] == currentEntry) {
                return entries[i + 1];
            }
        }
        
        return null;
    }

    /**
     * 获取指定对话条目之后的所有对话条目（包含有指令的条目）。
     * @param currentEntry 当前的对话条目。
     * @return 后续所有对话条目的列表。
     */
    public List<DialogEntry> getRemainingEntries(DialogEntry currentEntry) {
        List<DialogEntry> remainingEntries = new ArrayList<>();
        if (currentEntry == null || entries == null || entries.length == 0) {
            return remainingEntries;
        }

        DialogEntry nextEntry = getNextEntry(currentEntry);
        while (nextEntry != null) {
            remainingEntries.add(nextEntry);
            nextEntry = getNextEntry(nextEntry);
        }
        
        return remainingEntries;
    }

    public enum DialogType {
        /**
         * 玩家HUD界面，不影响行动
         */
        OVERLAY,
        /**
         * 屏幕，影响行动
         */
        SCREEN
    }
}