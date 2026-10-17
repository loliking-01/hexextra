package com.llk.hexextra.core;

import at.petrak.hexcasting.api.HexAPI;
import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic;
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import com.llk.hexextra.HexextraCommon;
import com.llk.hexextra.pattern.for_loop.FrameForLoop;
import com.llk.hexextra.pattern.if_else.FrameIfElse;
import com.llk.hexextra.pattern.if_else.IfElseArithmetic;
import com.llk.hexextra.pattern.if_else.IfElseIota;
import com.llk.hexextra.pattern.if_else.OpCreateElseChain;
import com.llk.hexextra.pattern.if_else.OpCreateIfChain;
import com.llk.hexextra.pattern.for_loop.OpForLoop;
import com.llk.hexextra.pattern.switch_case.CaseIota;
import com.llk.hexextra.pattern.switch_case.OpCreateCase;
import com.llk.hexextra.pattern.switch_case.SwitchArithmetic;
import com.llk.hexextra.pattern.switch_case.SwitchIota;
import net.minecraft.resources.ResourceLocation;

/**
 * 本模组要注册进咒法学三张表里的全部内容 —— <b>与加载器无关</b>的定义集中在这里。
 *
 * <h2>为什么要有这一层</h2>
 * Forge 与 Fabric 的注册 API 完全不同(前者 {@code DeferredRegister} + 注册事件,
 * 后者直接 {@code Registry.register}),但"要注册什么、用什么 id"是一样的。
 * 把内容定义抽到这里,两个 loader 模块各自只写十几行胶水,就不会出现
 * "Forge 侧漏注册了一个 iota 类型"这种两版行为不一致的 bug。
 *
 * <h2>注册进哪三张表</h2>
 * <ul>
 *   <li>{@code HexIotaTypes.REGISTRY}(类型 {@code hexcasting:iota_type})—— 告诉游戏认得这个数据类型;</li>
 *   <li>{@code HexArithmetics.REGISTRY}(类型 {@code hexcasting:arithmetic})—— 让已有图案认识新类型;</li>
 *   <li>{@code HexActions.REGISTRY}(类型 {@code hexcasting:action})—— 新图案本身。</li>
 * </ul>
 * 这些 {@code REGISTRY} 字段是"活的"注册表实例(0.11.4 通过 {@code IXplatAbstractions} 拿),
 * 所以 Fabric 侧可以直接往上面注册。
 */
public final class HexextraContent {

    /** 本模组的命名空间。 */
    public static final String MOD_ID = HexextraCommon.MOD_ID;

    // =======================================================================
    // 图案角度串
    //
    // 与 1.21.1 版完全一致 —— 图案是游戏概念,与版本无关,移植时必须保持不变,
    // 否则玩家的笔记与教程就对不上了。
    // =======================================================================

    /** 「凯斯之策略」:2 参,造一条 case。 */
    public static final String CASE_PATTERN_ANGLES = "dawwaqw";

    /** 「佛尔之策略」:2 参,循环。 */
    public static final String FOR_LOOP_PATTERN_ANGLES = "qaweedwaqqwde";

    /** 「伊芙艾儿丝之策略」:2 参,造一条条件链。 */
    public static final String IF_CHAIN_PATTERN_ANGLES = "aedaqda";

    /** 「艾儿丝之策略」:1 参,造一条只有 else 的链。 */
    public static final String ELSE_CHAIN_PATTERN_ANGLES = "aedaqdaaw";

    /** 所有图案的起始方向。 */
    /**
     * for 循环的起始方向 —— <b>西北</b>。
     *
     * <p>与另外三枚图案不同:1.21.1 版就是用它 + 角度串 {@code qaweedwaqqwde};
     * 移植时必须原样保留,否则玩家在 1.21.1 学会的画法在 1.20.1 上画不出来。
     */
    public static final HexDir FOR_LOOP_START_DIR = HexDir.NORTH_WEST;

    public static final HexDir PATTERN_START_DIR = HexDir.SOUTH_EAST;

    // =======================================================================
    // iota 类型
    // =======================================================================

    /** {@code hexextra:switch} —— switch 表的类型。 */
    public static final ResourceLocation SWITCH_IOTA_ID = id("switch");

    /** {@code hexextra:case} —— 一条 case 的类型。 */
    public static final ResourceLocation CASE_IOTA_ID = id("case");

    /** {@code hexextra:if_else} —— 条件链的类型。 */
    public static final ResourceLocation IF_ELSE_IOTA_ID = id("if_else");

    /** 所有 iota 类型(id → 实例)。 */
    public static Iterable<Entry<IotaType<?>>> iotaTypes() {
        return java.util.List.of(
                new Entry<>(SWITCH_IOTA_ID, SwitchIota.TYPE),
                new Entry<>(CASE_IOTA_ID, CaseIota.TYPE),
                new Entry<>(IF_ELSE_IOTA_ID, IfElseIota.TYPE));
    }

    // =======================================================================
    // 算术
    // =======================================================================

    /** {@code hexextra:switch} —— 给已有图案补上 switch 家族的算子。 */
    public static final ResourceLocation SWITCH_ARITHMETIC_ID = id("switch");

    /** {@code hexextra:for_loop} —— for 循环的续延帧类型。 */
    public static final ResourceLocation FOR_LOOP_FRAME_ID = id("for_loop");

    /** {@code hexextra:if_else} —— 条件链的续延帧类型。 */
    public static final ResourceLocation IF_ELSE_FRAME_ID = id("if_else");

    /** 所有续延帧类型(id → 类型对象)。 */
    public static Iterable<Entry<ContinuationFrame.Type<?>>> frameTypes() {
        return java.util.List.of(
                new Entry<>(FOR_LOOP_FRAME_ID, FrameForLoop.TYPE),
                new Entry<>(IF_ELSE_FRAME_ID, FrameIfElse.TYPE));
    }

    /** {@code hexextra:if_else} —— 给已有图案补上条件链的算子。 */
    public static final ResourceLocation IF_ELSE_ARITHMETIC_ID = id("if_else");

    public static Iterable<Entry<Arithmetic>> arithmetics() {
        return java.util.List.of(
                new Entry<>(SWITCH_ARITHMETIC_ID, new SwitchArithmetic()),
                new Entry<>(IF_ELSE_ARITHMETIC_ID, new IfElseArithmetic()));
    }

    // =======================================================================
    // 图案(动作)
    // =======================================================================

    /** {@code hexextra:case} —— 造一条 case 的图案。 */
    public static final ResourceLocation CASE_ACTION_ID = id("case");

    /** {@code hexextra:for_loop} —— for 循环的图案。 */
    public static final ResourceLocation FOR_LOOP_ACTION_ID = id("for_loop");

    /** {@code hexextra:if_chain} / {@code hexextra:else_chain} —— 造链的两个图案。 */
    public static final ResourceLocation IF_CHAIN_ACTION_ID = id("if_chain");
    public static final ResourceLocation ELSE_CHAIN_ACTION_ID = id("else_chain");

    public static Iterable<Entry<ActionRegistryEntry>> actions() {
        return java.util.List.of(
                new Entry<>(CASE_ACTION_ID, new ActionRegistryEntry(
                        HexPattern.fromAngles(CASE_PATTERN_ANGLES, PATTERN_START_DIR),
                        new OpCreateCase())),
                new Entry<>(FOR_LOOP_ACTION_ID, new ActionRegistryEntry(
                        HexPattern.fromAngles(FOR_LOOP_PATTERN_ANGLES, FOR_LOOP_START_DIR),
                        new OpForLoop())),
                new Entry<>(IF_CHAIN_ACTION_ID, new ActionRegistryEntry(
                        HexPattern.fromAngles(IF_CHAIN_PATTERN_ANGLES, PATTERN_START_DIR),
                        new OpCreateIfChain())),
                new Entry<>(ELSE_CHAIN_ACTION_ID, new ActionRegistryEntry(
                        HexPattern.fromAngles(ELSE_CHAIN_PATTERN_ANGLES, PATTERN_START_DIR),
                        new OpCreateElseChain())));
    }

    // =======================================================================
    // 工具
    // =======================================================================

    /** 一个「id → 值」的注册项。 */
    public record Entry<T>(ResourceLocation id, T value) {
    }

    private static ResourceLocation id(String path) {
        return HexAPI.modLoc(path);
    }

    private HexextraContent() {
    }
}
