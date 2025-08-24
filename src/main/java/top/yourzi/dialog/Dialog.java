package top.yourzi.dialog;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import top.yourzi.dialog.network.NetworkHandler;


@Mod(Dialog.MODID)
public class Dialog {
    public static final String MODID = "dialog";
    public static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings("removal")
    public Dialog(IEventBus modEventBus, ModContainer modContainer) {

        // 注册配置项
        modContainer.registerConfig(ModConfig.Type.CLIENT, top.yourzi.dialog.config.ClientConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, top.yourzi.dialog.config.ServerConfig.SPEC);

        // 初始化网络处理器
        modEventBus.addListener(NetworkHandler::init);

        // 注册 MinecraftForge 事件总线
        // modEventBus.register(this);
    }
}
