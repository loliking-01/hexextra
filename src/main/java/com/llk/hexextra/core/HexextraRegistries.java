package com.llk.hexextra.core;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic;
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.common.lib.HexRegistries;
import com.llk.hexextra.Hexextra;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * hexextra 用到的所有注册表集中在这里,不按功能细分(对应规则 5 里的 {@code core})。
 *
 * <p>这里只放「注册表本体」,不放具体条目 —— 每个功能的注册条目由该功能自己的文件夹负责,
 * 这样新增功能时只需要动功能目录,不会让这个文件无限膨胀。
 */
public final class HexextraRegistries {

    /**
     * 咒法学的「图案 → 操作符」注册表。
     *
     * <p>往这里注册一条 {@link ActionRegistryEntry},玩家在游戏里画出对应图案时就会调用它绑定的操作符。
     * 游戏启动时会扫描这个注册表,把「图案签名 → 操作符」缓存成一张表。
     */
    public static final DeferredRegister<ActionRegistryEntry> ACTIONS =
            DeferredRegister.create(HexRegistries.ACTION, Hexextra.MODID);

    /**
     * 咒法学的「续延帧类型」注册表。
     *
     * <p>这是本模组能实现「循环」的关键:咒法学的施法 VM 靠一串续延帧推进计算,
     * 而自定义帧必须在这里登记类型,否则 VM 状态在做 NBT/网络序列化时会找不到对应编码器,
     * 客户端同步时直接报错。
     *
     * <p>之所以附属模组能往这里注册:咒法学是用 {@code NewRegistryEvent} 把这些注册表
     * 建为普通静态注册表的,所以标准的 {@link DeferredRegister} 就能写入。
     */
    public static final DeferredRegister<ContinuationFrame.Type<?>> CONTINUATION_TYPES =
            DeferredRegister.create(HexRegistries.CONTINUATION_TYPE, Hexextra.MODID);

    /**
     * 咒法学的「iota 类型」注册表。
     *
     * <p>自定义的 iota 必须在这里登记类型,否则:存档里写不进 NBT、网络同步会字节错位,
     * 而且游戏也认不出这个 iota 属于谁。
     *
     * <p>注意这是一张<b>同步注册表</b>(咒法学用 {@code sync(true)} 建的),
     * 所以客户端必须装同一个附属,否则两边 id 对不上。
     */
    public static final DeferredRegister<IotaType<?>> IOTA_TYPES =
            DeferredRegister.create(HexRegistries.IOTA_TYPE, Hexextra.MODID);

    /**
     * 咒法学的「算术」注册表 —— 给已有图案(加法、索引、绝对值……)补上新类型算子的地方。
     *
     * <p>这是官方支持的扩展点:{@code Arithmetic} 接口的文档就写明它是给附属
     * "为尚未定义的类型覆写某个算子图案的行为"用的。
     *
     * <p>时序上要点:算术引擎是<b>首次使用时</b>才从这张表的内容构建的,
     * 所以我们的条目只要在 {@code RegisterEvent} 期间落表(也就是本注册表的正常时机),
     * 就一定会被算进去。
     */
    /**
     * 特殊处理器表。它在正常图案解析<b>之前</b>被询问,用来替换某枚图案的行为。
     *
     * <p>目前用于让「群体之拆解」「群体之策略」认识条件链与 switch 表。
     */
    public static final DeferredRegister<at.petrak.hexcasting.api.casting.castables.SpecialHandler.Factory<?>> SPECIAL_HANDLERS =
            DeferredRegister.create(HexRegistries.SPECIAL_HANDLER, Hexextra.MODID);

    public static final DeferredRegister<Arithmetic> ARITHMETICS =
            DeferredRegister.create(HexRegistries.ARITHMETIC, Hexextra.MODID);

    private HexextraRegistries() {
    }

    /**
     * 把上面几个注册表挂到模组事件总线上。
     *
     * <p>必须在各功能的注册条目都创建好之后再调用,所以调用前要先执行各功能的 init()。
     */
    public static void registerAll(IEventBus modEventBus) {
        ACTIONS.register(modEventBus);
        CONTINUATION_TYPES.register(modEventBus);
        IOTA_TYPES.register(modEventBus);
        ARITHMETICS.register(modEventBus);
        SPECIAL_HANDLERS.register(modEventBus);
    }
}
