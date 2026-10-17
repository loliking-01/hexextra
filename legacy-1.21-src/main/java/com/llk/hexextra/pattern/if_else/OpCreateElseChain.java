package com.llk.hexextra.pattern.if_else;

import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.utils.TreeList;

import java.util.List;

/**
 * 创建「仅 else 链」的动作:从栈上取一个代码块,组成一条<b>没有任何条件分支</b>、
 * 只有最终 {@code else} 的条件链。
 *
 * <h2>它有什么用</h2>
 * 它就是「链的分支」在栈上的统一表示单位:
 * <ul>
 *   <li>配合「加法之馏化」可以把一条链接到另一条链后面;</li>
 *   <li>「外科医生之提整」替换分支时,要填进去的也正是这种单分支/仅 else 链;</li>
 *   <li>「群体之拆解」把一条链摊开时,压出来的每一项都是这种形态。</li>
 * </ul>
 *
 * <h2>关于"没有分支"</h2>
 * 本类产生的链 {@code branches} 为空。这不是非法状态 —— 执行时没有条件可判,直接跑 else。
 * 允许它存在,也让「切除器之馏化」删掉最后一个分支后不会产生非法对象。
 */
public final class OpCreateElseChain implements ConstMediaAction {

    /** 从栈上消费一个参数:代码块。 */
    private static final int ARGC = 1;

    @Override
    public int getArgc() {
        return ARGC;
    }

    @Override
    public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
        TreeList<Iota> body = OperatorUtils.getList(args, 0, ARGC);

        IfElseFeature.trace("建仅 else 链:代码块 {} 个元素", body.size());

        return List.of(new IfElseIota(List.of(), body));
    }
}
