package com.llk.hexextra.pattern.if_else;

import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.utils.TreeList;

import java.util.List;

/**
 * 「艾儿丝之策略」—— 造一条<b>只有 else</b> 的条件链:1 参(代码块)。
 *
 * <h2>它有什么用</h2>
 * 一条没有分支、只有 else 的链,执行时<b>无条件</b>跑那段代码块。
 * 配合加法之馏化就能给已有链接上 else:
 * {@code add(链, 只有else的链)} 等价于 {@code add(链, 列表)}。
 *
 * <p>之所以单独给一个图案而不是只靠 {@code add(链, 列表)}:这样 else 也能像
 * 其他部分一样被<b>单独构造、传递、拆装</b> —— 与 if 分支的表示保持一致。
 */
public final class OpCreateElseChain implements ConstMediaAction {

    private static final int ARGC = 1;

    @Override
    public int getArgc() {
        return ARGC;
    }

    @Override
    public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
        @SuppressWarnings("unchecked")
        List<Iota> list = (List<Iota>) args;
        TreeList<Iota> elseBody = TreeList.from(OperatorUtils.getList(list, 0, ARGC));
        return List.of(new IfElseIota(List.of(), elseBody));
    }
}
