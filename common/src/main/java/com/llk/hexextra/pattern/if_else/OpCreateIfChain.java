package com.llk.hexextra.pattern.if_else;

import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.utils.TreeList;

import java.util.List;

/**
 * 「伊芙艾儿丝之策略」—— 造一条条件链:2 参(条件代码块 + 代码块)。
 *
 * <h2>栈序</h2>
 * 先压条件、再压代码块(后者在栈顶)。这与咒法学「先压的在深处」的通例一致。
 *
 * <h2>产出的是"链",不是"执行结果"</h2>
 * 本动作只把链放到栈上 —— 要真的求值,得在链上画「赫尔墨斯之策略」。
 * 这样链可以被加法之馏化接长、被算术拆装、当普通值传递。
 *
 * <h2>产出的是单分支链,不是"分支"对象</h2>
 * 一条单分支链本身既是"只有一条分支的 if",也是一条合法分支 ——
 * 所以同一件东西可以直接执行,也可以用来拼更长的链。这是 1.21.1 版就定下的设计。
 */
public final class OpCreateIfChain implements ConstMediaAction {

    private static final int ARGC = 2;

    @Override
    public int getArgc() {
        return ARGC;
    }

    @Override
    public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
        @SuppressWarnings("unchecked")
        List<Iota> list = (List<Iota>) args;

        // args[0] 是条件(较深),args[1] 是代码块(栈顶)
        TreeList<Iota> condition = TreeList.from(OperatorUtils.getList(list, 0, ARGC));
        TreeList<Iota> body = TreeList.from(OperatorUtils.getList(list, 1, ARGC));

        return List.of(new IfElseIota(List.of(new IfElseIota.Branch(condition, body)), null));
    }
}
