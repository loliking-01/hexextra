package com.llk.hexextra.core;

import at.petrak.hexcasting.api.casting.iota.IotaType;
import com.llk.hexextra.pattern.if_else.IfElseIota;
import com.llk.hexextra.pattern.switch_case.CaseIota;
import com.llk.hexextra.pattern.switch_case.SwitchIota;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * hexextra 的自定义 iota 类型,集中在这里登记。
 *
 * <p>为什么集中在 core:iota 类型是「游戏认识这个数据类型」的登记处,属于跨功能的基础设施,
 * 所以不放进各功能自己的文件夹(见项目规则 5)。
 *
 * <p>供应商里的日志是刻意的启动自检:供应器<b>只有当条目真正写进注册表时才会执行</b>,
 * 所以「日志出现」就等于注册成功。
 */
public final class HexextraIotaTypes {

    /**
     * try_catch 的「异常」iota。
     *
     * <p>注册名 {@code exception} → 完整 id 为 {@code hexextra:exception}。
     */
    public static final DeferredHolder<IotaType<?>, IotaType<com.llk.hexextra.pattern.try_catch.ExceptionIota>> EXCEPTION =
            HexextraRegistries.IOTA_TYPES.register("exception", () -> {
                com.llk.hexextra.Hexextra.LOGGER.info("hexextra: iota 类型 hexextra:exception 已登记");
                return com.llk.hexextra.pattern.try_catch.ExceptionIota.TYPE;
            });

    /**
     * if / elseif / else 条件链。
     *
     * <p>注册名 {@code if_else} → 完整 id 为 {@code hexextra:if_else}。
     */
    public static final DeferredHolder<IotaType<?>, IotaType<IfElseIota>> IF_ELSE =
            HexextraRegistries.IOTA_TYPES.register("if_else", () -> {
                com.llk.hexextra.Hexextra.LOGGER.info("hexextra: iota 类型 hexextra:if_else 已登记");
                return IfElseIota.TYPE;
            });

    /**
     * switch 多路等值分派。
     *
     * <p>注册名 {@code switch} → 完整 id 为 {@code hexextra:switch}。
     */
    public static final DeferredHolder<IotaType<?>, IotaType<SwitchIota>> SWITCH =
            HexextraRegistries.IOTA_TYPES.register("switch", () -> {
                com.llk.hexextra.Hexextra.LOGGER.info("hexextra: iota 类型 hexextra:switch 已登记");
                return SwitchIota.TYPE;
            });

    /**
     * switch 的一条 case(造表的原料,不可执行)。
     *
     * <p>注册名 {@code case} → 完整 id 为 {@code hexextra:case}。
     * 注意这与同名操作符({@code hexcasting.action.hexextra:case})分属两张不同的注册表,
     * 互不冲突。
     */
    public static final DeferredHolder<IotaType<?>, IotaType<CaseIota>> CASE =
            HexextraRegistries.IOTA_TYPES.register("case", () -> {
                com.llk.hexextra.Hexextra.LOGGER.info("hexextra: iota 类型 hexextra:case 已登记");
                return CaseIota.TYPE;
            });

    private HexextraIotaTypes() {
    }

    /**
     * 触发本类的静态初始化,把上面的注册项挂进 {@code HexextraRegistries.IOTA_TYPES}。
     *
     * <p>方法体是空的 —— 唯一作用就是"被调用"。必须在 {@code registerAll} 之前调用。
     */
    public static void init() {
        // 故意为空:静态字段的初始化就是全部工作。
    }
}
