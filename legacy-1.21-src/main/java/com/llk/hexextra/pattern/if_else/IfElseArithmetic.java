package com.llk.hexextra.pattern.if_else;

import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic;
import at.petrak.hexcasting.api.casting.arithmetic.operator.Operator;
import at.petrak.hexcasting.api.casting.arithmetic.operator.OperatorBasic;
import at.petrak.hexcasting.api.casting.arithmetic.predicates.IotaMultiPredicate;
import at.petrak.hexcasting.api.casting.arithmetic.predicates.IotaPredicate;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import com.llk.hexextra.core.HexextraIotaTypes;
import com.llk.hexextra.core.HexextraMishaps;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 if/elseif/else 条件链接进咒法学的<b>算术引擎</b>,让原本作用于列表的图案也能作用在条件链上。
 *
 * <h2>为什么要走算术引擎</h2>
 * 咒法学把 {@code 加法之馏化 / 切除器之馏化 / 外科医生之提整 / 索引 / 绝对值} 这类
 * "按参数类型分派"的图案统一交给算术引擎处理,而 {@link Arithmetic} 接口的文档原话就是:
 * <blockquote>This is the interface to implement if you want to override the behaviour of an
 * Operator pattern like ADD, SUB, etc. for some type/s of iotas for which that Operator pattern
 * is not yet defined.</blockquote>
 * 也就是说,<b>加算术重载是官方支持的附属扩展点</b>,不需要 Mixin、也不要去顶掉原图案。
 *
 * <h2>两条必须遵守的硬约束</h2>
 * <ol>
 *   <li><b>arity 必须与内置算子完全一致</b>。引擎在构建时会把同一图案的所有算子归到一起,
 *       发现 arity 不一致就抛 {@code IllegalArgumentException} —— 而引擎是懒加载的
 *       (首次施法才构建),所以写错会在游戏中途连带<b>整个算术系统瘫痪</b>。
 *       本类各图案的 arity 已逐一对源码核对:
 *       {@code ADD=2、INDEX=2、ABS=1、REMOVE=2、REPLACE=3}。
 *       {@code IfElseFeature.checkArithmeticConsistency()} 会在启动时用一份快照跑一遍校验,
 *       把这种错误提前成"启动即报错"。</li>
 *   <li><b>类型谓词只能依据 IotaType</b>。引擎会按「图案 + 参数类型元组」缓存解析结果,
 *       谓词一旦依赖具体取值,缓存就会失效。所以本类全部用 {@link IotaPredicate#ofType}。</li>
 * </ol>
 *
 * <h2>⚠ 为什么类型谓词是"方法"而不是"静态字段"</h2>
 * 这是个踩过的坑,值得写清楚:本类是在 {@code RegisterEvent} 期间被构造的
 * (DeferredRegister 的供应商那时才执行)。而那时 {@code DeferredHolder} <b>还没绑定</b> ——
 * 若把 {@code HexextraIotaTypes.IF_ELSE.get()} 写进静态字段,类初始化就会抛
 * {@code NullPointerException: Trying to access unbound value},
 * 后果是<b>整个服务端起不来</b>。
 *
 * <p>所以这里一律改成方法,等到真正需要造算子时(引擎构建期,远晚于注册期)才去解析持有者。
 *
 * <h2>为什么一个图案只有一个算子</h2>
 * {@code ArithmeticEngine} 对每个 {@code (Arithmetic, 图案)} 只取<b>一个</b>算子。
 * 所以「加法之馏化」既要能吃 (链, 链) 又要能吃 (链, 列表) 时,不能注册两个算子,
 * 而是用一个算子、其谓词写成 {@code either(...)},再由 {@code apply} 里分派。
 */
public final class IfElseArithmetic implements Arithmetic {

    /** 本算术负责的图案 —— 必须与 {@link #getOperator} 里的分支一一对应。 */
    private final List<HexPattern> ops = List.of(ADD, INDEX, ABS, REMOVE, REPLACE);

    @Override
    public String arithName() {
        return "hexextra_if_else";
    }

    @Override
    public Iterable<HexPattern> opTypes() {
        return this.ops;
    }

    @Override
    public Operator getOperator(HexPattern pattern) {
        // 按签名比较而不按 HexPattern.equals:后者还要求起始方向一致,
        // 而引擎的查表键就是 opTypes() 返回的那批常量,签名比较更稳。
        if (sameSignature(pattern, ADD)) return this.makeAdd();
        if (sameSignature(pattern, INDEX)) return this.makeIndex();
        if (sameSignature(pattern, ABS)) return this.makeLength();
        if (sameSignature(pattern, REMOVE)) return this.makeRemove();
        if (sameSignature(pattern, REPLACE)) return this.makeReplace();
        throw new IllegalArgumentException("hexextra 的 if_else 算术不支持图案 " + pattern);
    }

    private static boolean sameSignature(HexPattern a, HexPattern b) {
        return a.getSignature().equals(b.getSignature());
    }

    // =======================================================================
    // 类型谓词 —— 每次现取,绝不在类初始化时解析 DeferredHolder(见类注释的坑)
    // =======================================================================

    /** 「是条件链」的谓词。 */
    private static IotaPredicate chain() {
        return IotaPredicate.ofType(HexextraIotaTypes.IF_ELSE.get());
    }

    /** 「是列表」的谓词。 */
    private static IotaPredicate list() {
        return IotaPredicate.ofType(HexIotaTypes.LIST.get());
    }

    /** 「是数值」的谓词。 */
    private static IotaPredicate number() {
        return IotaPredicate.ofType(HexIotaTypes.DOUBLE.get());
    }

    // =======================================================================
    // 加法之馏化:组装 —— 把两个代码块/两条链接起来
    // =======================================================================

    /**
     * {@code add(链, 链)} 或 {@code add(链, 列表)}。
     *
     * <p>原图案是「两个列表拼接」;对条件链的对应语义就是<b>把两条链接起来</b>:
     * <ul>
     *   <li>{@code add(链A, 链B)} —— A 的分支在前,B 的分支接在后面(B 的 else 成为整条链的 else)</li>
     *   <li>{@code add(链A, 列表)} —— 该列表成为整条链最终执行、不做条件判断的 else 块</li>
     * </ul>
     *
     * <p>A 自己<b>已经有 else</b> 时直接报错,而不是默默覆盖 —— 链只能有一个最终 else。
     */
    private Operator makeAdd() {
        return chain(2, IotaMultiPredicate.either(
                IotaMultiPredicate.pair(chain(), chain()),
                IotaMultiPredicate.pair(chain(), list())),
                args -> {
                    IfElseIota head = asChain(args.get(0));
                    Iota second = args.get(1);

                    if (second instanceof IfElseIota tail) {
                        // 两条链相接 —— 规则与 Mixin 侧"批量串接"共用同一份实现,
                        // 免得「已有 else 报错」这条规矩在两处走样
                        return List.of(IfElseChainOps.concat(head, tail));
                    }

                    // 第二个参数是列表 → 它就是最终的 else 代码块
                    return List.of(IfElseChainOps.attachElse(head, OperatorUtils.getList(args, 1, 2)));
                });
    }

    // =======================================================================
    // 索引:拆解 —— 取出第 i 个分支(表示为"单分支链")
    // =======================================================================

    /**
     * {@code index(链, i)} → 把第 i 个分支取出来,表示成一条<b>单分支链</b>。
     *
     * <p>越界时返回 {@code NullIota} —— 这是照搬原版列表版 {@code OperatorIndex} 的行为
     * (它用 {@code getOrElse { NullIota() }}),保持一致以免玩家困惑。
     *
     * <p>原版对下标是「取整、不做整性校验」的,这里也照搬:传 1.5 会被取整成 2。
     */
    private Operator makeIndex() {
        return chain(2, IotaMultiPredicate.pair(chain(), number()), args -> {
            IfElseIota source = asChain(args.get(0));
            int i = (int) Math.round(OperatorUtils.getDouble(args, 1, 2));

            List<IfElseIota.Branch> branches = source.getBranches();
            if (i < 0 || i >= branches.size()) {
                return List.of(new NullIota());
            }
            return List.of(new IfElseIota(List.of(branches.get(i)), null));
        });
    }

    // =======================================================================
    // 绝对值:拆解 —— 条件链"有多长"就是有多少个分支
    // =======================================================================

    /**
     * {@code abs(链)} → 分支数量。
     *
     * <p>原图案对列表取长度,对条件链自然就是<b>分支个数</b>(不含最终 else)。
     */
    private Operator makeLength() {
        return chain(1, IotaMultiPredicate.all(chain()), args -> {
            IfElseIota source = asChain(args.get(0));
            return List.of(new DoubleIota(source.getBranches().size()));
        });
    }

    // =======================================================================
    // 切除器之馏化:把第 i 个分支删掉
    // =======================================================================

    /**
     * {@code remove_from(链, i)} → 删掉第 i 个分支。
     *
     * <p>与原版列表行为一致:下标是「取整、必须为整数」的,<b>越界时原样返回</b>不报错。
     * 删空所有分支后剩下的是一条「仅 else 链」(或彻底空的链),两者都是合法状态。
     */
    private Operator makeRemove() {
        return chain(2, IotaMultiPredicate.pair(chain(), number()), args -> {
            IfElseIota source = asChain(args.get(0));
            // 原版用的是 nextInt(要求整数值),这里对应 OperatorUtils.getInt
            int i = OperatorUtils.getInt(args, 1, 2);

            List<IfElseIota.Branch> branches = source.getBranches();
            if (i < 0 || i >= branches.size()) {
                return List.of(source);   // 越界原样返回
            }

            List<IfElseIota.Branch> out = new ArrayList<>(branches);
            out.remove(i);
            return List.of(new IfElseIota(out, source.getElseBody()));
        });
    }

    // =======================================================================
    // 外科医生之提整:把第 i 个分支换成别的
    // =======================================================================

    /**
     * {@code replace(链, i, 新分支链)} → 替换第 i 个分支。
     *
     * <p>与两个相邻图案的边界行为保持一致:
     * <ul>
     *   <li>下标<b>越界会报错</b>(原版 {@code OperatorReplace} 用
     *       {@code nextPositiveIntUnder},不像 {@code remove_from} 那样宽容),
     *       本实现照搬,复用咒法学已有的本地化文案 {@code int.positive.less.equal};</li>
     *   <li>替换项本身可以含<b>多个分支</b>,会就地摊开(1 个换 N 个);</li>
     *   <li>替换项<b>不允许自带 else</b> —— 分支只由「条件 + 代码块」组成,
     *       悄悄丢掉替换项里的 else 会让玩家以为接上了却没接上,所以直接报错。</li>
     * </ul>
     */
    private Operator makeReplace() {
        return chain(3, IotaMultiPredicate.triple(chain(), number(), chain()), args -> {
            IfElseIota source = asChain(args.get(0));
            List<IfElseIota.Branch> branches = source.getBranches();

            // 参数顺序:0=链, 1=下标, 2=替换项;argc 传 3 让报错定位到正确的位置
            int i = OperatorUtils.getPositiveIntUnder(args, 1, branches.size(), 3);

            IfElseIota replacement = asChain(args.get(2));
            if (replacement.getElseBody() != null) {
                throw new HexextraMishaps.ReplacementHasElse();
            }

            List<IfElseIota.Branch> out = new ArrayList<>(branches);
            out.remove(i);
            out.addAll(i, replacement.getBranches());
            return List.of(new IfElseIota(out, source.getElseBody()));
        });
    }

    // =======================================================================
    // 公共脚手架
    // =======================================================================

    /**
     * 按参数个数与类型谓词造一个算子,{@code body} 里只需要写「拿到参数列表后返回什么」。
     *
     * <p>{@link OperatorBasic} 已经把「从栈顶取 arity 个参数、把返回值压回栈、记算力」
     * 这套样板做好了,所以这里只包一层:把 {@code Iterable} 转成可下标访问的 {@link List},
     * 省得每个算子都写一遍迭代器。
     */
    private static Operator chain(int arity, IotaMultiPredicate accepts, Body body) {
        return new OperatorBasic(arity, accepts) {
            @Override
            public Iterable<Iota> apply(Iterable<? extends Iota> iotas, CastingEnvironment env) {
                List<Iota> args = new ArrayList<>(arity);
                for (Iota iota : iotas) {
                    args.add(iota);
                }
                return body.apply(args);
            }
        };
    }

    /** 「拿到参数列表后返回要压栈的 iota」这一小段逻辑。 */
    @FunctionalInterface
    private interface Body {
        Iterable<Iota> apply(List<Iota> args);
    }

    /**
     * 把参数转成条件链。
     *
     * <p>不做运行时类型检查:调用方的谓词已经保证了类型,而且谓词只看 IotaType、
     * 引擎又按类型元组缓存,所以这里拿到的一定是 {@link IfElseIota}。
     */
    private static IfElseIota asChain(Iota iota) {
        return (IfElseIota) iota;
    }
}
