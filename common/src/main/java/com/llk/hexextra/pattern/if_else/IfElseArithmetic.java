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
import com.llk.hexextra.core.HexextraMishaps;

import java.util.ArrayList;
import java.util.List;

/**
 * 把条件链接进咒法学的<b>算术引擎</b>,让原本作用于列表的图案也能作用在链上。
 *
 * <h2>五个算子</h2>
 * <ul>
 *   <li>{@code add(链, 链)} → 拼接(左链不能已带 else);{@code add(链, 列表)} → 接成 else;</li>
 *   <li>{@code index(链, i)} → 第 i 条分支(作为单分支链);越界给 null;</li>
 *   <li>{@code abs(链)} → 分支条数(<b>不</b>含 else);</li>
 *   <li>{@code remove_from(链, i)} → 删掉第 i 条分支;越界原样返回;</li>
 *   <li>{@code replace(链, i, 链)} → 换掉第 i 条分支(替换项不能带 else;越界报错)。</li>
 * </ul>
 *
 * <h2>⚠ 与 1.21.1 版一样的硬约束</h2>
 * arity 必须与内置算子一致({@code ADD=2、INDEX=2、ABS=1、REMOVE=2、REPLACE=3}),
 * 不一致会在引擎懒构建时抛异常,后果是玩家第一次施法时<b>整个算术系统一起瘫痪</b>。
 *
 * <h2>0.11.4 的差异</h2>
 * 谓词直接吃静态字段({@code HexIotaTypes.LIST} / {@code .DOUBLE}),
 * 取值助手用 {@code OperatorUtils}(对 {@code List<Iota>} 的扩展)。
 */
public final class IfElseArithmetic implements Arithmetic {

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
        if (pattern.sigsEqual(ADD)) return this.makeAdd();
        if (pattern.sigsEqual(INDEX)) return this.makeIndex();
        if (pattern.sigsEqual(ABS)) return this.makeLength();
        if (pattern.sigsEqual(REMOVE)) return this.makeRemove();
        if (pattern.sigsEqual(REPLACE)) return this.makeReplace();
        throw new IllegalArgumentException("hexextra 的 if_else 算术不支持图案 " + pattern);
    }

    // ---- 类型谓词 ----

    private static IotaPredicate chain() {
        return IotaPredicate.ofType(IfElseIota.TYPE);
    }

    private static IotaPredicate list() {
        return IotaPredicate.ofType(HexIotaTypes.LIST);
    }

    private static IotaPredicate number() {
        return IotaPredicate.ofType(HexIotaTypes.DOUBLE);
    }

    // ---- add:拼接 / 接 else ----

    private Operator makeAdd() {
        return chain(2, IotaMultiPredicate.either(
                IotaMultiPredicate.pair(chain(), chain()),
                IotaMultiPredicate.pair(chain(), list())), args -> {
            IfElseIota first = (IfElseIota) args.get(0);

            if (args.get(1) instanceof IfElseIota second) {
                return List.of(IfElseChainOps.concat(first, second));
            }
            TreeList<Iota> elseBody = TreeList.from(OperatorUtils.getList(asList(args), 1, 2));
            return List.of(IfElseChainOps.attachElse(first, elseBody));
        });
    }

    // ---- index:取出第 i 条分支 ----

    private Operator makeIndex() {
        return chain(2, IotaMultiPredicate.pair(chain(), number()), args -> {
            IfElseIota source = (IfElseIota) args.get(0);
            int i = (int) Math.round(OperatorUtils.getDouble(asList(args), 1, 2));

            // 下标只数分支,不算 else
            if (i < 0 || i >= source.getBranches().size()) {
                return List.of(new NullIota());
            }
            return List.of(new IfElseIota(List.of(source.getBranches().get(i)), null));
        });
    }

    // ---- abs:分支条数 ----

    private Operator makeLength() {
        return chain(1, IotaMultiPredicate.all(chain()), args -> {
            IfElseIota source = (IfElseIota) args.get(0);
            return List.of(new DoubleIota(source.getBranches().size()));
        });
    }

    // ---- remove_from:删掉第 i 条分支 ----

    private Operator makeRemove() {
        return chain(2, IotaMultiPredicate.pair(chain(), number()), args -> {
            IfElseIota source = (IfElseIota) args.get(0);
            int i = OperatorUtils.getInt(asList(args), 1, 2);

            List<IfElseIota.Branch> branches = source.getBranches();
            if (i < 0 || i >= branches.size()) {
                return List.of(source);
            }
            List<IfElseIota.Branch> out = new ArrayList<>(branches);
            out.remove(i);
            return List.of(new IfElseIota(out, source.getElseBody()));
        });
    }

    // ---- replace:换掉第 i 条分支 ----

    private Operator makeReplace() {
        return chain(3, IotaMultiPredicate.triple(chain(), number(), chain()), args -> {
            IfElseIota source = (IfElseIota) args.get(0);
            List<IfElseIota.Branch> branches = source.getBranches();

            int i = OperatorUtils.getPositiveIntUnder(asList(args), 1, branches.size(), 3);

            IfElseIota replacement = (IfElseIota) args.get(2);
            // 替换项必须恰好是一条"无 else 的单分支链" —— 分支本身不含 else,
            // 悄悄丢掉替换项里的 else 会让玩家以为接上了却没接上
            if (replacement.getElseBody() != null) {
                throw new HexextraMishaps.ReplacementHasElse();
            }
            if (replacement.getBranches().size() != 1) {
                throw new HexextraMishaps.ReplacementHasElse();
            }

            List<IfElseIota.Branch> out = new ArrayList<>(branches);
            out.set(i, replacement.getBranches().get(0));
            return List.of(new IfElseIota(out, source.getElseBody()));
        });
    }

    // ---- 脚手架 ----

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

    @SuppressWarnings("unchecked")
    private static List<Iota> asList(List<Iota> args) {
        return args;
    }
}
