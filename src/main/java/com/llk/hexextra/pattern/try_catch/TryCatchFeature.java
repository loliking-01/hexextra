package com.llk.hexextra.pattern.try_catch;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import com.llk.hexextra.Hexextra;
import com.llk.hexextra.core.HexextraRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * 「try / catch / finally」功能的注册入口。
 *
 * <h2>为什么必须注册续延帧</h2>
 * 续延帧会写进存档、也会同步给客户端:没登记类型的帧会让这两条路径失败。
 * 这是自定义续延帧最容易踩的坑,所以这里和图案一起注册。
 *
 * <h2>图案</h2>
 * {@code deaqqqwdeweweweweead},起始方向东南(SOUTH_EAST),吃 1 个参数 ——
 * 一个含 2~3 个代码块的列表。
 */
public final class TryCatchFeature {

    /** try_catch 的图案串。 */
    public static final String TRY_CATCH_ANGLES = "deaqqqwdeweweweweead";

    /** try_catch 的起始方向。 */
    public static final HexDir PATTERN_START_DIR = HexDir.SOUTH_EAST;

    /** get_exception 的图案串(仅 catch 内有效)。 */
    public static final String GET_EXCEPTION_ANGLES = "deqdeaqq";

    /** get_exception 的起始方向。 */
    public static final HexDir GET_EXCEPTION_START_DIR = HexDir.NORTH_EAST;

    /** 续延帧类型。 */
    public static final DeferredHolder<ContinuationFrame.Type<?>, ContinuationFrame.Type<?>> FRAME =
            HexextraRegistries.CONTINUATION_TYPES.register("try_catch", () -> {
                Hexextra.LOGGER.info("hexextra: 续延帧类型 hexextra:try_catch 已登记");
                return FrameTryCatch.TYPE;
            });

    /** 造 try_catch 的图案。 */
    public static final DeferredHolder<ActionRegistryEntry, ActionRegistryEntry> ACTION =
            HexextraRegistries.ACTIONS.register("try_catch", () -> {
                Hexextra.LOGGER.info("hexextra: 操作符图案 {} 已登记( {} 个角度 )",
                        TRY_CATCH_ANGLES, TRY_CATCH_ANGLES.length());
                return new ActionRegistryEntry(
                        HexPattern.fromAngleString(TRY_CATCH_ANGLES, PATTERN_START_DIR),
                        new OpTryCatch());
            });

    /** 触发本类的静态初始化。 */
    public static void init() {
        // 故意为空:静态字段的初始化就是全部工作。
    }

    private TryCatchFeature() {
    }
}