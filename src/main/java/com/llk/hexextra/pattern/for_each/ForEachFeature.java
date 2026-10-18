package com.llk.hexextra.pattern.for_each;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame;
import com.llk.hexextra.core.HexextraRegistries;
import com.llk.hexextra.pattern.for_loop.LoopGetters;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * foreach 家族的注册项:foreach 图案本身、它的续延帧类型、以及「取当前元素」。
 *
 * <p>这三者必须一起注册:
 * <ul>
 *   <li>图案不注册 → 玩家画不出来</li>
 *   <li><b>续延帧类型不注册</b> → 续延写 NBT 或同步客户端时会因找不到编码器而失败,
 *       表现为"循环一跑就报错"或"存档里这个法术再也读不出来"</li>
 *   <li>「取当前元素」不注册 → 循环体拿不到元素,foreach 等于空转</li>
 * </ul>
 */
public final class ForEachFeature {

    /** foreach 自身的图案:角度串与方向由作者指定。 */
    public static final String FOR_EACH_PATTERN_ANGLES = "qqaweedwaqqwdee";
    public static final HexDir FOR_EACH_START_DIR = HexDir.NORTH_EAST;

    /** 「取当前元素」的图案。 */
    public static final String GET_CURRENT_PATTERN_ANGLES = "aaqaweedwaqqwde";
    public static final HexDir GET_CURRENT_START_DIR = HexDir.SOUTH_WEST;

    /** 续延帧类型。不注册的话续延无法序列化/同步。 */
    public static final DeferredHolder<ContinuationFrame.Type<?>, ContinuationFrame.Type<FrameForEach>> FRAME_TYPE =
            HexextraRegistries.CONTINUATION_TYPES.register("for_each", () -> FrameForEach.TYPE);

    /** foreach 图案本身。 */
    public static final DeferredHolder<ActionRegistryEntry, ActionRegistryEntry> ACTION =
            HexextraRegistries.ACTIONS.register("for_each", () -> {
                return new ActionRegistryEntry(
                        HexPattern.fromAngleString(FOR_EACH_PATTERN_ANGLES, FOR_EACH_START_DIR),
                        new OpForEach());
            });

    /** 取当前元素(仅在 foreach 内有效)。 */
    public static final DeferredHolder<ActionRegistryEntry, ActionRegistryEntry> GET_CURRENT =
            HexextraRegistries.ACTIONS.register("get_current", () -> {
                return new ActionRegistryEntry(
                        HexPattern.fromAngleString(GET_CURRENT_PATTERN_ANGLES, GET_CURRENT_START_DIR),
                        new LoopGetters.GetCurrent());
            });

    private ForEachFeature() {
    }

    /** 触发本类静态初始化,把上面的注册项挂进 core 的 DeferredRegister。 */
    public static void init() {
        // 故意为空:静态字段的初始化就是全部工作。
    }
}
