package com.llk.hexextra.pattern.if_else;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic;
import at.petrak.hexcasting.api.casting.arithmetic.engine.ArithmeticEngine;
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame;
import at.petrak.hexcasting.api.casting.iota.BooleanIota;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.lib.hex.HexArithmetics;
import com.llk.hexextra.Hexextra;
import com.llk.hexextra.core.HexextraRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * 「if / elseif / else」功能的注册入口 —— 本功能对外的接线集中在这一个文件里。
 *
 * <h2>为什么是两个图案而不是一个</h2>
 * 这个功能需要两种不同参数个数的操作:「建 if 链」吃 2 个参数,「建仅 else 链」吃 1 个。
 * 咒法学的普通图案只能对应固定参数个数(参数个数是操作符自己决定的,图案本身不携带这个信息),
 * 而做成特殊处理器(用图案形状编码参数个数)又会让图案不再是一个固定串。
 * 所以这里用<b>两个图案</b>分别承担,语义最直白。
 *
 * <h2>两个图案的关系</h2>
 * {@code aedaqda} 正好是 {@code aedaqdaaw} 的前缀。这没有冲突:
 * 图案查找是<b>按签名精确匹配</b>的,7 个角度与 9 个角度是两个不同的签名。
 *
 * <h2>两个图案都已通过完整校验</h2>
 * 字符合法、无自交、不含 {@code s}(玩家画得出来)、不与 Hex Casting 内置的 185 个图案冲突、
 * 不与本模组已占用的图案冲突,并且对<b>全部六个起始方向</b>都不会被三个特殊处理器
 * (簿记员之策略的掩码族、托特之策略、数字字面量)匹配 —— 后者是很隐蔽的坑:
 * 特殊处理器是在普通图案之后才检查的,选错图案会顶掉它们。
 */
public final class IfElseFeature {

    /**
     * 诊断开关:打开后,条件链的创建与执行的每一步都会写进日志。
     *
     * <p>存在的理由:条件链是"跨多次 VM 步"执行的,一旦某一步行为不符预期,
     * 光看玩家那边"没反应"是查不出问题的(不报错、也没效果)。
     * 打开它跑一次,日志会完整记录:链怎么建的、条件求值前后栈深多少、
     * 产出几个值、是不是布尔、最后走了哪条分支。
     *
     * <p><b>平时保持 false</b> —— 否则每次施法都会刷日志。
     * 只有在"施法后毫无反应、也不报错"这类没法靠读代码定位的问题上才临时打开。
     */
    public static final boolean TRACE = false;

    /** 按 {@link #TRACE} 决定要不要写诊断日志。 */
    public static void trace(String format, Object... args) {
        if (TRACE) {
            Hexextra.LOGGER.info("[if_else] " + format, args);
        }
    }

    /** 建 if 链的图案:2 个参数(条件代码块、代码块)。 */
    public static final String IF_PATTERN_ANGLES = "aedaqda";

    /** 建仅 else 链的图案:1 个参数(代码块)。 */
    public static final String ELSE_PATTERN_ANGLES = "aedaqdaaw";

    /** 两个图案共用的起始方向:东南。 */
    public static final HexDir PATTERN_START_DIR = HexDir.SOUTH_EAST;

    /**
     * 条件链的续延帧类型。
     *
     * <p><b>必须注册</b>:if 链执行时要把 {@link FrameIfElse} 挂到续延上,而续延会被写进存档、
     * 也会同步给客户端。没有登记类型的帧会让这两条路径失败(找不到编解码器)。
     * 这是自定义续延帧最容易踩的坑 —— for 循环那一轮已经踩过一次。
     */
    public static final DeferredHolder<ContinuationFrame.Type<?>, ContinuationFrame.Type<FrameIfElse>> FRAME_TYPE =
            HexextraRegistries.CONTINUATION_TYPES.register("if_else", () -> {
                Hexextra.LOGGER.info("hexextra: 续延帧类型 hexextra:if_else 已登记");
                return FrameIfElse.TYPE;
            });

    /**
     * 「建 if 链」的注册项。
     *
     * <p>注册名 {@code if_chain} 同时决定游戏里的名称本地化键:
     * {@code hexcasting.action.hexextra:if_chain}。
     */
    public static final DeferredHolder<ActionRegistryEntry, ActionRegistryEntry> IF_CHAIN =
            HexextraRegistries.ACTIONS.register("if_chain", () -> {
                Hexextra.LOGGER.info("hexextra: 操作符图案 {} 已登记( {} 个角度 )",
                        IF_PATTERN_ANGLES, IF_PATTERN_ANGLES.length());
                return new ActionRegistryEntry(
                        HexPattern.fromAngleString(IF_PATTERN_ANGLES, PATTERN_START_DIR),
                        new OpCreateIfChain());
            });

    /**
     * 「建仅 else 链」的注册项。
     *
     * <p>名称本地化键:{@code hexcasting.action.hexextra:else_chain}。
     */
    public static final DeferredHolder<ActionRegistryEntry, ActionRegistryEntry> ELSE_CHAIN =
            HexextraRegistries.ACTIONS.register("else_chain", () -> {
                Hexextra.LOGGER.info("hexextra: 操作符图案 {} 已登记( {} 个角度 )",
                        ELSE_PATTERN_ANGLES, ELSE_PATTERN_ANGLES.length());
                return new ActionRegistryEntry(
                        HexPattern.fromAngleString(ELSE_PATTERN_ANGLES, PATTERN_START_DIR),
                        new OpCreateElseChain());
            });

    /**
     * 条件链的算术(给已有图案补上新类型算子)。
     *
     * <p>引擎是懒构建的,所以这里只要在注册期间落表即可被算进去。
     */
    public static final DeferredHolder<Arithmetic, Arithmetic> ARITHMETIC =
            HexextraRegistries.ARITHMETICS.register("if_else", () -> {
                Hexextra.LOGGER.info("hexextra: 算术 hexextra:if_else 已登记(add/index/abs/remove_from/replace)");
                return new IfElseArithmetic();
            });

    private IfElseFeature() {
    }

    /**
     * 触发本类的静态初始化,把注册项挂进 {@code core} 的 DeferredRegister。
     *
     * <p>方法体是空的 —— 唯一作用就是"被调用"。必须在 {@code registerAll} 之前调用。
     */
    public static void init() {
        // 故意为空:静态字段的初始化就是全部工作。
    }

    /**
     * 启动自检:用一份注册表快照<b>现造一个一次性算术引擎</b>,借此校验所有算术的 arity 一致性。
     *
     * <h2>为什么必须做这件事</h2>
     * 算术引擎是<b>懒构建</b>的 —— 咒法学直到第一次有操作符真正用到算术时才去建它。
     * 而构建过程中会把同一图案的所有算子归到一组,一旦发现 arity 不一致就抛
     * {@code IllegalArgumentException}。也就是说:如果我把某个图案的 arity 写错了,
     * 后果不是"这个图案不能用",而是<b>玩家第一次施法时整个算术系统一起瘫痪</b>,
     * 而且报错发生在游戏中途、极难定位。
     *
     * <p>所以这里在注册全部完成之后、进入游戏之前,自己造一个引擎跑一遍:
     * 把"游戏中途炸"提前成"启动即报错"。造的引擎<b>不装进咒法学</b>(它们是两份独立实例),
     * 纯粹当校验用,不影响咒法学自己的懒加载时机。
     *
     * <p>时机要求:必须晚于 {@code RegisterEvent}(否则其它模组的算术还没落表),
     * 所以由 {@code FMLCommonSetupEvent} 触发。
     */
    public static void checkArithmeticConsistency() {
        List<Arithmetic> snapshot = new ArrayList<>();
        for (Arithmetic arith : HexArithmetics.REGISTRY) {
            snapshot.add(arith);
        }

        // 若某个图案的算子 arity 与既有算子不一致,这一行会抛异常
        new ArithmeticEngine(snapshot);

        Hexextra.LOGGER.info("hexextra: 算术 arity 自检通过(快照 {} 个算术)", snapshot.size());
    }

    /**
     * 启动自检:条件链的 NBT 编解码<b>往返一致性</b>,顺带验证悬停显示确实是多行。
     *
     * <h2>为什么必须做这件事</h2>
     * 自定义 iota 会被写进物品数据组件与方块实体的存档。而咒法学对 NBT 侧的失败处理是
     * <b>静默退化</b> —— {@code IotaType.TYPED_CODEC} 在反序列化超限或类型不对时
     * 会 {@code orElse(new GarbageIota())},<b>不报错</b>。
     * 也就是说:如果我的编解码器字段写得不对称(读的字段和写的不一致),
     * 后果是玩家的法术书内容<b>悄悄变成乱码</b>,而且当场毫无提示、事后极难定位。
     *
     * <p>与其等玩家丢数据,不如在启动时造一条链、走一遍
     * 「对象 → NBT → 对象」并比对,当场发现。
     *
     * <p>顺带验证 {@code display()}:它会在鼠标悬停时被调用,若抛异常就是客户端崩溃;
     * 同时确认结果里含 {@code '\n'} —— 那是"两个分支确实分行"的实证。
     */
    public static void checkCodecRoundTrip() {
        // 造一条含两个分支 + 一个 else 的链,尽量覆盖编解码器的每条路径
        TreeList<Iota> condition = TreeList.from(List.of(new BooleanIota(true)));
        TreeList<Iota> body = TreeList.from(List.of(new DoubleIota(42.0)));
        TreeList<Iota> elseBody = TreeList.from(List.of(new DoubleIota(0.0)));

        IfElseIota chain = new IfElseIota(
                List.of(new IfElseIota.Branch(condition, body),
                        new IfElseIota.Branch(condition, body)),
                elseBody);

        // ---- 1) NBT 往返 ----
        Tag encoded = IotaType.TYPED_CODEC.encodeStart(NbtOps.INSTANCE, chain).getOrThrow();
        Iota decoded = IotaType.TYPED_CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();

        if (!(decoded instanceof IfElseIota) || !decoded.equals(chain)) {
            throw new IllegalStateException(
                    "hexextra: if_else 的 NBT 编解码往返结果不一致 —— 存档会静默损坏,必须修复");
        }

        // ---- 2) 悬停显示 ----
        String shown = chain.display().getString();
        if (shown.isEmpty() || !shown.contains("\n")) {
            throw new IllegalStateException("hexextra: if_else 的悬停显示不正常:[" + shown + "]");
        }

        // 用 ⏎ 代替真换行,免得日志里那一行被折断
        Hexextra.LOGGER.info("hexextra: if_else 编解码往返自检通过;悬停显示 = {}",
                shown.replace("\n", " ⏎ "));
    }
}
