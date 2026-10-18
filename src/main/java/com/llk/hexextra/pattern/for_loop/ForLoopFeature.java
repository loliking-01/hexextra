package com.llk.hexextra.pattern.for_loop;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import com.llk.hexextra.Hexextra;
import com.llk.hexextra.core.HexextraRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * 「for 循环」功能的注册入口 —— 本功能对外的全部接线都集中在这一个文件里。
 *
 * <p>按规则 5,一个图案占一个独立文件夹;这个类就是该文件夹与 {@code core} 之间的唯一接口:
 * core 只提供注册表本体,具体注册什么由本类说了算。
 */
public final class ForLoopFeature {

    /**
     * 本操作符绑定的图案(角度串)。
     *
     * <p>已用工具核对过:11 个角度、无重叠边(自交检测通过)、不含 {@code s}(玩家画得出来),
     * 并且与 Hex Casting 内置的全部 185 个图案都不冲突。
     *
     * <p>注意:图案的「身份」只由这个角度串决定,与起始方向无关。
     * 起始方向只影响图案在书里/悬浮提示里的显示姿态。
     */
    public static final String PATTERN_ANGLES = "qaweedwaqqwde";

    /** 图案的起始方向(玩家从哪一边起笔)。 */
    public static final HexDir PATTERN_START_DIR = HexDir.NORTH_WEST;

    /** 倒序计数的 for:角度串与方向由作者指定。跑几轮不变,只是「取当前次数」反着记。 */
    public static final String FOR_DOWN_PATTERN_ANGLES = "edwqqawdeewaq";
    public static final HexDir FOR_DOWN_START_DIR = HexDir.NORTH_EAST;

    /** 取当前次数(0 起)。 */
    public static final String GET_INDEX_PATTERN_ANGLES = "qaweedwaqqwdedd";
    public static final HexDir GET_INDEX_START_DIR = HexDir.NORTH_WEST;

    /** 取剩余次数。 */
    public static final String GET_REMAINING_PATTERN_ANGLES = "aaqaweedwaqqwdedd";
    public static final HexDir GET_REMAINING_START_DIR = HexDir.SOUTH_WEST;

    /**
     * 循环续延帧的类型。
     *
     * <p>必须注册:续延会被序列化进存档、也会同步给客户端,没有登记类型的帧会让这两条路径失败。
     *
     * <p>供应商里的日志是刻意的启动自检:供应器<b>只有当条目真正被写进注册表时才会执行</b>,
     * 所以「日志出现」就等于注册成功;万一将来注册表出问题,日志不出现也能立刻发现。
     */
    public static final DeferredHolder<ContinuationFrame.Type<?>, ContinuationFrame.Type<FrameForLoop>> FRAME_TYPE =
            HexextraRegistries.CONTINUATION_TYPES.register("for_loop", () -> {
                return FrameForLoop.TYPE;
            });

    /**
     * 图案 → 操作符 的注册项。注册之后玩家画出该图案就会触发 {@link OpForLoop}。
     *
     * <p>注册名 {@code for_loop} 同时决定了游戏里的名称本地化键:
     * {@code hexcasting.action.hexextra:for_loop}(见 hexextra 的 en_us.json / zh_cn.json)。
     */
    public static final DeferredHolder<ActionRegistryEntry, ActionRegistryEntry> ACTION =
            HexextraRegistries.ACTIONS.register("for_loop", () -> {
                return new ActionRegistryEntry(
                        HexPattern.fromAngleString(PATTERN_ANGLES, PATTERN_START_DIR),
                        new OpForLoop());
            });

    /** 倒序计数的 for 循环。 */
    public static final DeferredHolder<ActionRegistryEntry, ActionRegistryEntry> FOR_DOWN =
            HexextraRegistries.ACTIONS.register("for_down", () -> {
                return new ActionRegistryEntry(
                        HexPattern.fromAngleString(FOR_DOWN_PATTERN_ANGLES, FOR_DOWN_START_DIR),
                        new OpForLoop(true));
            });

    /** 取当前次数(仅在循环内有效)。 */
    public static final DeferredHolder<ActionRegistryEntry, ActionRegistryEntry> GET_INDEX =
            HexextraRegistries.ACTIONS.register("get_index", () -> {
                return new ActionRegistryEntry(
                        HexPattern.fromAngleString(GET_INDEX_PATTERN_ANGLES, GET_INDEX_START_DIR),
                        new LoopGetters.GetIndex());
            });

    /** 取剩余次数(仅在循环内有效)。 */
    public static final DeferredHolder<ActionRegistryEntry, ActionRegistryEntry> GET_REMAINING =
            HexextraRegistries.ACTIONS.register("get_remaining", () -> {
                return new ActionRegistryEntry(
                        HexPattern.fromAngleString(GET_REMAINING_PATTERN_ANGLES, GET_REMAINING_START_DIR),
                        new LoopGetters.GetRemaining());
            });

    private ForLoopFeature() {
    }

    /**
     * 触发本类的静态初始化,从而把上面两个注册项挂进 {@code core} 的 DeferredRegister。
     *
     * <p>方法体是空的 —— 它的唯一作用就是「被调用」。
     * 必须由主类在 {@link HexextraRegistries#registerAll} <b>之前</b>调用,
     * 否则注册条目还没挂上,事件总线就已经把注册表交出去了。
     */
    public static void init() {
        // 故意为空:静态字段的初始化就是全部工作。
    }
}
