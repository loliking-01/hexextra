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
 * <h2>⚠ 两条硬约束(1.21.1 上踩过)</h2>
 * <ol>
 *   <li><b>arity 必须与内置算子完全一致</b>:{@code ADD=2、INDEX=2、ABS=1、REMOVE=2、REPLACE=3}。
 *       不一致会在引擎构建时抛异常,而引擎是懒构建的 —— 后果是玩家第一次施法时
 *       <b>整个算术系统一起瘫痪</b>。</li>
 *   <li><b>类型谓词只能依据 IotaType</b> —— 引擎按「图案 + 类型元组」缓存解析结果。</li>
 * </ol>
 *
 * <h2>0.11.4 与 1.21.1 的差异</h2>
 * 这一层几乎是逐行照搬的,只有三处:
 * <ul>
 *   <li>{@code IotaPredicate.ofType(...)} 直接吃 <b>静态字段</b>({@code HexIotaTypes.LIST}),
 *       1.21.1 上是 {@code DeferredHolder},要 {@code .get()};</li>
 *   <li>{@code OperatorBasic.apply} 的参数是 {@code Iterable<Iota>}(1.21.1 是协变的);</li>
 *   <li>取值助手用 {@link ActionUtilsKt}(对 {@code List<Iota>} 的扩展),因为 0.11.4 的
 *       {@code OperatorUtils} 是对 {@code Iterator} 的扩展,不吃 List。</li>
 * </ul>
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
        // 0.11.4 的 HexPattern 没有 getSignature();sigsEqual 只比 angles,正是"图案形状相同"的判定
        return a.sigsEqual(b);
    }

    // =======================================================================
    // 类型谓词
    // =======================================================================

    private static IotaPredicate table() {
        return IotaPredicate.ofType(SwitchIota.TYPE);
    }


    private static IotaPredicate list() {
        return IotaPredicate.ofType(HexIotaTypes.LIST);
    }

    private static IotaPredicate number() {
        return IotaPredicate.ofType(HexIotaTypes.DOUBLE);
    }

    // =======================================================================
    // 加法之馏化:把原料拼成成品
    // =======================================================================

    /**
     * 「加法之馏化」对 switch 的两种用法。
     *
     * <ul>
     *   <li>{@code add(表, 表)} —— 合并两张表(类型须一致,且至多一边有 default)。
     *       因为一条分支本身就是"只有一条分支的表",这也覆盖了"追加一条"。</li>
     *   <li>{@code add(表, 列表)} —— 该列表成为 default。</li>
     * </ul>
     *
     * <p>未定类型的表(null 键组成的那些)可以被任何表吸收。
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
            return List.of(attachDefault(first, TreeList.from(OperatorUtils.getList(args, 1, 2))));
        });
    }

    private static SwitchIota appendCase(SwitchIota table, SwitchIota.Case extra) {
        SwitchMatchType caseType = extra.matchType();
        if (caseType == null) {
            throw new HexextraMishaps.CaseNotConstant(extra.getValue());
        }
        if (caseType != table.getMatchType()) {
            throw new HexextraMishaps.CaseTypeMismatch(table.getMatchType().translationKey(), extra.getValue());
        }

        List<SwitchIota.Case> out = new ArrayList<>(table.getCases());
        out.add(extra);
        return new SwitchIota(table.getMatchType(), out, table.getDefaultBody());
    }

    private static SwitchIota merge(SwitchIota a, SwitchIota b) {
        // 未定类型的表(null 键组成的那些)可以被任何表吸收;两边都确定了才比较
        SwitchMatchType aType = a.getMatchType();
        SwitchMatchType bType = b.getMatchType();
        if (aType != null && bType != null && aType != bType) {
            throw new HexextraMishaps.SwitchTypeMismatch(aType.translationKey(), bType.translationKey());
        }
        SwitchMatchType mergedType = aType != null ? aType : bType;
        if (a.getDefaultBody() != null && b.getDefaultBody() != null) {
            throw new HexextraMishaps.SwitchAlreadyHasDefault();
        }

        List<SwitchIota.Case> merged = new ArrayList<>(a.getCases());
        merged.addAll(b.getCases());

        TreeList<Iota> defaultBody = a.getDefaultBody() != null ? a.getDefaultBody() : b.getDefaultBody();
        return new SwitchIota(mergedType, merged, defaultBody);
    }

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
     * {@code index(表, i)} → 第 i 条 case,作为 {@link SwitchIota.Case} 取出。
     *
     * <p>越界返回 {@code NullIota},照搬原版列表版 {@code OperatorIndex} 的行为;
     * 下标是「取整、不校验整性」的。
     *
     * <p>取出来的是原料而不是单 case 表,这样 {@code replace(表, i, index(表, j))}
     * 就能直接完成"把第 j 条复制到第 i 位"。
     */
    private Operator makeIndex() {
        return chain(2, IotaMultiPredicate.pair(table(), number()), args -> {
            SwitchIota source = asTable(args.get(0));
            int i = (int) Math.round(OperatorUtils.getDouble(asList(args), 1, 2));

            List<SwitchIota.Case> cases = source.getCases();
            if (i < 0 || i >= cases.size()) {
                return List.of(new NullIota());
            }
            // 取出来的是一条分支 —— 它本身就是「只有一条分支的表」,可直接执行
            return List.of(new SwitchIota(source.getMatchType(), List.of(cases.get(i)), null));
        });
    }

    // =======================================================================
    // 长度之纯化:表里有多少条 case
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

    /** {@code remove_from(表, i)} → 删掉第 i 条;下标越界时原样返回(照搬原版列表行为)。 */
    private Operator makeRemove() {
        return chain(2, IotaMultiPredicate.pair(table(), number()), args -> {
            SwitchIota source = asTable(args.get(0));
            // 原版用的是"要求整数值"的取值,对应 getInt
            int i = OperatorUtils.getInt(asList(args), 1, 2);

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
     * {@code replace(表, i, case)} → 把第 i 条换成给的那一条。
     *
     * <p>下标越界会<b>报错</b>(照搬原版 {@code OperatorReplace} 的 {@code getPositiveIntUnder});
     * 类型必须与表一致。替换项是一条 case 而不是表 —— 所以它<b>不可能</b>自带 default。
     */
    private Operator makeReplace() {
        return chain(3, IotaMultiPredicate.triple(table(), number(), table()), args -> {
            SwitchIota source = asTable(args.get(0));
            List<SwitchIota.Case> cases = source.getCases();

            int i = OperatorUtils.getPositiveIntUnder(asList(args), 1, cases.size(), 3);

            // 替换项必须是一张「只有一条分支、且没有 default」的表
            SwitchIota replacementTable = asTable(args.get(2));
            if (replacementTable.getDefaultBody() != null || replacementTable.getCases().size() != 1) {
                throw new HexextraMishaps.SwitchTypeMismatch(
                        source.getMatchType() != null ? source.getMatchType().translationKey() : "hexextra.switch.type.unknown",
                        "hexextra.switch.type.unknown");
            }
            SwitchIota.Case replacement = replacementTable.getCases().get(0);
            if (replacement.matchType() != null && source.getMatchType() != null
                    && replacement.matchType() != source.getMatchType()) {
                throw new HexextraMishaps.CaseTypeMismatch(
                        source.getMatchType().translationKey(), replacement.getValue());
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

    @FunctionalInterface
    private interface Body {
        Iterable<Iota> apply(List<Iota> args);
    }

    /** 取出第 idx 个参数并当成代码块(列表)。 */
    private static TreeList<Iota> asTreeList(List<Iota> args, int idx, int argc) {
        return TreeList.from(OperatorUtils.getList(asList(args), idx, argc));
    }

    /**
     * 把协变的参数列表转成 Kotlin 扩展函数要求的 {@code List<Iota>}。
     *
     * <p>Kotlin 的 {@code List<Iota>} 声明处协变,编译到 Java 就是 {@code List<? extends Iota>};
     * 而 {@code OperatorUtils} 那些扩展函数在 Java 侧的签名收的是 {@code List<Iota>}。
     * 这里做一次无检查的转换 —— 运行时它本来就是 {@code List<Iota>}。
     */
    @SuppressWarnings("unchecked")
    private static List<Iota> asList(List<? extends Iota> args) {
        return (List<Iota>) args;
    }

    /** 把参数转成 switch 表(谓词已保证类型)。 */
    private static SwitchIota asTable(Iota iota) {
        return (SwitchIota) iota;
    }
}
