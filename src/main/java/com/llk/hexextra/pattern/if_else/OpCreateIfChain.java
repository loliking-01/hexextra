package com.llk.hexextra.pattern.if_else;

import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.utils.TreeList;

import java.util.List;

/**
 * 创建 {@code if} 链的动作:从栈上取两个代码块,组成一个只含单个 {@code if} 分支的条件链。
 *
 * <h2>栈序</h2>
 * 先压<b>条件代码块</b>,再压<b>代码块</b>(后者在栈顶)。{@code ConstMediaAction} 取参数时
 * 是按 {@code args[0] = 较深的一个} 排列的,所以 {@code args[0]} 是条件、{@code args[1]} 是代码块。
 *
 * <h2>为什么用 ConstMediaAction 而不是裸 Action</h2>
 * 它替我们包办了「检查参数够不够 → 取栈顶 argc 个 → 把返回值压栈 → 记算力」这套样板,
 * 我们只需要实现「拿到参数、还回结果」这一步。本动作不消耗媒质(mediaCost 默认 0)。
 */
public final class OpCreateIfChain implements ConstMediaAction {

    /** 从栈上消费两个参数:条件代码块 + 代码块。 */
    private static final int ARGC = 2;

    @Override
    public int getArgc() {
        return ARGC;
    }

    @Override
    public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
        // getList 会在参数不是列表时抛出 MishapInvalidIota,并把错误定位到对应参数上
        TreeList<Iota> condition = OperatorUtils.getList(args, 0, ARGC);
        TreeList<Iota> body = OperatorUtils.getList(args, 1, ARGC);

        IfElseFeature.trace("建链:条件块 {} 个元素 {},代码块 {} 个元素 {}",
                condition.size(), describe(condition), body.size(), describe(body));

        // 单分支、无 else —— 后续用「加法之馏化」把更多分支与 else 接上去
        return List.of(new IfElseIota(
                List.of(new IfElseIota.Branch(condition, body)),
                null));
    }

    /** 诊断用:把代码块的内容列成"元素类型"列表,一眼能看出里面到底装了什么。 */
    private static String describe(TreeList<Iota> block) {
        StringBuilder sb = new StringBuilder("[");
        for (Iota iota : block) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(iota.getClass().getSimpleName());
        }
        return sb.append(']').toString();
    }
}
