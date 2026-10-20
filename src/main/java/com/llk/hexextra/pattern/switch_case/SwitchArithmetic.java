package com.llk.hexextra.pattern.switch_case;

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
 * 把 switch 表与 case 接进咒法学的<b>算术引擎</b>,让原本作用于列表的图案也能作用在它们上面。
 *
 * <h2>两种东西,两套语义</h2>
 * <ul>
 *   <li>{@link SwitchIota}(表)是<b>成品</b>:可执行、有类型、可有 default;</li>
 *   <li>{@link SwitchIota.Case}(一条 case)是<b>原料</b>:只有「常量 + 代码块」,不可执行。</li>
 * </ul>
 * 「加法之馏化」是唯一同时认识两者的图案 —— 它负责把原料拼成成品。
 *
 * <h2>与 {@code IfElseArithmetic} 的关系</h2>
 * 两者负责同一批图案但接受不同类型;算术引擎会把同一图案下所有算术提供的算子汇总成候选,
 * 再看哪个的谓词接受当前的参数类型元组 —— 所以可以并存。
 *
 * <h2>两条硬约束(踩过)</h2>
 * <ol>
 *   <li><b>arity 必须与内置算子完全一致</b>:{@code ADD=2、INDEX=2、ABS=1、REMOVE=2、REPLACE=3}。
 *       不一致会在引擎构建时抛异常,而引擎是懒构建的 —— 后果是玩家第一次施法时
 *       <b>整个算术系统一起瘫痪</b>。启动自检会提前验它。</li>
 *   <li><b>类型谓词只能依据 IotaType</b> —— 引擎按「图案 + 类型元组」缓存解析结果。</li>
 * </ol>
 *
 * <h2>⚠ 类型谓词是"方法"不是"静态字段"</h2>
 * 原因同 {@code IfElseArithmetic}:本类在 {@code RegisterEvent} 期间被构造,
 * 那时 {@code DeferredHolder} 还没绑定,静态字段里调 {@code .get()} 会让服务端起不来。
 */
public final class SwitchArithmetic implements Arithmetic {

    /** 本算术负责的图案。 */
    private final List<HexPattern> ops = List.of(ADD, INDEX, ABS, REMOVE, REPLACE);

    @Override
    public String arithName() {
        return "hexextra_switch";
    }

    @Override
    public Iterable<HexPattern> opTypes() {
        return this.ops;
    }

    @Override
    public Operator getOperator(HexPattern pattern) {
        if (sameSignature(pattern, ADD)) return this.makeAdd();
        if (sameSignature(pattern, INDEX)) return this.makeIndex();
        if (sameSignature(pattern, ABS)) return this.makeLength();
        if (sameSignature(pattern, REMOVE)) return this.makeRemove();
        if (sameSignature(pattern, REPLACE)) return this.makeReplace();
        throw new IllegalArgumentException("hexextra 的 switch 算术不支持图案 " + pattern);
    }

    private static boolean sameSignature(HexPattern a, HexPattern b) {
        return a.getSignature().equals(b.getSignature());
    }

    // =======================================================================
    // 类型谓词 —— 每次现取,绝不在类初始化时解析 DeferredHolder
    // =======================================================================

    /** 「是 switch 表」的谓词。 */
    private static IotaPredicate table() {
        return IotaPredicate.ofType(HexextraIotaTypes.SWITCH.get());
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
    // 加法之馏化:把原料拼成成品
    // =======================================================================

    /**
     * 「加法之馏化」对 switch 家族的四种用法。
     *
     * <ul>
     *   <li>{@code add(case, case)} —— 把两条 case 组成一张表(类型须一致);</li>
     *   <li>{@code add(表, case)} —— 往表里追加一条;</li>
     *   <li>{@code add(表, 表)} —— 合并两张表(类型须一致,且至多一边有 default);</li>
     *   <li>{@code add(表, 列表)} —— 该列表成为 default(与 if_else 家族
     *       {@code add(链, 列表)} 接 else 完全对称)。</li>
     * </ul>
     */
    /**
     * 「加法之馏化」对 switch 的两种用法。
     *
     * <ul>
     *   <li>{@code add(表, 表)} —— 合并两张表(类型须一致,且至多一边有 default)。
     *       因为一条分支本身就是"只有一条 case 的表",所以这同时也覆盖了"追加一条"。</li>
     *   <li>{@code add(表, 列表)} —— 该列表成为 default(与 if_else 家族对称)。</li>
     * </ul>
     */
    private Operator makeAdd() {
        IotaMultiPredicate accepts = IotaMultiPredicate.either(
                IotaMultiPredicate.pair(table(), table()),
                IotaMultiPredicate.pair(table(), list()));

        return chain(2, accepts, args -> {
            SwitchIota first = asTable(args.get(0));
            if (args.get(1) instanceof SwitchIota second) {
                return List.of(merge(first, second));
            }
            return List.of(attachDefault(first, OperatorUtils.getList(args, 1, 2)));
        });
    }
    /** 合并两张表:类型一致、至多一边有 default,case 直接拼接。 */
    private static SwitchIota merge(SwitchIota a, SwitchIota b) {
        if (a.getDefaultBody() != null && b.getDefaultBody() != null) {
            throw new HexextraMishaps.SwitchAlreadyHasDefault();
        }

        // 未定类型的表(null 键组成的那些)可以被任何表吸收;两边都确定了才比较
        SwitchIota.MatchType aType = a.getMatchType();
        SwitchIota.MatchType bType = b.getMatchType();
        if (aType != null && bType != null && aType != bType) {
            throw new HexextraMishaps.SwitchTypeMismatch(aType.translationKey(), bType.translationKey());
        }
        SwitchIota.MatchType mergedType = aType != null ? aType : bType;

        List<SwitchIota.Case> merged = new ArrayList<>(a.getCases());
        merged.addAll(b.getCases());

        TreeList<Iota> defaultBody = a.getDefaultBody() != null ? a.getDefaultBody() : b.getDefaultBody();
        return new SwitchIota(mergedType, merged, defaultBody);
    }

    /** 给表接上 default。 */
    private static SwitchIota attachDefault(SwitchIota table, TreeList<Iota> defaultBody) {
        if (table.getDefaultBody() != null) {
            throw new HexextraMishaps.SwitchAlreadyHasDefault();
        }
        return new SwitchIota(table.getMatchType(), table.getCases(), defaultBody);
    }

    // =======================================================================
    // 索引:取出第 i 条 case(结果是原料,不是表)
    // =======================================================================

    /**
     * {@code index(表, i)} → 第 i 条 case,作为"只有一条分支的表"取出(可直接执行)。
     *
     * <p>越界返回 {@code NullIota} —— 照搬原版列表版 {@code OperatorIndex} 的行为。
     * 下标是「取整、不校验整性」的,同样照搬。
     *
     * <p>取出来的是原料而不是单 case 表,这样 {@code replace(表, i, index(表, j))}
     * 就能直接完成"把第 j 条复制到第 i 位"。
     */
    private Operator makeIndex() {
        return chain(2, IotaMultiPredicate.pair(table(), number()), args -> {
            SwitchIota source = asTable(args.get(0));
            int i = (int) Math.round(OperatorUtils.getDouble(args, 1, 2));

            List<SwitchIota.Case> cases = source.getCases();
            if (i < 0 || i >= cases.size()) {
                return List.of(new NullIota());
            }
            // 取出来的是一条分支 —— 它本身就是「只有一条 case 的表」,可以直接执行
            return List.of(new SwitchIota(source.getMatchType(), List.of(cases.get(i)), null));
        });
    }

    // =======================================================================
    // 绝对值:表里有多少条 case
    // =======================================================================

    /** {@code abs(表)} → case 条数(不含 default)。 */
    private Operator makeLength() {
        return chain(1, IotaMultiPredicate.all(table()), args -> {
            SwitchIota source = asTable(args.get(0));
            return List.of(new DoubleIota(source.getCases().size()));
        });
    }

    // =======================================================================
    // 切除器之馏化:删掉第 i 条 case
    // =======================================================================

    /** {@code remove_from(表, i)} → 删掉第 i 条 case;下标越界时原样返回(照搬原版列表行为)。 */
    private Operator makeRemove() {
        return chain(2, IotaMultiPredicate.pair(table(), number()), args -> {
            SwitchIota source = asTable(args.get(0));
            // 原版用的是 nextInt(要求整数值),这里对应 OperatorUtils.getInt
            int i = OperatorUtils.getInt(args, 1, 2);

            List<SwitchIota.Case> cases = source.getCases();
            if (i < 0 || i >= cases.size()) {
                return List.of(source);
            }

            List<SwitchIota.Case> out = new ArrayList<>(cases);
            out.remove(i);
            return List.of(new SwitchIota(source.getMatchType(), out, source.getDefaultBody()));
        });
    }

    // =======================================================================
    // 外科医生之提整:换掉第 i 条 case
    // =======================================================================

    /**
     * {@code replace(表, i, case)} → 把第 i 条 case 换成给的那一条。
     *
     * <p>下标<b>越界会报错</b>(照搬原版 {@code OperatorReplace} 的 {@code nextPositiveIntUnder});
     * 类型必须与表一致。
     *
     * <p>注意替换项是一条 case 而不是表 —— 所以它<b>不可能</b>自带 default,
     * 这里也就不需要"替换项不允许带 default"那条检查了。
     */
    private Operator makeReplace() {
        return chain(3, IotaMultiPredicate.triple(table(), number(), table()), args -> {
            SwitchIota source = asTable(args.get(0));
            List<SwitchIota.Case> cases = source.getCases();

            // 参数顺序:0=表, 1=下标, 2=替换项;argc 传 3 让报错定位到正确位置
            int i = OperatorUtils.getPositiveIntUnder(args, 1, cases.size(), 3);

            // 替换项必须是一张「只有一条分支、且没有 default」的表
            SwitchIota replacementTable = asTable(args.get(2));
            if (replacementTable.getDefaultBody() != null || replacementTable.getCases().size() != 1) {
                throw new HexextraMishaps.SwitchTypeMismatch(
                        source.getMatchType().translationKey(), source.getMatchType().translationKey());
            }
            SwitchIota.Case replacement = replacementTable.getCases().get(0);
            SwitchIota.MatchType replacementType = replacement.matchType();
            SwitchIota.MatchType sourceType = source.getMatchType();
            if (replacementType != null && sourceType != null && replacementType != sourceType) {
                throw new HexextraMishaps.CaseTypeMismatch(
                        sourceType.translationKey(), replacement.getValue());
            }

            List<SwitchIota.Case> out = new ArrayList<>(cases);
            out.set(i, replacement);
            return List.of(new SwitchIota(source.getMatchType(), out, source.getDefaultBody()));
        });
    }

    // =======================================================================
    // 公共脚手架
    // =======================================================================

    /** 按参数个数与类型谓词造算子;{@code body} 只写「拿到参数列表后返回什么」。 */
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

    /** 「拿到参数列表后返回要压栈的 iota」。 */
    @FunctionalInterface
    private interface Body {
        Iterable<Iota> apply(List<Iota> args);
    }

    /**
     * 把参数转成 switch 表。
     *
     * <p>不做运行时类型检查:调用方的谓词已经保证了类型,而且谓词只看 IotaType、
     * 引擎又按类型元组缓存。
     */
    private static SwitchIota asTable(Iota iota) {
        return (SwitchIota) iota;
    }
}
