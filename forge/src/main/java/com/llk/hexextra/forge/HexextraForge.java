package com.llk.hexextra.forge;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.common.lib.HexRegistries;
import com.llk.hexextra.HexextraCommon;
import com.llk.hexextra.core.HexextraContent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge 侧的入口 —— 只做<b>注册胶水</b>,不含任何玩法逻辑。
 *
 * <h2>为什么内容定义不在这里</h2>
 * 见 {@link HexextraContent}:要注册什么、用什么 id 都在 common 里定义好了,
 * 这里只负责把这些定义喂给 Forge 的注册系统。Fabric 侧有一份对应的胶水,
 * 两边喂的是同一批内容 —— 这样就不会出现"一个 loader 上能用、另一个上少注册了东西"。
 *
 * <h2>三个注册表</h2>
 * {@link HexRegistries#IOTA_TYPE} / {@link HexRegistries#ARITHMETIC} / {@link HexRegistries#ACTION}
 * 都是咒法学自己的自定义注册表(键与 1.21.1 版一致)。Forge 的 {@code DeferredRegister}
 * 直接吃 {@code ResourceKey<Registry<T>>},所以不需要先拿到"活的"注册表实例 ——
 * 这一点比 Fabric 侧简单。
 *
 * <h2>注意包名</h2>
 * 这是 <b>Forge</b>(1.20.1),不是 NeoForge —— 所以是 {@code net.minecraftforge.*}。
 */
@Mod(HexextraCommon.MOD_ID)
public final class HexextraForge {

    private static final Logger LOGGER = LoggerFactory.getLogger("hexextra");

    private static final String MOD_ID = HexextraCommon.MOD_ID;

    /** iota 类型表。 */
    private static final DeferredRegister<IotaType<?>> IOTA_TYPES =
            DeferredRegister.create(HexRegistries.IOTA_TYPE, MOD_ID);

    /** 算术表。 */
    private static final DeferredRegister<Arithmetic> ARITHMETICS =
            DeferredRegister.create(HexRegistries.ARITHMETIC, MOD_ID);

    /** 续延帧类型表。 */
    private static final DeferredRegister<at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame.Type<?>> FRAME_TYPES =
            DeferredRegister.create(HexRegistries.CONTINUATION_TYPE, MOD_ID);

    /** 特殊处理器表。 */
    private static final DeferredRegister<at.petrak.hexcasting.api.casting.castables.SpecialHandler.Factory<?>> SPECIAL_HANDLERS =
            DeferredRegister.create(HexRegistries.SPECIAL_HANDLER, MOD_ID);

    /** 图案(动作)表。 */
    private static final DeferredRegister<ActionRegistryEntry> ACTIONS =
            DeferredRegister.create(HexRegistries.ACTION, MOD_ID);

    /**
     * 无参构造 —— Forge 1.20.1 的 {@code @Mod} 类必须提供它。
     *
     * <p>注意:{@code IEventBus} 参数的构造器注入是 <b>NeoForge 1.21</b> 的写法,
     * 在 Forge 1.20.1 上不行(FML 只会去找无参构造,找不到就抛
     * {@code NoSuchMethodException})。1.20.1 要从 {@link FMLJavaModLoadingContext} 取总线。
     */
    public HexextraForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        // 逐条把 common 里的定义登记进来。用 id 的 path 部分作为注册名
        // (例如 hexextra:switch → 路径 "switch")。
        for (HexextraContent.Entry<IotaType<?>> entry : HexextraContent.iotaTypes()) {
            IOTA_TYPES.register(entry.id().getPath(), entry::value);
        }
        for (HexextraContent.Entry<Arithmetic> entry : HexextraContent.arithmetics()) {
            ARITHMETICS.register(entry.id().getPath(), entry::value);
        }
        for (HexextraContent.Entry<at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame.Type<?>> entry : HexextraContent.frameTypes()) {
            FRAME_TYPES.register(entry.id().getPath(), entry::value);
        }
        for (HexextraContent.Entry<at.petrak.hexcasting.api.casting.castables.SpecialHandler.Factory<?>> entry : HexextraContent.specialHandlers()) {
            SPECIAL_HANDLERS.register(entry.id().getPath(), entry::value);
        }
        for (HexextraContent.Entry<ActionRegistryEntry> entry : HexextraContent.actions()) {
            ACTIONS.register(entry.id().getPath(), entry::value);
        }

        // DeferredRegister 必须挂到 mod 事件总线上,否则永远不会真正注册
        IOTA_TYPES.register(modEventBus);
        FRAME_TYPES.register(modEventBus);
        ARITHMETICS.register(modEventBus);
        SPECIAL_HANDLERS.register(modEventBus);
        ACTIONS.register(modEventBus);

        LOGGER.info("hexextra: DeferredRegister 已挂上事件总线 —— iota 类型 3、续延帧 2、算术 2、图案 4");
    }
}
